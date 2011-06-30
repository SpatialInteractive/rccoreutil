package net.rcode.core.io;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A memory output stream that stores its data in a sequence of blocks.  This
 * stream assumes that it will be used for memory copies and other tasks and
 * therefore allows more direct access to its contents than a ByteArrayOutputStream
 * does.
 * 
 * @author stella
 *
 */
public class BlockOutputStream extends OutputStream {
	public static final int DEFAULT_BLOCK_SIZE=8192;
	
	private int blockSize;
	private List<byte[]> blocks=new ArrayList<byte[]>();
	private long streamLength;
	private byte[] currentBlock;
	private int currentPos;
	
	public BlockOutputStream(int blockSize) {
		this.blockSize=blockSize;
	}
	
	public BlockOutputStream() {
		this(DEFAULT_BLOCK_SIZE);
	}

	public long length() {
		if (blocks.isEmpty()) return 0;
		return (blocks.size()-1) * blockSize + currentPos;
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		while (len>0) {
			if (currentBlock==null) {
				currentBlock=new byte[blockSize];
				blocks.add(currentBlock);
				currentPos=0;
			}
			
			int remain=blockSize - currentPos;
			if (remain>len) {
				remain=len;
			}
			
			System.arraycopy(b, off, currentBlock, currentPos, remain);
			currentPos+=remain;
			off+=remain;
			len-=remain;
			streamLength+=remain;
			
			if (currentPos>=blockSize) {
				currentBlock=null;
				currentPos=0;
			}
		}
	}
	

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}
	
	@Override
	public void write(int ib) throws IOException {
		byte b=(byte)(ib < 128 ? ib : 127 - ib);
		write(new byte[] { b }, 0, 1);
	}

	public InputStream openInput() {
		return new BlockInputStream(blocks.toArray(new byte[blocks.size()][]), 
				blockSize, 
				currentBlock==null && !blocks.isEmpty()? blockSize : currentPos);
	}

	public byte[] getBytes() {
		if (streamLength>Integer.MAX_VALUE) {
			throw new RuntimeException("Cannot create larger than 32bit array");
		}
		
		byte[] ret=new byte[(int)streamLength];
		// Copy full blocks
		for (int i=0; i<(blocks.size()-1); i++) {
			byte[] block=blocks.get(i);
			System.arraycopy(block, 0, ret, i*blockSize, blockSize);
		}
		
		// Write the last block
		if (!blocks.isEmpty() && currentPos>0) {
			byte[] lastBlock=blocks.get(blocks.size()-1);
			System.arraycopy(lastBlock, 0, ret, (blocks.size()-1)*blockSize, currentPos);
		}
		
		return ret;
	}

	public void writeTo(OutputStream out) throws IOException {
		// Write full blocks
		for (int i=0; i<(blocks.size()-1); i++) {
			byte[] block=blocks.get(i);
			out.write(block);
		}
		
		// Write the last block
		if (!blocks.isEmpty() && currentPos>0) {
			byte[] lastBlock=blocks.get(blocks.size()-1);
			out.write(lastBlock, 0, currentPos);
		}
	}

	/**
	 * Transfers up to length chars from the given source Reader
	 * @param source
	 * @param len maximum chars to read or -1 to read to close
	 * @return number of chars transferred
	 * @throws IOException 
	 */
	public int transferFrom(InputStream source, int len) throws IOException {
		int total=0;
		while (len<0 || total<len) {
			if (currentBlock==null) {
				currentBlock=new byte[blockSize];
				blocks.add(currentBlock);
				currentPos=0;
			}
			
			int remain=blockSize - currentPos;
			if (remain>len) {
				remain=len;
			}
			
			int consumed=source.read(currentBlock, currentPos, remain);
			if (consumed<0) break;
			
			currentPos+=consumed;
			total+=consumed;
			len-=consumed;
			
			if (currentPos>=blockSize) {
				currentBlock=null;
				currentPos=0;
			}
		}
		
		return total;
	}

	
	/**
	 * Transfers up to length chars from the given source Reader
	 * @param buffer
	 * @throws IOException 
	 */
	public void transferFrom(ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			if (currentBlock==null) {
				currentBlock=new byte[blockSize];
				blocks.add(currentBlock);
				currentPos=0;
			}
			
			int remain=Math.min(blockSize - currentPos, buffer.remaining());
			buffer.get(currentBlock, currentPos, remain);
			
			currentPos+=remain;
			
			if (currentPos>=blockSize) {
				currentBlock=null;
				currentPos=0;
			}
		}
	}
	

}
