package net.rcode.core.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;

/**
 * A writer for buffering an unbounded amount of character data.  The buffer
 * can then be accessed by a BlockReader to process it.
 * 
 * @author stella
 *
 */
public class BlockWriter extends Writer {
	public static final int DEFAULT_BLOCK_SIZE=4096;
	
	private int blockSize;
	private ArrayList<char[]> blocks=new ArrayList<char[]>(10);
	private long streamLength;
	private char[] currentBlock;
	private int currentPos;
	
	public BlockWriter(int blockSize) {
		this.blockSize=blockSize;
	}
	
	public BlockWriter() {
		this(DEFAULT_BLOCK_SIZE);
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		while (len>0) {
			if (currentBlock==null) {
				currentBlock=new char[blockSize];
				blocks.add(currentBlock);
				currentPos=0;
			}
			
			int remain=blockSize - currentPos;
			if (remain>len) {
				remain=len;
			}
			
			System.arraycopy(cbuf, off, currentBlock, currentPos, remain);
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
	
	/**
	 * Transfers up to length chars from the given source Reader
	 * @param source
	 * @param len maximum chars to read or -1 to read to close
	 * @return number of chars transferred
	 * @throws IOException 
	 */
	public int transferFrom(Reader source, int len) throws IOException {
		int total=0;
		while (len<0 || total<len) {
			if (currentBlock==null) {
				currentBlock=new char[blockSize];
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
	 * Dump the contents of this to another writer
	 * @param out
	 * @throws IOException
	 */
	public void writeTo(Writer out) throws IOException {
		// Write full blocks
		for (int i=0; i<(blocks.size()-1); i++) {
			char[] block=blocks.get(i);
			out.write(block);
		}
		
		// Write the last block
		if (!blocks.isEmpty() && currentPos>0) {
			char[] lastBlock=blocks.get(blocks.size()-1);
			out.write(lastBlock, 0, currentPos);
		}
	}
	
	public BlockReader openInput() {
		return new BlockReader(blocks.toArray(new char[blocks.size()][]), 
				blockSize, 
				currentBlock==null && !blocks.isEmpty()? blockSize : currentPos);
	}
}
