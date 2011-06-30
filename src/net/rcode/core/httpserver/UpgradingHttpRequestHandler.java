package net.rcode.core.httpserver;

/**
 * Request handler that conditionally may upgrade the connection
 * @author stella
 *
 */
public interface UpgradingHttpRequestHandler extends HttpRequestHandler {
	/**
	 * This method will be called just after begin(...).  If true, then all
	 * standard http processing will terminate.  It is up to the handler to
	 * adjust the pipeline during the begin method.  This check is just
	 * here so that the ChannelHandler knows to stop any further work. 
	 * @return true if upgraded
	 */
	public boolean didUpgrade();
}
