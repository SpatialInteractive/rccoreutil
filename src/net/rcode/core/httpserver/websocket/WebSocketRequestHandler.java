package net.rcode.core.httpserver.websocket;

import java.net.SocketAddress;
import java.security.MessageDigest;

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
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request handler that handles basic http signalling to upgrade to
 * a websocket and then passes control off to a WebSocketApplication.
 * @author stella
 *
 */
public class WebSocketRequestHandler implements UpgradingHttpRequestHandler, Cloneable {
	/**
	 * Default frame size == 1mb
	 */
	public static final int DEFAULT_MAX_FRAME_SIZE=1 * 1024 * 1024;
	
	private static final Logger logger=LoggerFactory.getLogger(WebSocketRequestHandler.class);
	
	/**
	 * True if the begin() method upgraded the request
	 */
	protected boolean upgraded;

	protected HttpRequest request;
	protected SocketAddress remoteAddress;
	
	@Override
	public HttpRequestHandler clone() {
		try {
			return (HttpRequestHandler) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new Error(e);
		}
	}
	
	@Override
	public boolean didUpgrade() {
		return upgraded;
	}
	
	/**
	 * Handle upgrade requests and perform key negotiation.  If not a websocket
	 * request, fall through to normal processing.  Otherwise, re-rig the pipeline
	 * appropriately.
	 */
	@Override
	public void begin(ChannelHandlerContext context, HttpRequest request,
			SocketAddress remote) throws Exception {
		this.request=request;
		this.remoteAddress=remote;
		
		// Detect websocket upgrade request
		if (request.getMethod()==HttpMethod.GET &&
				Values.UPGRADE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)) &&
				Values.WEBSOCKET.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.UPGRADE))) {
			if (!handshakeWebSocket(context)) {
				context.getChannel().close();
			} else {
				upgraded=true;
			}
		}
	}

	/**
	 * Called from begin on Connection: upgrade && Upgrade: websocket
	 * @param context
	 * @param request
	 * @param remote
	 * @throws Exception
	 */
	protected boolean handshakeWebSocket(ChannelHandlerContext context) throws Exception {
		HttpResponse response;
		ChannelBuffer responseTrailer;
		ChannelBuffer remainder;
		
		// Branch based on handshake needs
		if (true) {
			// Default draft76 handshake.
			response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,
					new HttpResponseStatus(101, "WebSocket"));
			
			// Figure protocol
			String protocol=request.getHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL);
			if (protocol!=null) {
				protocol=selectProtocol(protocol);
				if (protocol!=null) {
					response.addHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL, protocol);
				}
			}
			
			// Upgrade and Connection headers
			response.addHeader(HttpHeaders.Names.UPGRADE, HttpHeaders.Values.WEBSOCKET);
			response.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.UPGRADE);
			
			// Origin (Just copy from request -> response)
			String origin=request.getHeader(HttpHeaders.Names.ORIGIN);
			if (origin!=null) response.addHeader(HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN, origin);
			response.addHeader(HttpHeaders.Names.SEC_WEBSOCKET_LOCATION, selectLocation(request));
			
			// Keys
			String key1=request.getHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY1);
			String key2=request.getHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY2);
			if (key1==null || key2==null) return false;
			int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
			int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
			long c = request.getContent().readLong();
			
			if (logger.isDebugEnabled()) {
				logger.debug("WebSocket keys: key1={" + key1 + "}, key2={" + key2 + "}, a=" + a + ", b=" + b + ", c=" + c);
			}
			
			ChannelBuffer input=ChannelBuffers.buffer(16);
			input.writeInt(a);
			input.writeInt(b);
			input.writeLong(c);
			responseTrailer=ChannelBuffers.wrappedBuffer(
					MessageDigest.getInstance("MD5").digest(input.array()));
			response.setContent(responseTrailer);
		}
		
		// Write the response before modifying the pipeline
		context.getChannel().write(response);
		
		// Re-rig the pipeline
		// Clear the pipeline of handlers
		ChannelPipeline pipeline=context.getPipeline();
		while (pipeline.getLast()!=null) pipeline.removeLast();
		
		// Add encoder/decoder
		pipeline.addLast("wsdecoder", new WebSocketFrameDecoder(maxFrameSize()));
		pipeline.addLast("wsencoder", new WebSocketFrameEncoder());
		
		// Add standard logic
		configureWebSocketPipeline(context.getPipeline());

		// Log
		logger.debug("Upgraded websocket request");
		HttpServer.Instance.get(context.getChannel()).logAccess(remoteAddress, request, response);

		// Synthesize a connect event
		Channels.fireChannelConnected(context.getChannel(), remoteAddress);
		
		// Make sure to capture any excess bits to pump back through later 
		remainder=request.getContent().slice();
		if (remainder.readable()) {
			Channels.fireMessageReceived(context.getChannel(), remainder);
		}
		
		return true;
	}

	/**
	 * After upgrade and after the websocket frame decoder/encoder have been
	 * added, add application logic to the pipeline.  The default implementation
	 * does nothing.
	 * @param context
	 */
	protected void configureWebSocketPipeline(ChannelPipeline pipeline) {
		pipeline.addLast("maint", new StandardWebSocketChannelHandler());
	}

	protected int maxFrameSize() {
		return DEFAULT_MAX_FRAME_SIZE;
	}

	/**
	 * Return an appropriate WebSocket-Location header value for the request
	 * @param request
	 * @return
	 */
	protected CharSequence selectLocation(HttpRequest request) {
		// TODO: Need to detect secure vs not secure
		return "ws://" + request.getHeader(HttpHeaders.Names.HOST) + request.getUri();
	}

	/**
	 * Takes incoming protocol request and returns the protocol for the response
	 * @param protocol
	 * @return protocol selected
	 */
	protected String selectProtocol(String protocol) {
		return protocol;
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
		
		HttpServer.Instance.get(context.getChannel()).logAccess(remoteAddress, request, response);
		return Channels.succeededFuture(context.getChannel());
	}

}
