package net.rcode.core.httpserver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.rcode.core.async.Promise;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard http request handler that buffers its request data,
 * handles connection persistence, handles logging and dispatch of responses.
 * <p>
 * For anything non-standard, you should implement HttpRequestHandler yourself
 * 
 * @author stella
 *
 */
public abstract class DefaultHttpRequestHandler implements HttpRequestHandler, Cloneable, HttpState {
	protected final Logger logger=LoggerFactory.getLogger(getClass());
	public static final int MAX_REQUEST_SIZE=4 * 1024 * 1024;	// 4MB
	protected Channel channel;
	protected HttpRequest request;
	protected HttpChunkTrailer trailer;
	protected ChannelHandlerContext context;
	protected SocketAddress remoteAddress;
	protected ChannelBuffer requestData;
	protected ChannelFuture resumePipelineFuture;
	
	// -- Internal state
	private Set<Cookie> decodedCookies;
	private List<Cookie> responseCookies;
	
	@SuppressWarnings("unchecked")
	public Promise.Callback<Object> respondErrorCallback() {
		return new Promise.Callback() {
			@Override
			public void complete(Promise promise) {
				if (promise.isError()) {
					Throwable t=promise.getError();
					logger.error("Error in asynchronous http interaction", t);
					respondError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error in asynchrnonous execution", t);
				}
			}
		};
	}
	
	@Override
	public HttpRequestHandler clone() {
		try {
			return (HttpRequestHandler) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new Error(e);
		}
	}
	
	/**
	 * Main entry point for handling the request.  Must terminate with a call to
	 * one of the respond*() functions
	 * @throws Exception
	 */
	protected abstract void handle() throws Exception;
	
	@Override
	public HttpRequest getRequest() {
		return request;
	}
	
	@Override
	public Channel getChannel() {
		return channel;
	}
	
	@Override
	public HttpServer getServer() {
		return HttpServer.Instance.get(channel);
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#getCookie(java.lang.String)
	 */
	@Override
	public Cookie getCookie(String name) {
		for (Cookie c: getCookies()) {
			if (name.equals(c.getName())) return c;
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#getCookieValue(java.lang.String)
	 */
	@Override
	public String getCookieValue(String name) {
		Cookie c=getCookie(name);
		if (c!=null) return c.getValue();
		else return null;
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#getCookies()
	 */
	@Override
	public Set<Cookie> getCookies() {
		if (decodedCookies==null) {
			String value=request.getHeader(HttpHeaders.Names.COOKIE);
			if (value==null) {
				decodedCookies=Collections.emptySet();
			} else {
				CookieDecoder d=new CookieDecoder();
				decodedCookies=d.decode(value);
			}
		}
		return decodedCookies;
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#addCookie(org.jboss.netty.handler.codec.http.Cookie)
	 */
	@Override
	public void addCookie(Cookie c) {
		if (responseCookies==null) {
			responseCookies=new ArrayList<Cookie>();
		}
		responseCookies.add(c);
	}
	
	/**
	 * Called when a response object is created and ready to be returned.  Default
	 * implementation adds cookies
	 * @param response
	 */
	protected void commitResponse(HttpResponse response) {
		if (responseCookies!=null && !response.containsHeader(HttpHeaders.Names.SET_COOKIE)) {
			CookieEncoder enc=new CookieEncoder(true);
			for (Cookie c: responseCookies) {
				enc.addCookie(c);
			}
			response.addHeader(HttpHeaders.Names.SET_COOKIE, enc.encode());
		}
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#respond(org.jboss.netty.handler.codec.http.HttpResponse)
	 */
	@Override
	public void respond(HttpResponse response) {
		boolean content=expectsContent(response);
		if (!content) {
			// No content on these
			response.removeHeader(HttpHeaders.Names.CONTENT_LENGTH);
			response.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
			response.setContent(null);
		} else {
			if (!response.containsHeader(HttpHeaders.Names.CONTENT_LENGTH)) {
				HttpHeaders.setContentLength(response, response.getContent().readableBytes());
			}
		}
		
		commitResponse(response);
		
		boolean persistConnection=HttpHeaders.isKeepAlive(response) &&
			HttpHeaders.isKeepAlive(request);
		ChannelFuture writeComplete=context.getChannel().write(response);
		resumePipelineFuture.setSuccess();
		if (!persistConnection) {
			writeComplete.addListener(ChannelFutureListener.CLOSE);
		}
		
		HttpServer.Instance.get(context.getChannel()).logAccess(remoteAddress, request, response);
	}
	
	public boolean expectsContent(HttpResponse response) {
		HttpResponseStatus status=response.getStatus();
		return !(request.getMethod()==HttpMethod.HEAD || 
				status==HttpResponseStatus.NO_CONTENT ||
				status==HttpResponseStatus.NOT_MODIFIED ||
				(status.getCode()>=100 && status.getCode()<=199));
	}
	
	private void bufferContent(ChannelBuffer content) throws TooLongFrameException {
		int estimatedLength=(int) HttpHeaders.getContentLength(request);
		if (estimatedLength<1000) estimatedLength=1000;
		if (estimatedLength>MAX_REQUEST_SIZE) {
			throw new TooLongFrameException();
		}
		
		if (requestData==null) requestData=new DynamicChannelBuffer(estimatedLength);
		if ((content.readableBytes() + requestData.readableBytes()) > MAX_REQUEST_SIZE) {
			throw new TooLongFrameException();
		}
		
		requestData.writeBytes(content);
	}
	
	@Override
	public final void begin(ChannelHandlerContext context, HttpRequest request,
			SocketAddress remote) throws TooLongFrameException {
		this.channel=context.getChannel();
		this.request=request;
		this.remoteAddress=remote;
		
		ChannelBuffer content=request.getContent();
		if (content!=null && content.readable()) {
			bufferContent(content);
			request.setContent(null);
		}
	}

	@Override
	public void data(ChannelHandlerContext context, ChannelBuffer content) throws TooLongFrameException {
		if (content.readable()) bufferContent(content);
	}

	@Override
	public void trailers(ChannelHandlerContext context, HttpChunkTrailer trailer) {
		this.trailer=trailer;
	}

	@Override
	public ChannelFuture finish(ChannelHandlerContext context) throws Exception {
		resumePipelineFuture=Channels.future(context.getChannel());
		this.context=context;
		handle();
		return resumePipelineFuture;
	}

	@Override
	public void respondError(HttpResponseStatus status, String text, Throwable t) {
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,
				status);
		
		commitResponse(response);

		StringWriter message=new StringWriter();
		message.append(text);
		message.append("\n");
		
		if (t!=null) {
			PrintWriter pout=new PrintWriter(message);
			t.printStackTrace(pout);
			pout.flush();
		}
		
		response.setContent(ChannelBuffers.copiedBuffer(message.getBuffer(), CharsetUtil.UTF_8));
		respond(response);
	}
	
	@Override
	public void respondError(HttpResponseStatus status, String text) {
		respondError(status, text, null);
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#respondError(int, java.lang.String)
	 */
	@Override
	public void respondError(int statusCode, String text) {
		respondError(statusCode, text, null);
	}

	/* (non-Javadoc)
	 * @see net.rcode.mrsession.util.io.httpserver.HttpState#respondError(int, java.lang.String, java.lang.Throwable)
	 */
	@Override
	public void respondError(int statusCode, String text, Throwable t) {
		respondError(HttpResponseStatus.valueOf(statusCode), text, t);
	}

}
