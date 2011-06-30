package net.rcode.core.httpserver.eventsource;

import java.net.SocketAddress;

import net.rcode.core.httpserver.HttpRequestHandler;
import net.rcode.core.httpserver.HttpServer;
import net.rcode.core.httpserver.UpgradingHttpRequestHandler;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request handler for endpoints that should upgrade to an HTML5 eventsource.
 * The process is similar to the one used for websockets.
 * See: http://dev.w3.org/html5/eventsource/
 * 
 * @author stella
 *
 */
public class EventSourceRequestHandler implements UpgradingHttpRequestHandler, Cloneable {
	private static final Logger logger=LoggerFactory.getLogger(EventSourceRequestHandler.class);
	
	/**
	 * True if the begin() method upgraded the request
	 */
	protected boolean upgraded;

	protected HttpRequest request;
	
	@Override
	public HttpRequestHandler clone() {
		try {
			return (HttpRequestHandler) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new Error(e);
		}
	}
	
	@Override
	public void begin(ChannelHandlerContext context, HttpRequest request,
			SocketAddress remote) throws Exception {
		this.request=request;
		
		String accept=request.getHeader(HttpHeaders.Names.ACCEPT);
		if (accept==null || accept.indexOf("text/event-stream")<0) {
			return;
		}
		
		// Ok, upgrade it
		upgraded=true;
		
		ChannelPipeline pipeline=context.getPipeline();

		// Send the response headers
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.addHeader(HttpHeaders.Names.CONTENT_TYPE, "text/event-stream");
		response.addHeader(HttpHeaders.Names.CACHE_CONTROL, HttpHeaders.Values.NO_CACHE);
		response.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.IDENTITY);
		response.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
		
		HttpServer.pipelineDisableCompression(pipeline);
		context.getChannel().write(response);
		
		// Re-rig the pipeline
		// Clear the pipeline of handlers
		while (pipeline.getLast()!=null) pipeline.removeLast();
		
		// Add standard logic
		pipeline.addLast("encoder", EventSourceChannelHandler.INSTANCE);
		configureEventSourcePipeline(context.getPipeline());

		// Log
		logger.debug("Upgraded eventsource request");
		HttpServer.Instance.get(context.getChannel()).logAccess(remote, request, response);
		
		// Synthesize a connect event
		Channels.fireChannelConnected(context.getChannel(), remote);
	}

	protected void configureEventSourcePipeline(ChannelPipeline pipeline) throws Exception {
	}

	@Override
	public boolean didUpgrade() {
		return upgraded;
	}

	
	/**
	 * This event will never happen except in an error flow
	 */
	@Override
	public void data(ChannelHandlerContext context, ChannelBuffer content)
			throws Exception {
	}

	/**
	 * This event will never happen except in an error flow
	 */
	@Override
	public void trailers(ChannelHandlerContext context, HttpChunkTrailer trailer)
			throws Exception {
	}

	/**
	 * This event will never happen except in an error flow
	 */
	@Override
	public ChannelFuture finish(ChannelHandlerContext context) throws Exception {
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
		response.setContent(
				ChannelBuffers.copiedBuffer("The requested method is not supported for this resource.", 
						CharsetUtil.UTF_8));
		context.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
		
		HttpServer.Instance.get(context.getChannel()).logAccess(context.getChannel().getRemoteAddress(), request, response);
		return Channels.succeededFuture(context.getChannel());
	}


}
