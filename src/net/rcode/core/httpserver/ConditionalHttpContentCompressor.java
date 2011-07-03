package net.rcode.core.httpserver;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;

/**
 * Applies conditional logic to the HttpContentCompressor.  The API is not really great
 * for this, so this class liberally works around it by overriding methods in both
 * HttpContentCompressor and HttpContentEncoder and using instance variables to
 * communicate.  Be warned.
 * 
 * @author stella
 *
 */
public class ConditionalHttpContentCompressor extends HttpContentCompressor {
	private boolean shouldCompress;
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		if (e instanceof HttpMessage) {
			HttpMessage msg=(HttpMessage) e;

			// Every path in the below must result in setitng shouldCompress
			shouldCompress=checkShouldCompress(msg);
		}
		
		super.writeRequested(ctx, e);
	}

	private boolean checkShouldCompress(HttpMessage msg) {
		String existingContentEncoding=msg.getHeader(HttpHeaders.Names.CONTENT_ENCODING);
		if (HttpHeaders.Values.IDENTITY.equals(existingContentEncoding)) {
			// Per response override if explicitly set.
			return false;
		} else {
			// Apply heuristic based on mime type
			String contentType=msg.getHeader(HttpHeaders.Names.CONTENT_TYPE);
			if (contentType!=null && isCompressibleMimeType(contentType)) {
				return true;
			} else {
				return false;
			}
		}
	}
	
	private boolean isCompressibleMimeType(String contentType) {
		if (contentType.startsWith("text/")) return true;
		if (contentType.startsWith("application/json")) return true;
		return false;
	}

	@Override
	protected EncoderEmbedder<ChannelBuffer> newContentEncoder(
			String acceptEncoding) throws Exception {
		if (!shouldCompress) return null;
		
		return super.newContentEncoder(acceptEncoding);
	}
	
	@Override
	protected String getTargetContentEncoding(String acceptEncoding)
			throws Exception {
		if (!shouldCompress) return null;

		return super.getTargetContentEncoding(acceptEncoding);
	}
}
