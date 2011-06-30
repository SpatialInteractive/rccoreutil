package net.rcode.core.redis;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;

import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage a streaming connection to a single redis instance.
 * @author stella
 *
 */
public final class Redis extends RedisResultBuilder implements RedisExecutor {
	static final Logger logger=LoggerFactory.getLogger(Redis.class);
	static final int COMBINE_BUFFER_LIMIT=1300;
	
	
	Object lock=this;
	RedisManager manager;
	RedisSerializer serializer;
	RedisParser parser;
	ConnectionState connectionState;
	ByteBuffer combineBuffer=ByteBuffer.allocateDirect(COMBINE_BUFFER_LIMIT);
	int transmissionLock;
	
	/**
	 * Some commands change the connection state.  When one of those comes through, we
	 * add it to the setupCommands hash keyed by the command name.  When a new connection
	 * is made, the setupCommands will be re-applied to the connection prior to
	 * anything else.
	 */
	LinkedHashMap<String, RedisCommand> setupCommands=new LinkedHashMap<String, RedisCommand>();

	// - subscription management
	HashMap<String, List<SubscriptionHandler>> subscribedChannels=new HashMap<String, List<SubscriptionHandler>>();
	
	public static interface SubscriptionHandler {
		public void handleMessage(String channel, RedisResult message);
	}
	
	static class QueuedCommand {
		public RedisCommand command;
		public Promise<RedisResult> result;
		
		public QueuedCommand(RedisCommand command) {
			this.command=command;
			this.result=new Promise<RedisResult>();
		}
		public QueuedCommand(RedisCommand command, Promise<RedisResult> result) {
			this.command=command;
			this.result=result;
		}
	}
	
	public Redis(RedisManager manager, int database) {
		this.manager=manager;
		this.parser=new RedisParser(this);
		this.serializer=new RedisSerializer();
		this.connectionState=new DisconnectedConnectionState();
		if (database!=0) {
			execute("SELECT", String.valueOf(database));
		}
	}
	
	void suspendTransmit() {
		synchronized (lock) {
			transmissionLock++;
		}
	}
	
	void resumeTransmit() {
		synchronized (lock) {
			if (--transmissionLock == 0) {
				connectionState.queueCommand(null, false);
			}
		}
	}
	
	@Override
	protected void handleResult(RedisResult result) {
		connectionState.handleResult(result);
	}
	
	public void subscribe(SubscriptionHandler handler, String... channels) {
		synchronized (lock) {
			for (String channel: channels) {
				subscribeChannel(handler, channel);
			}
		}
	}
	
	public void unsubscribe(SubscriptionHandler handler, String... channels) {
		synchronized (lock) {
			for (String channel: channels) {
				unsubscribeChannel(handler, channel);
			}
		}
	}
	
	private void unsubscribeChannel(SubscriptionHandler handler, String channel) {
		List<SubscriptionHandler> handlers=subscribedChannels.get(channel);
		if (handlers==null) return;
		
		int index=handlers.indexOf(handler);
		if (index<0) return;
		
		handlers.remove(index);
		if (handlers.isEmpty()) {
			// Unsubscribe
			subscribedChannels.remove(channel);
			queueCommand(new QueuedCommand(new RedisCommand("UNSUBSCRIBE", channel)));
		}
	}
	
	private void subscribeChannel(SubscriptionHandler handler, String channel) {
		List<SubscriptionHandler> handlers=subscribedChannels.get(channel);
		if (handlers==null) {
			// Need to issue a subscribe
			handlers=new ArrayList<SubscriptionHandler>(1);
			handlers.add(handler);
			queueCommand(new QueuedCommand(new RedisCommand("SUBSCRIBE", channel)));
			subscribedChannels.put(channel, handlers);
		} else {
			handlers.add(handler);
		}
	}

	void dispatchMessage(String channel, String pattern, RedisResult message) {
		List<SubscriptionHandler> handlers;
		
		synchronized (lock) {
			if (pattern==null) {
				// Dispatch to channel listener
				handlers=subscribedChannels.get(channel);
			} else {
				// TODO: Patterns
				handlers=null;
			}
		}
		
		if (handlers!=null) {
			for (SubscriptionHandler handler: handlers) {
				try {
					handler.handleMessage(channel, message);
				} catch (Throwable t) {
					logger.error("Error in message handler", t);
				}
			}
		}
	}
	
