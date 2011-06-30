package net.rcode.core.httpserver;

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerChannelHandler extends SimpleChannelUpstreamHandler {
	private static final Logger logger=LoggerFactory.getLogger(HttpServerChannelHandler.class);
	
	/**
	 * List of dispatchers we were inited with
	 */
	private List<HttpRequestDispatcher> dispatchers;
	
	/**
	 * If non-null then we are sinking received messages here until
	 * we are in a state where we can process new work
	 */
	protected LinkedList<MessageEvent> pendingMessages;
	protected boolean pendInboundMessages;
	
	protected HttpRequest activeRequest;
	protected HttpRequestHandler activeHandler;
	
	public HttpServerChannelHandler(List<HttpRequestDispatcher> dispatchers) {
		this.dispatchers=dispatchers;
	}
	
	/**
	 * Assigns the activeHandler field to a handler which should be used to
	 * process the request or leaves it as null.
	 * @param request
	 * @throws Exception 
	 */
	protected void selectActiveHandler(HttpRequest request) throws Exception {
		for (HttpRequestDispatcher dispatcher: dispatchers) {
			HttpDispatch dispatch=dispatcher.dispatch(request);
			if (dispatch!=null) {
				activeRequest=dispatch.request;
				activeHandler=dispatch.handler;
			}
		}
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		// Divert messages if we are stalled.  We should at most get a packet full
		// of these since when we stall we also stop the channel, so this is just
		// to catch whatever the upstream decoder is picking apart
		if (pendInboundMessages || pendingMessages!=null) {
			logger.info("Pending pipelined inbound message from " + e.getRemoteAddress());
			if (pendingMessages==null) pendingMessages=new LinkedList<MessageEvent>();
			pendingMessages.addLast(e);
			return;
		}
		
		processMessage(ctx, e);
	}
	
	protected void processMessage(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		if (activeRequest==null) {
			// Head of line.  Get the request and select a handler.
			activeRequest=(HttpRequest) e.getMessage();
			selectActiveHandler(activeRequest);
			if (activeHandler==null) {
				activeHandler=errorHandler(HttpResponseStatus.NOT_FOUND, "The requested resource was not found");
			}
			
			activeHandler.begin(ctx, activeRequest, e.getRemoteAddress());
			if (activeHandler instanceof UpgradingHttpRequestHandler) {
				if (((UpgradingHttpRequestHandler)activeHandler).didUpgrade()) {
					// Stop everything
					return;
				}
			}
			
			if (!activeRequest.isChunked()) {
				// Single message transaction - all done here
				finish(ctx);
				return;
			}
		} else {
			// Continuation
			HttpChunk chunk=(HttpChunk) e.getMessage();
			if (chunk.isLast()) {
				activeHandler.trailers(ctx, (HttpChunkTrailer) chunk);
				finish(ctx);
			} else {
				activeHandler.data(ctx, chunk.getContent());
			}		
		}
	}
	
	protected void finish(final ChannelHandlerContext ctx) throws Exception {
		// Suspend read operation until the handler has committed
		// it's response.  Also start a pending pool to catch
		// stuff that the decoder is actively picking apart
		ctx.getChannel().setReadable(false);
		pendInboundMessages=true;
		
		ChannelFuture future=activeHandler.finish(ctx);
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				// TODO: Do we need to route exceptions here to somewhere
				if (!future.isSuccess()) {
					logger.warn("HttpRequestHandler did not resolve to success.  Closing connection.");
					future.getChannel().close();
					return;
				}
				
				// Clear the state
				activeRequest=null;
				activeHandler=null;
				pendInboundMessages=false;
				
				// Dispatch pending messages
				while (!pendInboundMessages) {
					if (pendingMessages==null || pendingMessages.isEmpty()) break;
					MessageEvent event=pendingMessages.removeFirst();
					processMessage(ctx, event);
				}
				
				// Release message lock
				pendingMessages=null;
				
				// Re-enable the channel
				if (ctx.getChannel().isConnected()) {
					ctx.getChannel().setReadable(true);
				}
			}
		});
	}
	
	protected HttpRequestHandler errorHandler(HttpResponseStatus status, String message) {
		return new HttpErrorRequestHandler(status, message);
	}

}
