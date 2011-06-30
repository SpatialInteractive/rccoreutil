package net.rcode.core.redis;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import net.rcode.core.redis.RedisParser.Events;

import org.apache.commons.lang.StringEscapeUtils;

public class DebugEvents implements Events {
	static final Charset UTF8=Charset.forName("UTF-8");

	protected void print(String msg) {
		System.err.println("RedisParser: " + msg);
	}
	
	protected String decodeBuffer(ByteBuffer buffer) {
		if (buffer==null) return "<null>";
		
		String s=UTF8.decode(buffer).toString();
		return StringEscapeUtils.escapeJava(s);
	}
	
	@Override
	public void handleBulk(ByteBuffer contents) {
		print("handleBulk(" + decodeBuffer(contents) + ")");
	}

	@Override
	public void handleEndMultiBulk() {
		print("handleEndMultiBulk()");
	}

	@Override
	public void handleError(ByteBuffer contents) {
		print("handleError(" + decodeBuffer(contents) + ")");
	}

	@Override
	public void handleInteger(int value) {
		print("handleInteger(" + value + ")");
	}

	@Override
	public void handleStartMultiBulk(int count) {
		print("handleStartMultiBulk(" + count + ")");
	}

	@Override
	public void handleStatus(ByteBuffer contents) {
		print("handleStatus(" + decodeBuffer(contents) + ")");
	}
	
}