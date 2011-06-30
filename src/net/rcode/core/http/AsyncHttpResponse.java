package net.rcode.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Holds a fully buffered HTTP response.
 * @author stella
 *
 */
public class AsyncHttpResponse {
	public long runtime;
	public HttpResponse header;
	public ChannelBuffer content;
	
	
	@Override
	public String toString() {
		return "AsyncHttpResponse";
		//return String.format("BufferedHttpResponse(length=%s, header=%s)", dataBuffer.length(), header);
	}
}
