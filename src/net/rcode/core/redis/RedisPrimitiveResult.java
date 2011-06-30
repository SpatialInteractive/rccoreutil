package net.rcode.core.redis;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class RedisPrimitiveResult extends RedisResult {
	private static final Charset UTF8=Charset.forName("UTF-8");
	
	private ByteBuffer buffer;
	private int type;
	private String stringValue;
	
	public RedisPrimitiveResult(int type, ByteBuffer buffer) {
		this.type=type;
		this.buffer=buffer;
	}
	
	public RedisPrimitiveResult(int type, String string) {
		this(type, UTF8.encode(string));
	}

	@Override
	public ByteBuffer getBuffer() {
		if (buffer==null) return null;
		else return buffer.slice();
	}

	@Override
	public String getString() {
		if (stringValue==null && buffer!=null) {
			stringValue=UTF8.decode(buffer).toString();
		}
		return stringValue;
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public boolean isNull() {
		return buffer==null;
	}
	
	@Override
	public void ifError() throws RuntimeException {
		if (type==TYPE_ERROR) {
			throw new RuntimeException("Redis error: " + getString());
		}
	}
	
	@Override
	public String toString() {
		return String.format("RedisResult(%s,'%s')", getTypeName(), getString());
	}
}
