package net.rcode.core.queue;

import java.util.ArrayList;
import java.util.List;

import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;
import net.rcode.core.redis.Redis;
import net.rcode.core.redis.RedisConnections;
import net.rcode.core.redis.RedisResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens in realtime for messages on a redis queue
 * @author stella
 *
 */
public class RedisQueueDispatcher implements Redis.SubscriptionHandler {
	private static final Logger logger=LoggerFactory.getLogger(RedisQueueDispatcher.class);
	
	RedisConnections connections;
	String queuePrefix;
	RedisQueue queue;
	RedisQueueHandler handler;
	
	// -- dispatch state (synchronize on this)
	boolean ready;
	boolean dispatching;
	
	public RedisQueueDispatcher(String queuePrefix, RedisQueueHandler handler, RedisConnections connections) {
		this.connections=connections;
		this.queuePrefix=queuePrefix;
		this.queue=new RedisQueue(connections.getStreamingConnection(), queuePrefix);
		this.handler=handler;
	}
	
	public void start() {
		ready=true;
		connections.getSubscribedConnection().subscribe(this, queuePrefix + ":ready");
		dispatch();
	}
	
	protected void dispatch() {
		synchronized(this) {
			if (dispatching) return;
			ready=false;
			dispatching=true;
		}
		
		Flow<Void> flow=new Flow<Void>() {
			List<QueueEntry> batch=new ArrayList<QueueEntry>();
			
			@Override
			protected void init() {
				addOne();
			}

			void addOne() {
				// Take a new one
				add(queue.take());
				add(new Consumer<QueueEntry>() {
					@Override
					public void run(QueueEntry entry) throws Throwable {
						if (entry==null) {
							// End of queue
							if (batch.isEmpty()) {
								// Resolve straight-away
								stop();
								resolve(null);
								return;
							} else {
								// Need to dispatch the batch
								handleBatch(false);
								return;
							}
						} else {
							batch.add(entry);
							if (batch.size()>=handler.maxBatchSize()) {
								handleBatch(true);
								return;
							} else {
								addOne();
								return;
							}
						}
					}
				});
			}
			
			void handleBatch(final boolean more) {
				add(handler.handleBatch(queue, batch));
				add(new Action() {
					@Override
					public void run() throws Throwable {
						// Commit or abort entries
						for (QueueEntry entry: batch) {
							add(queue.commit(entry.id));
						}
	
						// Reset and start over
						if (more) {
							batch.clear();
							addOne();
						} else {
							finish(null);
						}
					}
				});
			}
		};
		
		flow.force(new Promise.Callback<Void>() {
			@Override
			public void complete(Promise<Void> promise) {
				try {
					promise.forceImmediate();
				} catch (Throwable t) {
					logger.error("Exception while processing message queue", t);
					// TODO: Restart the queue
				}
				
				synchronized (RedisQueueDispatcher.this) {
					dispatching=false;
					if (!ready) return;
				}
				
				dispatch();
			}
		});
	}

	@Override
	public void handleMessage(String channel, RedisResult message) {
		synchronized (this) {
			if (dispatching) {
				// Defer
				logger.debug("Scheduling followup queue read");
				ready=true;
				return;
			}
		}
		
		logger.debug("Dispatching new messages from queue");
		dispatch();
	}

}
