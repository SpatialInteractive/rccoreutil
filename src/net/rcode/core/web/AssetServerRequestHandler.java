package net.rcode.core.web;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import net.rcode.assetserver.core.AssetLocator;
import net.rcode.assetserver.embedded.AssetServerEmbedded;

/**
 * Manages an embedded asset server and farms requests off to it
 * @author stella
 *
 */
public class AssetServerRequestHandler extends ThreadedRequestHandler {
	private AssetServerEmbedded assetServer;
	
	public AssetServerRequestHandler(Executor executor, File location) throws Exception {
		super(executor);
		assetServer=new AssetServerEmbedded(location);
	}

	public AssetServerEmbedded getAssetServer() {
		return assetServer;
	}
	
	public void setGlobalDisableOptimization(boolean b) {
		assetServer.getServer().setGlobalDisableOptimization(b);
	}
	
	@Override
	protected void handleInThread() throws Exception {
		AssetLocator locator=assetServer.resolve(request.getUri());
		if (locator==null) {
			respondError(404, "Not found");
			return;
		}
		
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		String etag=locator.getETag();
		
		// Does etag match?
		if (etag!=null) {
			String ifNoneMatch=request.getHeader(HttpHeaders.Names.IF_NONE_MATCH);
			if (ifNoneMatch!=null && etag.equals(ifNoneMatch)) {
				HttpResponse notModifiedResponse=new DefaultHttpResponse(HttpVersion.HTTP_1_1, 
						HttpResponseStatus.NOT_MODIFIED);
				respond(notModifiedResponse);
				return;
			} else {
				response.addHeader(HttpHeaders.Names.ETAG, etag);
			}
		}
		
		String contentType=locator.getContentType(), charset=locator.getCharacterEncoding();
		if (contentType!=null) {
			if (charset!=null) contentType+=";charset=" + charset;
			response.addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
		}

		// Respond
		if (request.getMethod()!=HttpMethod.HEAD) {
			ChannelBuffer content=readBuffer(locator);
			HttpHeaders.setContentLength(response, content.readableBytes());
			response.setContent(content);
		} else {
			HttpHeaders.setContentLength(response, locator.length());
		}
		
		respond(response);
	}

	private ChannelBuffer readBuffer(AssetLocator locator) throws IOException {
		ChannelBuffer buffer=ChannelBuffers.dynamicBuffer(locator.length()>0 && locator.length()<Integer.MAX_VALUE ? (int)locator.length() : 8192);
		ChannelBufferOutputStream out=new ChannelBufferOutputStream(buffer);
		locator.writeTo(out);
		return buffer;
	}

}
