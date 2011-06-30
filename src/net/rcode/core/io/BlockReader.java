package net.rcode.core.io;

import java.io.IOException;
import java.io.Reader;

/**
 * Wrap a Reader around an array of fixed size blocks.  This is typically created
 * via a call to BlockWriter.openInput().
 * 
 * @author stella
 *
 */
public class BlockReader extends Reader {
	private char[][] blocks;
	private int lastLength;
	private int blockSize;
	
	private long position;
	
	public BlockReader(char[][] blocks, int blockSize, int lastLength) {
		this.blocks=blocks;
		this.blockSize=blockSize;
		this.lastLength=lastLength;
	}
	
	@Override
	public boolean ready() throws IOException {
		return true;
	}
	
	@Override
	public void close() throws IOException {
	}

	@Override
	public long skip(long n) throws IOException {
		long origPosition=position, maxLength=(blocks.length-1)*blockSize + lastLength;
		position+=n;
		if (position>maxLength) position=maxLength;
		return position-origPosition;
	}
	
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int blockIndex=(int)(position / blockSize);
		int blockPos=(int)(position % blockSize);
		boolean lastBlock=(blockIndex==(blocks.length-1));
		if (blockIndex>=blocks.length || (lastBlock && blockPos>=lastLength)) return -1;
		
		char[] block=blocks[(int)blockIndex];
		int remain;
		if (lastBlock) {
			// Last block
			remain=lastLength-blockPos;
		} else {
			// Before last block
			remain=blockSize-blockPos;
		}
		
		if (remain>len) remain=len;
		System.arraycopy(block, blockPos, cbuf, off, remain);
		
		position+=remain;
		return remain;
	}

}
