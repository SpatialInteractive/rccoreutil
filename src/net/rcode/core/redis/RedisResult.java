package net.rcode.core.redis;

import java.nio.ByteBuffer;

/**
 * Encapsulate a result in redis.  This is biased towards primitive results that
 * can be interpreted as a binary buffer.  There is a subclass for dealing with
 * multi-bulk replies.
 * 
 * @author stella
 *
 */
public abstract class RedisResult {
	public static final int TYPE_BULK=0;
	public static final int TYPE_STATUS=1;
	public static final int TYPE_ERROR=2;
	public static final int TYPE_INTEGER=3;
	public static final int TYPE_MULTI=4;
	
	public abstract int getType();
	
	public String getTypeName() {
		switch (getType()) {
		case TYPE_BULK: return "BULK";
		case TYPE_STATUS: return "STATUS";
		case TYPE_ERROR: return "ERROR";
		case TYPE_INTEGER: return "INTEGER";
		case TYPE_MULTI: return "MULTI";
		}
		return "Unknown";
	}
	
	/**
	 * @return true if this represents a null response (multi or bulk)
	 */
	public abstract boolean isNull();
	
	/**
	 * For primitive types, this will return the underlying buffer.
	 * @return newly sliced buffer of the result or null if a null bulk reply
	 * @throws IllegalStateException if not a primitive type
	 */
	public abstract ByteBuffer getBuffer();
	
	/**
	 * @return decoded string value
	 * @throws IllegalStateException if not a primitive type
	 */
	public abstract String getString();
	
	/**
	 * @return the integral value
	 * @throws NumberFormatException if the response does not represent a number
	 * @throws IllegalStateException if not a primitive type
	 */
	public int getInteger() {
		return Integer.parseInt(getString());
	}
	
	/**
	 * If this is an error response, throw the error.
	 * @throws RuntimeException
	 */
	public void ifError() throws RuntimeException {
	}
	
	/**
	 * @return the result as a multi result.  This can be used on any result type
	 * to get back a list context
	 */
	public RedisMultiResult getAsMulti() {
		return new RedisMultiResult(this);
	}
}
