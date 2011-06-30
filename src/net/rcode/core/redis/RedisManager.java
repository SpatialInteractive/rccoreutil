package net.rcode.core.redis;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import net.rcode.core.async.Promise;
import net.rcode.core.io.DnsResolver;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.netty.channel.Channels.*;

/**
 * Come here for connections to Redis.
 * @author stella
 *
 */
public class RedisManager {
	public static final int DEFAULT_PORT=6379;
	
	static final Logger logger=LoggerFactory.getLogger(RedisManager.class);
	
	ClientBootstrap bootstrap;
	static final ChannelLocal<Redis> REDIS_INSTANCE_ATTR=new ChannelLocal<Redis>();
	private int database;
	
	public RedisManager(ChannelFactory channelFactory, Map<String, Object> options) {
		bootstrap=new ClientBootstrap(channelFactory);
		if (options!=null) bootstrap.setOptions(options);
		
		ChannelPipeline pipeline=pipeline();
		pipeline.addLast("input", new InputChannelHandler());
		pipeline.addLast("output", new OutputChannelHandler());
		bootstrap.setPipeline(pipeline);
	}
	
	public int getDatabase() {
		return database;
	}
	
	public void setDatabase(int database) {
		this.database = database;
	}
	
	/**
	 * Initiate a new connection to Redis.  The returned redis instance will connect
	 * in the background
	 * @param host
	 * @param port
	 * @return instance
	 * @throws IOException 
	 */
	public Redis connect(SocketAddress address) throws IOException {
		Redis redis=new Redis(this, database);
		connectAndBind(address, redis);
		return redis;
	}
	
	/**
	 * Initiate a connection to a given redis host and port
	 * @param host
	 * @param port
	 * @return redis
	 */
	public Redis connect(String host, final int port) {
		final Redis redis=new Redis(this, database);
		DnsResolver.DEFAULT.lookup(host).force(new Promise.Callback<InetAddress>() {
			@Override
			public void complete(Promise<InetAddress> promise) {
				if (promise.isError()) {
					logger.error("Error resolving hostname for redis connection (OOPS - not implemented yet)", promise.getError());
					return;
				}
				
				SocketAddress address=new InetSocketAddress(promise.getResolution(), port);
				try {
					connectAndBind(address, redis);
				} catch (IOException e) {
					logger.error("Error initiating connection to redis (OOPS - not implemented yet)", e);
				}
			}
		});
		return redis;
	}
	
	/**
	 * Initiate a connection to the default redis on this host
	 * @return
	 */
	public Redis connect() {
		return connect("localhost", DEFAULT_PORT);
	}
	
	/**
	 * Initiates a new connection and binds it to the redis instance
	 * @param address
	 * @param redis
	 * @throws IOException 
	 */
	void connectAndBind(final SocketAddress address, final Redis redis) throws IOException {
		if (logger.isDebugEnabled()) logger.debug("Starting connection to " + address);
		ChannelFuture future=bootstrap.connect(address);
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (!future.isDone()) return;
				
				if (future.isSuccess()) {
					Channel channel=future.getChannel();
					REDIS_INSTANCE_ATTR.set(channel, redis);
					redis.bind(address, channel);
				} else if (future.isCancelled()) {
					logger.error("Cancelled connecting to redis (OOPS - not implemented yet)");
					return;
				} else {
					logger.error("Error connecting to redis (OOPS - not implemented yet)", future.getCause());
				}
			}
		});
		
	}

	class InputChannelHandler extends SimpleChannelUpstreamHandler {
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
				throws Exception {
			super.channelClosed(ctx, e);
			
			Redis instance=REDIS_INSTANCE_ATTR.get(ctx.getChannel());
			logger.info("RedisManager.InputChannelHandler: channelClosed");
			instance.connectionClosed();
		}
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			super.messageReceived(ctx, e);
			logger.info("RedisManager.InputChannelHandler: messageReceived");
			
			Redis instance=REDIS_INSTANCE_ATTR.get(ctx.getChannel());
			
			ChannelBuffer content=(ChannelBuffer) e.getMessage();
			instance.parser.write(content.toByteBuffer());	// TODO: Convert internals to native ChannelBuffer
		}
		
		@Override
		public void writeComplete(ChannelHandlerContext ctx,
				WriteCompletionEvent e) throws Exception {
			super.writeComplete(ctx, e);
			logger.info("RedisManager.InputChannelHandler: writeComplete");
			
			Redis instance=REDIS_INSTANCE_ATTR.get(ctx.getChannel());
			instance.writeComplete(e.getWrittenAmount());
		}
	}
	
	class OutputChannelHandler extends SimpleChannelDownstreamHandler {
		@Override
		public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			// TODO: Operate natively on ChannelBuffer
			ByteBuffer bb=(ByteBuffer) e.getMessage();
			ChannelBuffer cb=ChannelBuffers.wrappedBuffer(bb);
			e=new DownstreamMessageEvent(e.getChannel(), e.getFuture(), cb, e.getRemoteAddress());
			
			logger.info("RedisManager.OutputChannelHandler: writeRequested(" + cb.readableBytes() + ")");

			ctx.sendDownstream(e);
		}
	}
	
}
