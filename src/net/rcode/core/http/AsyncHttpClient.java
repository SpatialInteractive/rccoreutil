package net.rcode.core.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

import net.rcode.core.async.Promise;
import net.rcode.core.io.ChannelFuturePromise;
import net.rcode.core.io.DnsResolver;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide a bridge accessing netty http services.
 * 
 * @author stella
 *
 */
public class AsyncHttpClient {
	static final Logger logger=LoggerFactory.getLogger(AsyncHttpClient.class);
	
	public class Transaction {
		Promise<Channel> channel;
		int port;
		
		Transaction(String host, int port) {
			this.port=port;
			
			Promise<InetAddress> address=DnsResolver.DEFAULT.lookup(host);
			channel=startConnect(address);
		}
		
		Promise<Channel> startConnect(Promise<InetAddress> address) {
			return address.chain(new Promise.AsyncChain<InetAddress,Channel>() {
				@Override
				public void chain(InetAddress address, Promise<Channel> result) throws IOException {
					connect(address, port).forward(result);
				}
			});
		}


		/*
		public void submit(final AsyncHttpRequest request, ByteBuffer data) {
			Buffer buffer;
			if (data!=null) buffer=new ByteBufferWrapper(data);
			else buffer=null;
			
			submit(request, buffer);
		}
		
		public void submit(final AsyncHttpRequest request, byte[] data) {
			Buffer buffer;
			if (data!=null) buffer=HeapBuffer.wrap(data);
			else buffer=null;
			submit(request, buffer);
		}
		*/
		
		public void submit(final AsyncHttpRequest request) {
			HttpRequest httpRequest=request.getRequestPacket();
			httpRequest.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
			
			
			channel.force(new Promise.Callback<Channel>() {
				@Override
				public void complete(Promise<Channel> promise) {
					Channel channel=null;
					try {
						channel=promise.forceImmediate();
						AsyncHttpResponseHandler.responsePipeline.get(channel).addLast(request);
						channel.write(request.getRequestPacket());
					} catch (Exception e) {
						request.getResponse().resolveError(e);
						channel.close();
					}
				}
			});
		}
		
		/**
		 * Close this transaction, releasing resources
		 */
		public void close() {
			channel.force(new Promise.Callback<Channel>() {
				@Override
				public void complete(Promise<Channel> promise) {
					promise.forceImmediate().close();
				}
			});
		}
	}
	
	ClientBootstrap bootstrap;
	
	public AsyncHttpClient(ChannelFactory channelFactory, Map<String, Object> options) throws IOException {
		bootstrap=new ClientBootstrap(channelFactory);
		if (options!=null) bootstrap.setOptions(options);
		bootstrap.setPipelineFactory(new AsyncHttpPipelineFactory());
	}
	
	Promise<Channel> connect(InetAddress address, int port) throws IOException {
		SocketAddress dest=new InetSocketAddress(address, port);
		return new ChannelFuturePromise(bootstrap.connect(dest));
	}
	
	/**
	 * Start a new plain transaction for the given host and port
	 * @param host
	 * @param port
	 * @return transaction
	 */
	public Transaction startTransaction(String host, int port) {
		return new Transaction(host, port);
	}
	
}
