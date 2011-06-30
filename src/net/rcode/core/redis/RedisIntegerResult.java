package net.rcode.core.redis;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class RedisIntegerResult extends RedisResult {
	private int value;
	
	public RedisIntegerResult(int value) {
		this.value=value;
	}
	
	@Override
	public ByteBuffer getBuffer() {
		String svalue=String.valueOf(value);
		return Charset.forName("US-ASCII").encode(svalue);
	}

	@Override
	public String getString() {
		return String.valueOf(value);
	}

	@Override
	public int getInteger() {
		return value;
	}
	
	@Override
	public int getType() {
		return TYPE_INTEGER;
	}

	@Override
	public boolean isNull() {
		return false;
	}

	@Override
	public String toString() {
		return String.format("RedisIntegerResult(%s)", value);
	}
}
