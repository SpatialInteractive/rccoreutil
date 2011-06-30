package net.rcode.core.http;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers an http response and dispatches it to its corresponding request.
 * 
 * @author stella
 *
 */
public class AsyncHttpResponseHandler extends SimpleChannelUpstreamHandler {
	private static final Logger logger=LoggerFactory.getLogger(AsyncHttpResponseHandler.class);
	
	private AsyncHttpResponse pendingResponse;
	
	public static final ChannelLocal<LinkedList<AsyncHttpRequest>> responsePipeline=new ChannelLocal<LinkedList<AsyncHttpRequest>>() {
		@Override
		protected LinkedList<AsyncHttpRequest> initialValue(Channel channel) {
			return new LinkedList<AsyncHttpRequest>();
		}
	};
	
	protected void dispatchResponse(ChannelHandlerContext ctx) {
		AsyncHttpResponse commitedResponse=pendingResponse;
		pendingResponse=null;
		
		LinkedList<AsyncHttpRequest> pending=responsePipeline.get(ctx.getChannel());
		if (pending.isEmpty()) {
			logger.error("Received http response for non-existing request");
			ctx.getChannel().close();
			return;
		}
		
		AsyncHttpRequest request=pending.removeFirst();
		request.getResponse().resolve(commitedResponse);
		
		if (!request.isPersistConnection()) {
			ctx.getChannel().close();
		}
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		if (pendingResponse==null) {
			HttpResponse response=(HttpResponse) e.getMessage();
			pendingResponse=new AsyncHttpResponse();
			pendingResponse.header=response;
			
			if (pendingResponse.header.isChunked()) {
				// We'll keep building it up with chunk messages
				pendingResponse.content=new DynamicChannelBuffer(8192);
			} else {
				// Content is attached and we're done
				ChannelBuffer content=pendingResponse.header.getContent();
				pendingResponse.content=content;
				dispatchResponse(ctx);
			}
		} else {
			// Accumulate a chunk
			HttpChunk chunk = (HttpChunk) e.getMessage();
			if (chunk.isLast()) {
				dispatchResponse(ctx);
			} else {
				pendingResponse.content.writeBytes(chunk.getContent());
			}
		}
	}
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		super.channelClosed(ctx, e);
		
		// Cancel all outstanding
		Iterator<AsyncHttpRequest> iter=responsePipeline.get(ctx.getChannel()).iterator();
		while (iter.hasNext()) {
			AsyncHttpRequest request=iter.next();
			iter.remove();
			
			request.getResponse().resolveError(new IOException("Connection closed"));
		}
	}
}