	public Promise<Boolean> quit() throws IOException {
		synchronized (lock) {
			// Not quite right - race condition on start
			if (connectionState instanceof DisconnectedConnectionState) {
				return Promise.fixed(true);
			} else {
				QueuedCommand qc=new QueuedCommand(new RedisCommand("QUIT"));
				queueCommand(qc);
				return qc.result.chain(new Promise.Chain<RedisResult,Boolean>() {
					@Override
					public Boolean chain(RedisResult input) throws Throwable {
						return true;
					}
				});
			}
		}
	}
	
	/**
	 * Execute a batch of commands atomically
	 * @param commands
	 */
	public void execute(RedisBatchSource source) {
		synchronized (lock) {
			try {
				suspendTransmit();
				source.submitBatch(this);
			} finally {
				resumeTransmit();
			}
			
		}
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.redis.RedisExecutor#execute(java.lang.String, java.lang.Object)
	 */
	public void execute(Promise<RedisResult> result, RedisCommand rc) {
		if (RedisConstants.SUBSCRIBE_COMMANDS.contains(rc.command)) {
			throw new IllegalStateException("Cannot execute subscribe commands with generic execute() method");
		} else if (!subscribedChannels.isEmpty()) {
			throw new IllegalStateException("Cannot issue generic commands while in pub/sub state");
		}
		
		QueuedCommand qc=new QueuedCommand(rc, result);
		queueCommand(qc);
	}
	
	public Promise<RedisResult> execute(RedisCommand rc) {
		Promise<RedisResult> result=new Promise<RedisResult>();
		execute(result, rc);
		return result;
	}
	
	@Override
	public void execute(Promise<RedisResult> result, String command,
			Object... arguments) {
		execute(result, new RedisCommand(command, arguments));
	}
	
	@Override
	public Promise<RedisResult> execute(String command, Object... arguments) {
		return execute(new RedisCommand(command, arguments));
	}
	
	public void executeAndForget(String command, Object...arguments) {
		execute(command, arguments);
	}
	
	public Flow.Initiator<RedisResult> executeStep(final String command, final Object... arguments) {
		final RedisCommand commandObject=new RedisCommand(command, arguments);
		return new Flow.Initiator<RedisResult>() {
			@Override
			public Promise<RedisResult> run() throws Throwable {
				return execute(commandObject);
			}
			
			@Override
			public String toString() {
				return "RedisExecute " + commandObject;
			}
		};
	}
	
	void queueCommand(QueuedCommand qc) {
		synchronized (lock) {
			if (RedisConstants.STATE_COMMANDS.contains(qc.command.command)) {
				setupCommands.put(qc.command.command, qc.command);
			}
			connectionState.queueCommand(qc, transmissionLock>0);
		}
	}

	/**
	 * Called by RedisManager to associate a connection with this instance.  This also moves the client
	 * to the connected state
	 * @param address
	 * @param connection
	 */
	void bind(SocketAddress address, Channel channel) throws IOException {
		DisconnectedConnectionState dcs;
		try {
			dcs=(DisconnectedConnectionState) connectionState;
		} catch (ClassCastException e) {
			logger.error("Attempt to bind Redis connection that is already connected");
			channel.close();
			throw new IllegalStateException("Attempt to bind Redis connection that is already connected");
		}
		
		dcs.bind(address, channel);
	}
	
	void connectionClosed() {
		try {
			((ConnectedConnectionState)connectionState).connectionClosed();
		} catch (ClassCastException e) {
			logger.error("Connection closed in illegal connection state");
		}
	}
	
	void writeComplete(long amountWritten) {
		try {
			((ConnectedConnectionState)connectionState).writeComplete(amountWritten);
		} catch (ClassCastException e) {
			logger.error("Connection write complete in illegal connection state");
		}
	}
	
	// -- state classes
	public static final int STATE_DISCONNECTED=0;
	public static final int STATE_CONNECTED=1;
	
	abstract class ConnectionState {
		abstract int getState();
		abstract void queueCommand(QueuedCommand qc, boolean disableTransmit);
		void handleResult(RedisResult result) {
			throw new IllegalStateException("Results not supported in this connection state");
		}
	}
	
	class DisconnectedConnectionState extends ConnectionState {
		LinkedList<QueuedCommand> pendingCommands=new LinkedList<QueuedCommand>();
		
		@Override
		int getState() {
			return STATE_DISCONNECTED;
		}
		
		@Override
		public void queueCommand(QueuedCommand qc, boolean disableTransmit) {
			if (qc!=null) pendingCommands.add(qc);
		}
		
		void bind(SocketAddress address, Channel channel) {
			synchronized (lock) {
				ConnectedConnectionState ccs=new ConnectedConnectionState(channel);
				connectionState=ccs;
				if (!pendingCommands.isEmpty()) {
					for (QueuedCommand qc: pendingCommands) {
						ccs.queueCommand(qc, false);
					}
				}
			}
			logger.info("Connected redis");
		}
	}
	
	class SubscriptionConnectionState extends ConnectedConnectionState {

		SubscriptionConnectionState(ConnectedConnectionState other) {
			super(other);
		}
		public SubscriptionConnectionState(Channel channel) {
			super(channel);
		}
		
		@Override
		boolean divertTransmission(QueuedCommand next) {
			return false;
		}

		boolean willDivert(QueuedCommand next) {
			return false;
		}

		@Override
		void handleResult(RedisResult result) {
			RedisMultiResult multi=result.getAsMulti();
			String kind=multi.get(0).getString();
			String channel, pattern;
			RedisResult payload;
			
			if ("message".equals(kind)) {
				// Dispatch message
				channel=multi.get(1).getString();
				pattern=null;
				payload=multi.get(2);
				dispatchMessage(channel, pattern, payload);
				return;
			} else if ("pmessage".equals(kind)) {
				// Dispatch pattern message
				pattern=multi.get(1).getString();
				channel=multi.get(2).getString();
				payload=multi.get(3);
				dispatchMessage(channel, pattern, payload);
				return;
			}
			
			synchronized (lock) {
				// Subscription management.  All we really care about is
				// the resultant count.
				if (RedisConstants.SUBSCRIPTION_MESSAGE_TYPES.contains(kind)) {
					// Count is last
					int remaining=multi.get(multi.getCount()-1).getInteger();
					if (remaining==0) {
						// Get out of this state
						ConnectedConnectionState csc=new ConnectedConnectionState(this);
						connectionState=csc;
						logger.info("Left subscription state");
					}
					
					// Pop an active message and acknowledge it
					QueuedCommand ackQc=activeCommands.removeFirst();
					if (ackQc.result!=null) ackQc.result.resolve(result);
				}
			}
		}
	}
	
	class ConnectedConnectionState extends ConnectionState  {
		Channel channel;
		
		/**
		 * Commands which have not yet been sent to the pipeline
		 */
		LinkedList<QueuedCommand> pendingCommands;
		LinkedList<QueuedCommand> activeCommands;
		QueuedCommand transmittingCommand;
		long transmitting;
		
		private long writeStartTime;
		
		ConnectedConnectionState(Channel channel) {
			this.channel=channel;
			this.pendingCommands=new LinkedList<QueuedCommand>();
			this.activeCommands=new LinkedList<QueuedCommand>();
		}
		
		ConnectedConnectionState(ConnectedConnectionState other) {
			this.channel=other.channel;
			this.pendingCommands=other.pendingCommands;
			this.activeCommands=other.activeCommands;
		}
		
		void connectionClosed() {
			DisconnectedConnectionState dcs=new DisconnectedConnectionState();
			
			RedisResult error=new RedisPrimitiveResult(RedisResult.TYPE_ERROR, "Connection Closed");
			for (QueuedCommand qc: activeCommands) {
				if (qc.result!=null) qc.result.resolve(error);
			}
			for (QueuedCommand qc: pendingCommands) {
				if (qc.result!=null) qc.result.resolve(error);
			}
			
			/*
			int carriedForward;
			dcs.pendingCommands.addAll(activeCommands);
			dcs.pendingCommands.addAll(pendingCommands);
			
			carriedForward=dcs.pendingCommands.size();
			connectionState=dcs;
			
			logger.info("Redis connection closed with " + carriedForward + " neglected commands");
			*/
		}
		
		@Override
		void handleResult(RedisResult result) {
			try {
				QueuedCommand next;
				
				synchronized (lock) {
					next=activeCommands.removeFirst();
				}
				
				if (next.result!=null) next.result.resolve(result);
			} catch (NoSuchElementException e) {
				logger.error("Command received when no active commands");
				forceClose();
			}
		}
		
		void forceClose() {
			try {
				channel.close();
				channel=null;
			} catch (Exception e) {
				logger.error("Error force closing connection", e);
			}
		}

		@Override
		int getState() {
			return STATE_CONNECTED;
		}

		@Override
		public void queueCommand(QueuedCommand qc, boolean disableTransmit) {
			if (qc!=null) pendingCommands.add(qc);
			if (transmitting<=0 && !disableTransmit) {
				transmitNext();
			}
		}
		
		boolean divertTransmission(QueuedCommand next) {
			String commandName=next.command.command;
			if ("SUBSCRIBE".equals(commandName) || "PSUBSCRIBE".equals(commandName)) {
				// Need to enter sub state
				SubscriptionConnectionState scs=new SubscriptionConnectionState(this);
				scs.activeCommands=activeCommands;
				scs.pendingCommands=pendingCommands;
				scs.transmitting=transmitting;
				connectionState=scs;
				
				// Note that the writeComplete may happen in the scope of this call
				scs.transmitNext();
				if (logger.isDebugEnabled()) logger.debug("Entered subscription state");
				return true;
			} else {
				return false;
			}	
		}
		
		boolean willDivert(QueuedCommand next) {
			String commandName=next.command.command;
			return "SUBSCRIBE".equals(commandName) || "PSUBSCRIBE".equals(commandName);
		}
		
		void transmitNext() {
			combineBuffer.clear();
			while (!pendingCommands.isEmpty()) {
				QueuedCommand next=pendingCommands.getFirst();
				
				// Does the next command signal a state change?
				// If so, dump what we have thus far and only change state
				// if there is nothing pending
				if (willDivert(next)) {
					// Flush the current combineBuffer and bail
					if (combineBuffer.position()>0) {
						break;
					} else {
						divertTransmission(next);
					}
					return;
				}
				
				// Will the next buffer fit onto the current combineBuffer?
				ByteBuffer nextBuffer=next.command.getBuffer();
				if (nextBuffer.remaining()<combineBuffer.remaining()) {
					// It fits.  Combine it with the previous.
					//if (logger.isDebugEnabled() && combineBuffer.position()>0) {
					//	logger.debug("Combining transmission packet with previous");
					//}
					combineBuffer.put(nextBuffer);
					pendingCommands.removeFirst();
					activeCommands.add(next);
					continue;
				}
				
				// It doesn't fit.  If we have anything pending in the combined buffer,
				// just break to transmit that.  Otherwise, transmit this.
				if (combineBuffer.position()>0) break;
				
				// Transmit this packet
				pendingCommands.removeFirst();
				activeCommands.add(next);
				writeBuffer(nextBuffer);
				return;
			}
			
			// If we have anything pending in the combineBuffer, send it
			if (combineBuffer.position()>0) {
				combineBuffer.flip();
				writeBuffer(combineBuffer);
			}
		}
		
		void writeBuffer(ByteBuffer buffer) {
			synchronized (lock) {
				writeStartTime=System.currentTimeMillis();
				transmitting+=buffer.remaining();
			}
			channel.write(buffer);
		}

		void writeComplete(long amountWritten) {
			// Hairpin another write
			synchronized (lock) {
				transmitting-=amountWritten;
				if (transmitting<=0) {
					//System.err.println("Pending write complete");
					transmitting=0;
					if (!pendingCommands.isEmpty()) {
						transmitNext();
					}
				}
			}
		}
	}
}
