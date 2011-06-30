package net.rcode.core.redis;

import java.util.Collection;

import net.rcode.core.async.AsyncSet;
import net.rcode.core.async.Promise;

/**
 * Provide an async set backed by a redis set
 * @author stella
 *
 */
public class RedisAsyncSet extends AsyncSet {
	protected Redis connection;
	protected String key;
	
	public RedisAsyncSet(Redis connection, String key) {
		this.connection=connection;
		this.key=key;
	}
	
	@Override
	public Promise<Boolean> add(String value) {
		return connection.execute("SADD", key, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}

	@Override
	public Promise<Boolean> remove(String value) {
		return connection.execute("SREM", key, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}
	
	@Override
	public Promise<Boolean> contains(String value) {
		return connection.execute("SISMEMBER", key, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}

	@Override
	public Promise<Collection<String>> members() {
		return connection.execute("SMEMBERS", key).chain(RedisConversions.MULTI_BULK_RESULT_TO_STRINGS);
	}
	
	@Override
	public Promise<Integer> size() {
		return connection.execute("SCARD", key).chain(RedisConversions.INTEGER_RESULT_TO_INTEGER);
	}
}
