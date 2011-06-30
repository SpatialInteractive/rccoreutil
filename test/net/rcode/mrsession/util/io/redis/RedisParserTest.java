package net.rcode.mrsession.util.io.redis;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import net.rcode.core.redis.DebugEvents;
import net.rcode.core.redis.RedisParser;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RedisParserTest {
	private StringBuilder output;
	private RedisParser parser;
	
	@BeforeClass
	public static void initClass() {
		BasicConfigurator.configure();
	}
	
	@Before
	public void setup() {
		this.output=new StringBuilder();
		this.parser=new RedisParser(new DebugEvents() {
			@Override
			protected void print(String msg) {
				output.append(msg).append("\n");
			}
		});
	}
	
	private String parse(String stream) throws IOException {
		ByteBuffer streamBuffer=ByteBuffer.wrap(stream.getBytes("UTF-8"));
		
		String previous=null;
		
		int blockSize=streamBuffer.remaining();
		int origBlockSize=blockSize;
		while (blockSize>0) {
			streamBuffer.rewind();
			String results=parseWithBlockSize(streamBuffer, blockSize);
			if (previous!=null) {
				assertEquals("Current differs from previous at block size=" + blockSize + " (orig size=" + origBlockSize + ")", previous, results);
			}
			previous=results;
			blockSize-=1;
		}
		
		return previous;
	}
	
	private String dumpBuffer(ByteBuffer buffer) {
		return StringEscapeUtils.escapeJava(Charset.forName("UTF-8").decode(buffer.slice()).toString());
	}
	
	private String parseWithBlockSize(ByteBuffer streamBuffer, int blockSize) {
		System.out.println("Parse stream (block size=" + blockSize + "): '" + dumpBuffer(streamBuffer) + "'");
		output.setLength(0);
		
		while (streamBuffer.hasRemaining()) {
			int r=Math.min(blockSize, streamBuffer.remaining());
			ByteBuffer block=streamBuffer.slice();
			block.limit(r);
			streamBuffer.position(streamBuffer.position()+r);
			
			System.out.println("  - Write block: '" + dumpBuffer(block) + "'");
			parser.write(block);
		}
		
		String ret=output.toString();
		System.out.println("  = '" + StringEscapeUtils.escapeJava(ret) + "'");
		return ret;
	}

	@Test
	public void testPrimitives() throws IOException {
		assertEquals("handleStatus(OK)\n", parse("+OK\r\n"));
		assertEquals("handleStatus(OK)\nhandleError(Error)\nhandleInteger(12345)\n", parse("+OK\r\n-Error\r\n:12345\r\n"));
	}
	
	@Test
	public void testBulk() throws IOException {
		assertEquals("handleBulk(somevalue)\n", parse("$9\r\nsomevalue\r\n"));
		assertEquals("handleBulk(somevalue)\nhandleBulk(<null>)\n", parse("$9\r\nsomevalue\r\n$-1\r\n"));
	}
	
	@Test
	public void testMultiBulk() throws IOException {
		assertEquals("handleStartMultiBulk(-1)\nhandleEndMultiBulk()\n", parse("*-1\r\n"));
		assertEquals("handleStartMultiBulk(3)\nhandleBulk(first)\nhandleBulk(<null>)\nhandleBulk(done)\nhandleEndMultiBulk()\n",
				parse("*3\r\n$5\r\nfirst\r\n$-1\r\n$4\r\ndone\r\n"));
		assertEquals("handleStartMultiBulk(4)\nhandleBulk(first)\nhandleBulk(<null>)\nhandleBulk(done)\nhandleBulk(<null>)\nhandleEndMultiBulk()\n",
				parse("*4\r\n$5\r\nfirst\r\n$-1\r\n$4\r\ndone\r\n$-1\r\n"));
	}
}
