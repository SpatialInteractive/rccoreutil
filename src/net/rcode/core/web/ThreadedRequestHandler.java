package net.rcode.core.web;

import java.util.concurrent.Executor;

import net.rcode.core.httpserver.DefaultHttpRequestHandler;

/**
 * RequestHandler that does its work by submitting jobs to an
 * Executor, freeing io threads for non-blocking work.
 * 
 * @author stella
 *
 */
public abstract class ThreadedRequestHandler extends DefaultHttpRequestHandler implements Runnable {
	private Executor executor;
	
	public ThreadedRequestHandler(Executor executor) {
		this.executor=executor;
	}
	
	public Executor getExecutor() {
		return executor;
	}
	
	/**
	 * Override this to handle the request
	 * @throws Exception
	 */
	protected abstract void handleInThread() throws Exception;
	
	@Override
	protected void handle() throws Exception {
		executor.execute(this);
	}

	@Override
	public final void run() {
		try {
			handleInThread();
		} catch (Throwable t) {
			logger.error("Exception processing request " + request.toString(), t);
			respondError(500, "Exception processing request", t);
		}
	}
}
