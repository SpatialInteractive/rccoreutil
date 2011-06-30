package net.rcode.core.queue;

import java.util.Map;

import net.rcode.core.async.AsyncMap;
import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;
import net.rcode.core.redis.Redis;
import net.rcode.core.redis.RedisAsyncMap;
import net.rcode.core.redis.RedisConversions;
import net.rcode.core.redis.RedisResult;
import net.rcode.core.redis.RedisSteps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Put messages on a Redis queue
 * @author stella
 *
 */
public class RedisQueue {
	static final Logger logger=LoggerFactory.getLogger(RedisQueue.class);
	
	private Redis connection;
	private String queuePrefix;
	
	public RedisQueue(Redis connection, String queuePrefix) {
		this.connection=connection;
		this.queuePrefix=queuePrefix;
	}
	
	/**
	 * Takes the next queue entry and returns it.  The queue entry is
	 * transferred to the pending queue and must be committed with a call
	 * to commit() when done.
	 * @return QueueEntry or null
	 */
	public Promise<QueueEntry> take() {
		Flow<QueueEntry> flow=new Flow<QueueEntry>() {
			String itemId;
			
			@Override
			protected void init() {
				takeNext();
			}

			void takeNext() {
				add(connection.execute("RPOPLPUSH", queuePrefix, queuePrefix + ":pending"));
				add(new Consumer<RedisResult>() {
					@Override
					public void run(RedisResult result) throws Throwable {
						result.ifError();
						
						if (result.isNull()) {
							// No more items
							resolve(null);
							return;
						}
						
						// We have a next item
						itemId=result.getString();
						readEntry();
					}
				});
			}
			
			void readEntry() {
				add(connection.execute("HGETALL", queuePrefix + ':' + itemId));
				add(RedisConversions.INTERLEAVED_MULTIBULK_TO_MAP);
				add(new Consumer<Map<String,String>>() {
					@Override
					public void run(Map<String, String> entry) throws Throwable {
						if (entry.isEmpty()) {
							// The entry has expired.  Clear it from pending and loop
							clearAndTryNext();
						} else {
							// This'l do
							resolve(new QueueEntry(itemId, entry));
						}
					}
				});
			}
			
			void clearAndTryNext() {
				add(connection.execute("LREM", queuePrefix + ":pending", "-1", itemId));
				add(RedisConversions.RESULT_TO_VOID);
				
				// The underlying entry didn't exist, but make sure to delete anyway
				// in case if this was just a data problem
				add(connection.execute("DEL", queuePrefix + ':' + itemId));
				add(RedisConversions.RESULT_TO_VOID);
				takeNext();
			}
		};
		
		return flow;
	}
	
	/**
	 * Commit a previously taken entry
	 * @param entryId
	 * @return
	 */
	public Promise<Void> commit(final String entryId) {
		Flow<Void> flow=new Flow<Void>();
		
		flow.add(connection.execute("LREM", queuePrefix + ":pending", "-1", entryId));
		flow.add(RedisConversions.RESULT_TO_VOID);
		
		// The underlying entry didn't exist, but make sure to delete anyway
		// in case if this was just a data problem
		flow.add(connection.execute("DEL", queuePrefix + ':' + entryId));
		flow.add(RedisConversions.RESULT_TO_VOID);
		flow.finish(null);
		
		return flow;
	}
	
	/**
	 * Puts an item on the queue, optionally with a ttl
	 * @param item
	 * @param ttl
	 * @return item id
	 */
	public Promise<String> put(final Map<String,String> item, final int ttl) {
		Flow<String> flow=new Flow<String>() {
			String itemId;
			
			@Override
			protected void init() {
				add(connection.executeStep("INCR", queuePrefix + ":id"));
				
				// Get the id and write the item hash
				add(new AsyncTransform<RedisResult,Void>() {
					@Override
					public Promise<Void> run(RedisResult result) throws Throwable {
						result.ifError();
						
						itemId=result.getString();
						
						// Write the main item
						AsyncMap map=new RedisAsyncMap(connection, queuePrefix + ':' + String.valueOf(itemId));
						return map.putAll(item);
					}
				});
				
				// Apply timeout
				if (ttl>0) {
					add(new Initiator<RedisResult>() {
						@Override
						public Promise<RedisResult> run() throws Throwable {
							return connection.execute("EXPIRE", queuePrefix + ':' + String.valueOf(itemId), ttl);
						}
					});
					add(RedisSteps.FAIL_ON_ERROR);
				}
				
				// Add the id to the queue list
				add(new Initiator<RedisResult>() {
					@Override
					public Promise<RedisResult> run() throws Throwable {
						return connection.execute("LPUSH", queuePrefix, String.valueOf(itemId));
					}
				});
				
				// Act on the size of the list.  If 1, publish to the notify channel (ie. level
				// triggered).  Otherwise, finish the flow.
				add(new Consumer<RedisResult>() {
					@Override
					public void run(RedisResult result) throws Throwable {
						result.ifError();
						if (result.getInteger()==1) {
							publishReady();
						} else {
							resolve(itemId);
						}
					}
				});
			}
			
			/**
			 * Publishes the queue ready event and resolves the flow
			 */
			void publishReady() {
				add(connection.executeStep("PUBLISH", queuePrefix + ":ready", "1"));
				add(new Consumer<RedisResult>() {
					@Override
					public void run(RedisResult result) throws Throwable {
						result.ifError();
						
						if (logger.isDebugEnabled() && result.getInteger()>0) {
							logger.debug("Triggered queue " + queuePrefix);
						}
						
						resolve(itemId);
					}
				});
			}
		};
		
		return flow;
	}
}
