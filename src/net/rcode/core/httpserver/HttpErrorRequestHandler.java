package net.rcode.core.httpserver;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

/**
 * Send an error report
 * @author stella
 *
 */
public class HttpErrorRequestHandler extends DefaultHttpRequestHandler {
	private HttpResponseStatus status;
	private String message;

	public HttpErrorRequestHandler(HttpResponseStatus status, String message) {
		this.status=status;
		this.message=message;
	}

	@Override
	protected void handle() throws Exception {
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
		if (message!=null) {
			response.setContent(ChannelBuffers.copiedBuffer(message, CharsetUtil.UTF_8));
		}
		respond(response);
	}
}
