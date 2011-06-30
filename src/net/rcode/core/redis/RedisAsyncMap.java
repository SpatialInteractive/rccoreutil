package net.rcode.core.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.rcode.core.async.AsyncMap;
import net.rcode.core.async.Promise;

public class RedisAsyncMap extends AsyncMap {
	protected Redis connection;
	protected String key;
	
	public RedisAsyncMap(Redis connection, String key) {
		this.connection=connection;
		this.key=key;
	}

	@Override
	public Promise<Boolean> contains(String value) {
		return connection.execute("HEXISTS", key, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}

	@Override
	public Promise<String> get(String name) {
		return connection.execute("HGET", key, name).chain(RedisConversions.BULK_TO_STRING);
	}

	@Override
	public Promise<Map<String, String>> getAll() {
		return connection.execute("HGETALL", key).chain(RedisConversions.INTERLEAVED_MULTIBULK_TO_MAP);
	}

	@Override
	public Promise<Boolean> put(String name, String value) {
		return connection.execute("HSET", key, name, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}

	@Override
	public Promise<Boolean> remove(String value) {
		return connection.execute("HDEL", key, value).chain(RedisConversions.INTEGER_RESULT_TO_BOOLEAN);
	}

	@Override
	public Promise<Void> putAll(Map<String, String> updates) {
		List<Object> arguments=new ArrayList<Object>();
		arguments.add(key);
		for (Map.Entry<String, String> entry: updates.entrySet()) {
			arguments.add(entry.getKey());
			arguments.add(entry.getValue());
		}
		return connection.execute(new RedisCommand("HMSET", arguments)).chain(RedisConversions.RESULT_TO_VOID);
	}

	
}
