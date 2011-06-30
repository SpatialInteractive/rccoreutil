package net.rcode.core.redis;

import java.io.IOException;

import net.rcode.core.async.Promise;

/**
 * Abstraction for things that accept redis commands.  The main redis client
 * and the MultiCommand do this.
 * 
 * @author stella
 *
 */
public interface RedisExecutor {
	public Promise<RedisResult> execute(RedisCommand command);
	public void execute(Promise<RedisResult> result, RedisCommand command);
	
	/**
	 * Queue the given command and arguments
	 * @param command
	 * @param arguments
	 * @return Promise to deliver a result
	 * @throws IOException
	 */
	public abstract Promise<RedisResult> execute(String command,
			Object... arguments);

	/**
	 * Execute a command with a pre-allocated result promise.
	 * @param result
	 * @param command
	 * @param arguments
	 */
	public abstract void execute(Promise<RedisResult> result, String command, Object...arguments);
	
	/**
	 * Execute a command where we don't care about the result.  This may remove
	 * some extra allocations in the case of Multi's but is not recommended
	 * for general use.
	 * 
	 * @param command
	 * @param arguments
	 */
	public abstract void executeAndForget(String command, Object... arguments);
	
}