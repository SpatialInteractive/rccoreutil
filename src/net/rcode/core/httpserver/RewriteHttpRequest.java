package net.rcode.core.httpserver;

import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * Wrap an HttpRequest to support rewriting of the uri
 * without overwriting the original.
 * @author stella
 *
 */
public class RewriteHttpRequest implements HttpRequest {
	private HttpRequest wrapped;
	private String uri;
	
	public RewriteHttpRequest(HttpRequest wrapped, String newUri) {
		this.wrapped=wrapped;
		this.uri=newUri;
	}
	
	public HttpRequest getRoot() {
		if (wrapped instanceof RewriteHttpRequest) return ((RewriteHttpRequest)wrapped).getRoot();
		else return wrapped;
	}
	
	@Override
	public String toString() {
		return wrapped.toString();
	}
	
	@Override
	public String getUri() {
		return uri;
	}
	
	@Override
	public void setUri(String uri) {
		this.uri=uri;
	}
	
	public HttpMethod getMethod() {
		return wrapped.getMethod();
	}

	public void setMethod(HttpMethod method) {
		wrapped.setMethod(method);
	}

	public void addHeader(String name, Object value) {
		wrapped.addHeader(name, value);
	}

	public void clearHeaders() {
		wrapped.clearHeaders();
	}

	public boolean containsHeader(String name) {
		return wrapped.containsHeader(name);
	}

	public ChannelBuffer getContent() {
		return wrapped.getContent();
	}

	public long getContentLength() {
		return wrapped.getContentLength();
	}

	public long getContentLength(long defaultValue) {
		return wrapped.getContentLength(defaultValue);
	}

	public String getHeader(String name) {
		return wrapped.getHeader(name);
	}

	public Set<String> getHeaderNames() {
		return wrapped.getHeaderNames();
	}

	public List<Entry<String, String>> getHeaders() {
		return wrapped.getHeaders();
	}

	public List<String> getHeaders(String name) {
		return wrapped.getHeaders(name);
	}

	public HttpVersion getProtocolVersion() {
		return wrapped.getProtocolVersion();
	}

	public boolean isChunked() {
		return wrapped.isChunked();
	}

	public boolean isKeepAlive() {
		return wrapped.isKeepAlive();
	}

	public void removeHeader(String name) {
		wrapped.removeHeader(name);
	}

	public void setChunked(boolean chunked) {
		wrapped.setChunked(chunked);
	}

	public void setContent(ChannelBuffer content) {
		wrapped.setContent(content);
	}

	public void setHeader(String name, Object value) {
		wrapped.setHeader(name, value);
	}

	public void setHeader(String name, Iterable<?> values) {
		wrapped.setHeader(name, values);
	}

	public void setProtocolVersion(HttpVersion version) {
		wrapped.setProtocolVersion(version);
	}	
}
