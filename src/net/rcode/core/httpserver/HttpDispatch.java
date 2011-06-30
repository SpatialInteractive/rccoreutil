package net.rcode.core.httpserver;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * State to be used when traversing dispatchers
 * 
 * @author stella
 *
 */
public class HttpDispatch {
	public HttpRequest request;
	public HttpRequestHandler handler;
	
	public HttpDispatch(HttpRequest request, HttpRequestHandler handler) {
		this.request=request;
		this.handler=handler;
	}
}
