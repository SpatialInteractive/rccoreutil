package net.rcode.core.httpserver;

import java.util.Set;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public interface HttpState {

	/**
	 * Get the channel associated with the transaction
	 * @return
	 */
	public abstract Channel getChannel();
	
	/**
	 * Get the server associated with this transaction
	 * @return
	 */
	public abstract HttpServer getServer();
	
	/**
	 * @return the http request object
	 */
	public abstract HttpRequest getRequest();
	
	/**
	 * Lookup the specific cookie
	 * @param name
	 * @return cookie or null
	 */
	public abstract Cookie getCookie(String name);

	/**
	 * Get a request cookie value as a string
	 * @param name
	 * @return value or null
	 */
	public abstract String getCookieValue(String name);

	/**
	 * Get the set of request cookies, decoding if necessary
	 * @return Set of Cookies
	 */
	public abstract Set<Cookie> getCookies();

	/**
	 * Add a cookie to the response
	 * @param c
	 */
	public abstract void addCookie(Cookie c);

	/**
	 * When the response contains embedded content, respond in one
	 * step.  This will set the Content-Length header appropriately,
	 * send the response and release the pipeline.
	 * @param response
	 */
	public abstract void respond(HttpResponse response);

	public abstract void respondError(int statusCode, String text);

	public abstract void respondError(int statusCode, String text, Throwable t);

	void respondError(HttpResponseStatus status, String text, Throwable t);

	void respondError(HttpResponseStatus status, String text);

}