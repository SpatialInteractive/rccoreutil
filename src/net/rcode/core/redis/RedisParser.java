package net.rcode.core.redis;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Put buffers in on one end, get high level redis response events
 * on the other.
 * 
 * @author stella
 *
 */
public class RedisParser {
	protected static final Logger logger=LoggerFactory.getLogger(RedisParser.class);
	
	public static interface Events {
		public void handleStatus(ByteBuffer contents);
		public void handleError(ByteBuffer contents);
		public void handleInteger(int value);
		public void handleBulk(ByteBuffer contents);
		
		// Multi-block
		public void handleStartMultiBulk(int count);
		public void handleEndMultiBulk();
	}
	
	private static final int DEFAULT_ACCUM_SIZE=4096;
	
	private static final int
		/**
		 * We are waiting to get a complete "control" line.  This will be a full line
		 * starting with one of the control characters '+' (status), '-' (error),
		 * ':' (integer), '$' (bulk start), '*' (multi-block start).
		 */
		STATE_CONTROL=0,
		/**
		 * Accumulating a block of blockSize.  This may be a standalone block or
		 * a block within a multi-block depending on the value of multiBlockCount.
		 */
		STATE_BULK=1;
		
		
	protected int state;
	protected int expectedBulkSize;
	protected int expectedMultiBulkRemaining;
	protected ByteBuffer accumBuffer;
	protected Events events;
	
	public RedisParser(Events events) {
		this.events=events;
		
		this.state=STATE_CONTROL;
		this.expectedBulkSize=0;
		this.expectedMultiBulkRemaining=0;
		this.accumBuffer=null;
	}
	
	/**
	 * Round size up to the next highest KB boundary.  This could still be inefficient for large 
	 * allocations, but redis isn't really optimized for those anyway.  Should probably be doing
	 * some kind of power of 2 expansion.
	 * @param requestedSize
	 * @return rounded size
	 */
	private int roundSize(int requestedSize) {
		requestedSize|=0x3ff;
		return requestedSize+1;
	}
	
	/**
	 * Make sure that accumBuffer can accomodate at least neededRemaining more bytes
	 * @param neededRemaining
	 */
	protected final void ensureAccumBuffer(int neededRemaining) {
		if (accumBuffer==null) {
			accumBuffer=ByteBuffer.allocate(Math.max(neededRemaining, DEFAULT_ACCUM_SIZE));
			return;
		}
		
		int sizeDelta=neededRemaining - accumBuffer.remaining();
		if (sizeDelta>0) {
			// Resize
			int newSize=roundSize(accumBuffer.capacity() + sizeDelta);
			if (logger.isDebugEnabled()) {
				logger.debug("Resize accumBuffer to " + newSize);
			}
			ByteBuffer newBuffer=ByteBuffer.allocate(newSize);
			accumBuffer.flip();
			newBuffer.put(accumBuffer);
			accumBuffer=newBuffer;
		}
	}
	
