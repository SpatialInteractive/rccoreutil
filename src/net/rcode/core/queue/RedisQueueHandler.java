package net.rcode.core.queue;

import java.util.List;

import net.rcode.core.async.Promise;

/**
 * Handler for queue batches
 * @author stella
 *
 */
public interface RedisQueueHandler {
	/**
	 * @return the maximum number of QueueEntry instances to pop per batch
	 */
	public int maxBatchSize();
	
	/**
	 * Handle a batch of entries.  Any entries left in the batch are aborted.
	 * Any removed are committed.
	 * 
	 * @param batch
	 * @return completion promise
	 */
	public Promise<Void> handleBatch(RedisQueue queue, List<QueueEntry> batch);
}
