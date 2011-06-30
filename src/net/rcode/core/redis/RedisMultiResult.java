package net.rcode.core.redis;

import java.nio.ByteBuffer;

public class RedisMultiResult extends RedisResult {
	private RedisResult[] items;
	
	public RedisMultiResult(RedisResult singleton) {
		this.items=new RedisResult[] { singleton };
	}
	
	public RedisMultiResult() {
		this.items=null;
	}
	
	public RedisMultiResult(RedisResult[] items) {
		this.items=items;
	}
	
	public int getCount() {
		if (items==null) return 0;
		else return items.length;
	}
	
	public RedisResult get(int index) {
		return items[index];
	}
	
	@Override
	public ByteBuffer getBuffer() {
		throw new IllegalStateException("Not a primitive type");
	}

	@Override
	public String getString() {
		throw new IllegalStateException("Not a primitive type");
	}

	@Override
	public int getType() {
		return TYPE_MULTI;
	}

	@Override
	public boolean isNull() {
		return items==null;
	}
	
	@Override
	public RedisMultiResult getAsMulti() {
		return this;
	}
	
	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		sb.append("RedisMultiResult(");
		if (items==null) {
			sb.append("null");
		} else {
			sb.append("[");
			for (int i=0; i<items.length; i++) {
				sb.append(items[i].toString());
			}
			sb.append("]");
		}
		sb.append(")");
		return sb.toString();
	}
}