	public void write(ByteBuffer packet) {
		while (packet.hasRemaining()) {
			if (state==STATE_BULK) {
				writePacketBlock(packet);
			} else if (state==STATE_CONTROL) {
				writePacketControl(packet);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	private void writePacketBlock(ByteBuffer packet) {
		int r=Math.min(packet.remaining(), expectedBulkSize-accumBuffer.position());
		ensureAccumBuffer(r);
		
		// Write up to r bytes from packet
		int origLimit=packet.limit();
		packet.limit(packet.position()+r);
		accumBuffer.put(packet);
		packet.limit(origLimit);
		
		// Did we get the whole block?
		if (accumBuffer.position()==expectedBulkSize) {
			// Yes we did.  Get out of this state.
			// Handle specially if we are part of a multi-block
			accumBuffer.flip();
			
			// The expected size contains the CRLF which we don't pass through
			accumBuffer.limit(accumBuffer.limit()-2);
			
			events.handleBulk(accumBuffer);
			if (expectedMultiBulkRemaining>0 && --expectedMultiBulkRemaining == 0) {
				// Last one
				events.handleEndMultiBulk();
			}
			
			// Reset and get out of this state
			state=STATE_CONTROL;
			accumBuffer.clear();
		}
	}

	private void writePacketControl(ByteBuffer packet) {
		// Scan for an end of packet marker (LF) and transfer that much
		int packetLimit=packet.limit();
		int controlLimit;
		for (controlLimit=packet.position(); controlLimit<packetLimit; controlLimit++) {
			if (packet.get(controlLimit)==0x0a) {
				controlLimit+=1;
				break;
			}
		}
		
		// Transfer the bytes
		packet.limit(controlLimit);
		ensureAccumBuffer(packet.remaining());
		accumBuffer.put(packet);
		packet.limit(packetLimit);
		
		// If the last two bytes is a CRLF, then we have a complete control line
		int pos=accumBuffer.position();
		if (pos>=2 && accumBuffer.get(pos-2)==0x0d && accumBuffer.get(pos-1)==0x0a) {
			// Terminating condition
			accumBuffer.flip();
			accumBuffer.limit(accumBuffer.limit()-2);
			processControlLine(accumBuffer);
			accumBuffer.clear();
		}
	}

	/**
	 * Process a control line without the trailing CRLF
	 * @param controlPacket
	 */
	private void processControlLine(ByteBuffer controlPacket) {
		byte controlChar=controlPacket.get();
		boolean startedMulti=false;
		
		// Branch based on control type
		if (controlChar=='$') {
			// Bulk
			int bulkSize=decodeInteger(controlPacket);
			if (bulkSize==-1) {
				// Nil bulk.  Process directly.
				events.handleBulk(null);
			} else if (bulkSize<0) {
				throw new IllegalStateException("Illegal bulk size");
			} else {
				expectedBulkSize=bulkSize+2;
				state=STATE_BULK;
			}
		} else if (controlChar=='*') {
			// Multi-bulk
			int multiCount=decodeInteger(controlPacket);
			if (multiCount==-1 || multiCount==0) {
				// Nil or empty multi-block.  Fire events but don't change state.
				events.handleStartMultiBulk(multiCount);
				events.handleEndMultiBulk();
			} else if (multiCount<0) {
				throw new IllegalStateException("Illegal multi-bulk count");
			} else {
				expectedMultiBulkRemaining=multiCount;
				events.handleStartMultiBulk(multiCount);
				startedMulti=true;
			}
		} else if (controlChar=='+') {
			events.handleStatus(controlPacket);
		} else if (controlChar=='-') {
			events.handleError(controlPacket);
		} else if (controlChar==':') {
			int value=decodeInteger(controlPacket);
			events.handleInteger(value);
		}
		
		// Process terminating condition for multi bulk if performed a primitive
		if (!startedMulti && state==STATE_CONTROL && expectedMultiBulkRemaining>0) {
			if (--expectedMultiBulkRemaining == 0) {
				events.handleEndMultiBulk();
			}
		}
	}

	/**
	 * Decode all remaining bytes as an ascii number.  It is an error to have non-digits (except for a leading minus).
	 * @param packet
	 * @return number
	 */
	private int decodeInteger(ByteBuffer packet) {
		int value=0;
		boolean negated=false;
		if (packet.remaining()==0) {
			throw new IllegalStateException("Expected integer but got empty buffer");
		}
		if (packet.get(packet.position())=='-') {
			negated=true;
			packet.position(packet.position()+1);
			if (packet.remaining()==0) {
				throw new IllegalStateException("Expected integer but got minus sign only");
			}
		}
		
		while (packet.hasRemaining()) {
			byte b=packet.get();
			if (b<'0' || b>'9') {
				throw new IllegalStateException("Expected integer but encountered non-numeric character");
			}
			
			value=value*10 + (b-'0');
		}
		
		if (negated) {
			value=-value;
		}
		return value;
	}
}
