package net.rcode.core.http;

import net.rcode.core.async.Promise;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Sent to a filter chain that contains BufferedHttpFilter to initiate a new HTTP interaction.
 * Can have a Promise passed in that will be resolved upon request completion.
 * 
 * @author stella
 *
 */
public class AsyncHttpRequest {
	private HttpRequest requestPacket;
	private Promise<AsyncHttpResponse> responsePromise;
	private boolean persistConnection;
	
	public AsyncHttpRequest(HttpRequest requestPacket, Promise<AsyncHttpResponse> responsePromise) {
		this.requestPacket=requestPacket;
		if (responsePromise!=null) { 
			this.responsePromise=responsePromise;
		} else {
			this.responsePromise=new Promise<AsyncHttpResponse>();
		}
	}
	
	public AsyncHttpRequest(HttpRequest requestPacket) {
		this(requestPacket, null);
	}
	
	public boolean isPersistConnection() {
		return persistConnection;
	}
	
	public void setPersistConnection(boolean persistConnection) {
		this.persistConnection = persistConnection;
	}
	
	public HttpRequest getRequestPacket() {
		return requestPacket;
	}
	
	public Promise<AsyncHttpResponse> getResponse() {
		return responsePromise;
	}
	
	@Override
	public String toString() {
		return String.format("AsyncHttpRequest(packet=%s)", requestPacket);
	}
}
