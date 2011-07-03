package net.rcode.core.httpserver;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer implements ChannelPipelineFactory {
	private static final SimpleDateFormat DATE_FMT_PROTOTYPE=new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");
	private static final Logger logger=LoggerFactory.getLogger(HttpServer.class);
	public static final ChannelLocal<HttpServer> Instance=new ChannelLocal<HttpServer>();
	public static final String HEADER_ORIGINAL_URI="X-Original-Uri";
	
	private Logger accessLogger=logger;
	private List<HttpRequestDispatcher> dispatchers=new ArrayList<HttpRequestDispatcher>();
	private Executor webWorkerExecutor;
	
	public HttpServer(Executor webWorkerExecutor) {
		this.webWorkerExecutor=webWorkerExecutor;
	}
	
	public Executor getWebWorkerExecutor() {
		return webWorkerExecutor;
	}
	
	public static void changeRequestUri(HttpRequest request, String uri) {
		if (!request.containsHeader(HEADER_ORIGINAL_URI))
			request.setHeader(HEADER_ORIGINAL_URI, request.getUri());
		request.setUri(uri);
	}
	
	public List<HttpRequestDispatcher> getDispatchers() {
		return dispatchers;
	}
	
	public Logger getAccessLogger() {
		return accessLogger;
	}
	public void setAccessLogger(Logger accessLogger) {
		this.accessLogger = accessLogger;
	}
	
	/**
	 * Write to the access log
	 * @param remote
	 * @param request
	 * @param response
	 */
	public void logAccess(SocketAddress remote, HttpRequest request, HttpResponse response) {
		if (request instanceof RewriteHttpRequest) {
			request=((RewriteHttpRequest)request).getRoot();
		}
		
		StringBuilder b=new StringBuilder(256);
		// %h remote host
		if (remote!=null) {
			try {
				InetSocketAddress isa=(InetSocketAddress) remote;
				b.append(isa.getAddress().getHostAddress());
				b.append(':');
				b.append(isa.getPort());
			} catch (ClassCastException e) {
				b.append('-');
			}
		} else {
			b.append('-');
		}
		
		// identd
		b.append(" -");
		
		// user
		b.append(" -");
		
		// %t time
		SimpleDateFormat fmt;
		fmt=(SimpleDateFormat) DATE_FMT_PROTOTYPE.clone();
		b.append(" [");
		b.append(fmt.format(new Date()));
		b.append(']');
		
		// Request line
		if (request!=null) {
			b.append(" \"");
			b.append(request.getMethod().toString());
			b.append(' ');
			String uri=request.getHeader(HEADER_ORIGINAL_URI);
			if (uri==null) uri=request.getUri();
			b.append(uri);
			b.append(' ');
			b.append(request.getProtocolVersion().toString());
			b.append('"');
		} else {
			b.append(" -");
		}
		
		// Response code
		if (response!=null) {
			b.append(' ');
			b.append(response.getStatus().getCode());
			b.append(' ');
			long len=HttpHeaders.getContentLength(response, -1);
			if (len>=0) b.append(len);
			else b.append('-');
		} else {
			b.append(" - -");
		}
		
		// Referer
		String referer=null, userAgent=null;
		if (request!=null) {
			referer=request.getHeader(HttpHeaders.Names.REFERER);
			userAgent=request.getHeader(HttpHeaders.Names.USER_AGENT);
		}
		b.append(' ');
		if (referer!=null) {
			b.append('"');
			b.append(referer);
			b.append('"');		
		} else {
			b.append('-');
		}
		b.append(' ');
		if (userAgent!=null) {
			b.append('"');
			b.append(userAgent);
			b.append('"');
		} else {
			b.append('-');
		}
		accessLogger.info(b.toString());
	}
	
	/**
	 * Handler added to the front of the pipeline which does server setup
	 * and teardown maintenance.
	 */
	private ChannelHandler serverMaintenance=new SimpleChannelUpstreamHandler() {
		@Override
		public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e)
				throws Exception {
			Instance.set(ctx.getChannel(), HttpServer.this);
			ctx.sendUpstream(e);
		}
		
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
			logger.error("Exception caught processing http request (closing channel)", e.getCause());
			ctx.getChannel().close();
		}
		
	};
	
	@Override
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline=pipeline();
		pipeline.addLast("maint", serverMaintenance);
		if (DebugChannelHandler.INSTANCE.isEnabled()) {
			pipeline.addLast("debug", DebugChannelHandler.INSTANCE);
		}
		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("encoder", new HttpResponseEncoder());
		
		pipeline.addLast("inflater", new HttpContentDecompressor());
		pipeline.addLast("deflator", new ConditionalHttpContentCompressor());
		
		pipeline.addLast("handler", new HttpServerChannelHandler(dispatchers));
		return pipeline;
	}
	
	/**
	 * Remove compression related handlers from the pipeline
	 * @param pipeline
	 */
	public static void pipelineDisableCompression(ChannelPipeline pipeline) {
		pipeline.remove("deflator");
	}
}
