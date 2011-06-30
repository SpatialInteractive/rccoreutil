package net.rcode.core.redis;


/**
 * Things that generate a sequence of redis commands can
 * implement this interface to have them queued for execution
 * all at once by redis.
 * 
 * @author stella
 *
 */
public interface RedisBatchSource {
	/**
	 * Callback to execute all commands against the executor in
	 * a context such that they happen sequentially without interruption.
	 * 
	 * @param executor
	 */
	public void submitBatch(RedisExecutor executor);
}
