package net.rcode.core.redis;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Convert redis requests to buffers that can be written to the server.  Unlike the parser,
 * which is streaming, the serializer always works with full requests.
 * 
 * @author stella
 *
 */
public class RedisSerializer {
	public static final RedisSerializer INSTANCE=new RedisSerializer();
	
	private static final byte[] CRLF=new byte[] { (byte)13, (byte)10 };
	
	private Charset charset;
	
	public RedisSerializer(Charset charset) {
		this.charset=charset;
	}
	public RedisSerializer() {
		this(Charset.forName("UTF-8"));
	}
	
	/**
	 * Perform serialization
	 * @param arguments either byte[], null or toString() coercible arguments
	 * @return ByteBuffer
	 */
	public ByteBuffer serialize(String command, Object... arguments) {
		ByteBuffer[] buffers=new ByteBuffer[arguments.length+1];
		buffers[0]=charset.encode(command);
		for (int i=0; i<arguments.length; i++) {
			Object argument=arguments[i];
			ByteBuffer buffer;
			if (argument==null) {
				buffer=ByteBuffer.allocate(0);
			} else if (argument instanceof byte[]) {
				buffer=ByteBuffer.wrap((byte[])argument);
			} else if (argument instanceof CharSequence) {
				// Work straight off the CharSequence - saves an extra copy for StringBuilder
				// and the like
				buffer=charset.encode(CharBuffer.wrap((CharSequence)argument));
			} else {
				buffer=charset.encode(argument.toString());
			}
			
			buffers[i+1]=buffer;
		}
		
		return serializeBuffers(buffers);
	}
	
	private ByteBuffer serializeBuffers(ByteBuffer[] buffers) {
		// Come up with a conservative size estimate.
		// 16 bytes per entry plus size of each entry.
		// Includes prefix char, \r\n, and ascii size
		// Plus the message header (16)
		int size=16*buffers.length + 16;
		for (int i=0; i<buffers.length; i++) {
			size+=buffers[i].limit();
		}
		
		ByteBuffer buffer=allocate(size);
		
		// Number of arguments
		buffer.put((byte)'*');
		writeAsciiNumber(buffer, buffers.length);
		buffer.put(CRLF);
		
		// For each argument
		for (int i=0; i<buffers.length; i++) {
			ByteBuffer argument=buffers[i];
			buffer.put((byte)'$');
			writeAsciiNumber(buffer, argument.remaining());
			buffer.put(CRLF);
			buffer.put(argument);
			buffer.put(CRLF);
		}
		
		buffer.flip();
		return buffer;
	}
	
	private void writeAsciiNumber(ByteBuffer buffer, int n) {
		byte zero=48;
		byte[] digits=new byte[12];
		int count=0;
		boolean neg;
		
		if (n<0) {
			neg=true;
			n=Math.abs(n);
		} else {
			neg=false;
		}
		
		for (;;) {
			int rem=n%10;
			digits[count++]=(byte)(zero+rem);
			n=n/10;
			if (n==0) break;
		}
		
		// Digits are reversed.  Write them.
		if (neg) buffer.put((byte)'-');
		for (int i=count-1; i>=0; i--) {
			buffer.put(digits[i]);
		}
	}
	
	protected ByteBuffer allocate(int size) {
		return ByteBuffer.allocate(size);
	}
}
