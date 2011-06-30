package net.rcode.core.httpserver;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Dispatches an HTTP request
 * @author stella
 *
 */
public interface HttpRequestHandler {
	/**
	 * @return a copy of the prototypical instance
	 */
	public HttpRequestHandler clone();
	
	/**
	 * Begins processing of the request.  This method will be followed by
	 * zero or more data() calls and one finish() call.
	 * @param context
	 * @param request
	 */
	public void begin(ChannelHandlerContext context, HttpRequest request, SocketAddress remote) throws Exception;
	
	/**
	 * Zero or more data frames are sent after begin
	 * @param context
	 * @param content
	 */
	public void data(ChannelHandlerContext context, ChannelBuffer content) throws Exception;
	
	/**
	 * If a trailer was received, it will be sent to this method just prior to the
	 * finish() call.
	 * @param context
	 * @param trailer
	 */
	public void trailers(ChannelHandlerContext context, HttpChunkTrailer trailer) throws Exception;
	
	/**
	 * 
	 * @param context
	 * @param trailer trailer
	 * @return ChannelFuture the completes when ready to resume the pipeline 
	 */
	public ChannelFuture finish(ChannelHandlerContext context) throws Exception;
}
