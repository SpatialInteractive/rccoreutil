package net.rcode.core.httpserver;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Selects an appropriate HttpRequestHandler for a request or performs other
 * activities.
 * @author stella
 *
 */
public interface HttpRequestDispatcher {
	/**
	 * Select a handler or perform other work
	 * @param request
	 * @return null if no handler, non-null if a handler should be assigned
	 * @throws Exception 
	 */
	public HttpDispatch dispatch(HttpRequest request) throws Exception;
}
