package net.rcode.core.async;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core coordination mechanism for async programming.  Synonymous with Future
 * but the java.concurrent Future is pretty lame so I chose a different name
 * for a divergent implementation.
 * 
 * @author stella
 *
 */
public class Promise<T> {
	private static final Logger logger=LoggerFactory.getLogger(Promise.class);
	
	public static interface Callback<T> {
		public void complete(Promise<T> promise);
	}
	
	public static interface Chain<FROM,TO> {
		public TO chain(FROM input) throws Throwable;
	}
	
	public static interface AsyncChain<FROM,TO> {
		public void chain(FROM input, Promise<TO> result) throws Throwable;
	}
	
	public static abstract class Action implements Callback<Void> {
		public abstract void action() throws Throwable;
		public final void complete(Promise<Void> r) {
			try {
				action();
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}
	}
	
	public static abstract class Handler<T> implements Chain<T,Void> {
		public final Void chain(T input) throws Throwable {
			handle(input);
			return null;
		}
		public abstract void handle(T input) throws Throwable;
	}
	
	public static interface Initiator<T> {
		public Promise<T> initiate() throws Throwable;
	}
	
	public static final int STATE_UNRESOLVED=0;
	public static final int STATE_RESOLVED=1;
	public static final int STATE_ERROR=2;
	
	public static <T> Promise<T> fixed(T value) {
		return new Promise<T>(value);
	}

	public static <T> Promise<T> none() {
		return new Promise<T>(null);
	}
	
	// -- instance members
	Scope scope;
	int state;
	Object resolution;
	ArrayList<Callback<? super T>> callbacks=new ArrayList<Callback<? super T>>(1);
	StackTraceElement[] origin;
	
	public Promise() {
		this.scope=Scope.capture();
		if (AsyncTrace.ENABLED) {
			try {
				throw new Throwable();
			} catch (Throwable t) {
				t.fillInStackTrace();
				origin=t.getStackTrace();
			}
		}
	}
	
	private Promise(T value) {
		this();
		this.state=STATE_RESOLVED;
		this.resolution=value;
	}
	
	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		sb.append("Promise(").append(state).append(")");
		if (origin!=null) {
			sb.append(":\n");
			for (StackTraceElement st: origin) {
				sb.append("\t\t").append(st.getClassName())
					.append(":").append(st.getLineNumber())
					.append('\n');
			}
		}
		return sb.toString();
	}
	
	/**
	 * Forward the result of this promise to another
	 * @param other
	 */
	public void forward(final Promise<T> other) {
		Callback<T> cb=new Callback<T>() {
			@Override
			public void complete(Promise<T> promise) {
				if (promise.isError()) other.resolveError(promise.getError());
				else other.resolve(promise.getResolution());
			}
		};
		
		force(cb);
	}
	
	public <TO> Promise<TO> chain(final Chain<T,TO> chain) {
		final Promise<TO> result=new Promise<TO>();
		force(new Callback<T>() {
			@Override
			public void complete(Promise<T> source) {
				if (source.isError()) {
					result.resolveError(source.getError());
					return;
				} else {
					TO value;
					try {
						value=chain.chain(source.getResolution());
					} catch (Throwable t) {
						result.resolveError(t);
						return;
					}
					result.resolve(value);
				}
			}
		});
		return result;
	}
	
	public <TO> Promise<TO> chain(final AsyncChain<T,TO> chain) {
		final Promise<TO> result=new Promise<TO>();
		
		force(new Callback<T>() {
			@Override
			public void complete(Promise<T> source) {
				if (source.isError()) {
					result.resolveError(source.getError());
					return;
				} else {
					try {
						chain.chain(source.getResolution(), result);
					} catch (Throwable t) {
						if (!result.isComplete()) {
							result.resolveError(t);
						} else {
							throw new RuntimeException(t);
						}
					}
				}
			}
		});
		
		return result;
	}
	
	public synchronized boolean isComplete() {
		return state>STATE_UNRESOLVED;
	}
	
	public synchronized boolean isResolved() {
		return state==STATE_RESOLVED;
	}
	
	public synchronized boolean isError() {
		return state==STATE_ERROR;
	}
	
	public synchronized int getState() {
		return state;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized T getResolution() {
		if (state==STATE_RESOLVED) return (T)resolution;
		else return null;
	}
	
	public synchronized Throwable getError() {
		if (state==STATE_ERROR) return (Throwable)resolution;
		else return null;
	}
	
	public void force(Callback<? super T> cb) {
		int localState;
		synchronized(this) {
			localState=state;
			if (localState==STATE_UNRESOLVED) {
				if (AsyncTrace.ENABLED) {
					if (callbacks.isEmpty()) {
						AsyncTrace.INSTANCE.addBrokenPromise(this);
					}
				}
				callbacks.add(cb);
				return;
			}
		}
		
		// Already resolved
		cb.complete((Promise)this);
	}
	
	public synchronized T forceImmediate() {
		if (state==STATE_RESOLVED) return (T)resolution;
		else if (state==STATE_ERROR) {
			if (resolution instanceof RuntimeException) throw (RuntimeException)resolution;
			else if (resolution instanceof Error) throw (Error)resolution;
			else throw new RuntimeException((Throwable)resolution);
		} else {
			throw new IllegalStateException();
		}
	}
	
	public T waitFor(long timeout) throws InterruptedException {
		WaitAction<T> wa=new WaitAction<T>();
		force(wa);
		return wa.waitFor(timeout);
	}

	public T waitFor() throws InterruptedException {
		WaitAction<T> wa=new WaitAction<T>();
		force(wa);
		return wa.waitFor();
	}

	private static class WaitAction<T> implements Callback<T> {
		private Promise<T> promise;

		@Override
		public void complete(Promise<T> promise) {
			synchronized (this) {
				this.promise=promise;
				this.notify();
			}
		}
		
		public T waitFor(long timeout) throws InterruptedException {
			synchronized (this) {
				if (promise==null) this.wait(timeout);
				return promise.forceImmediate();
			}
		}
		
		public T waitFor() throws InterruptedException {
			synchronized (this) {
				for (;;) {
					if (promise!=null) return promise.forceImmediate();
					this.wait();
				}
			}
		}
	}
	
	public void resolve(final T resolution) {
		final ArrayList<Callback<? super T>> localCallbacks;
		synchronized (this) {
			if (state!=STATE_UNRESOLVED) {
				throw new IllegalStateException("Duplicate promise resolution: " + resolution);
			}
			
			this.resolution=resolution;
			this.state=STATE_RESOLVED;
			localCallbacks=this.callbacks;
			this.callbacks=null;
		}
		
		// Invoke callbacks
		if (!localCallbacks.isEmpty()) {
			if (AsyncTrace.ENABLED) {
				AsyncTrace.INSTANCE.removeBrokenPromise(this);
			}
			Scope.invoke(scope, new Scope.Step() {
				@Override
				public void step() throws Exception {
					for (int i=0; i<localCallbacks.size(); i++) {
						Callback<? super T> cb=localCallbacks.get(i);
						cb.complete((Promise)Promise.this);
					}
				}
			});
		}
	}
	
	public void resolveError(final Throwable t) {
		final ArrayList<Callback<? super T>> localCallbacks;
		synchronized (this) {
			if (state==STATE_ERROR) {
				logger.warn("Duplicate error resolution. First exception wins. Subsequent exception follows.", t);
				return;
			} else if (state!=STATE_UNRESOLVED) {
				throw new IllegalStateException("Duplicate promise resolution with error", t);
			}
			
			this.resolution=t;
			this.state=STATE_ERROR;
			localCallbacks=this.callbacks;
			this.callbacks=null;
		}
		
		logger.warn("Resolving promise to error", t);
		
		// Invoke callbacks
		if (!localCallbacks.isEmpty()) {
			if (AsyncTrace.ENABLED) {
				AsyncTrace.INSTANCE.removeBrokenPromise(this);
			}
			
			Scope.invoke(scope, new Scope.Step() {
				@Override
				public void step() throws Exception {
					for (int i=0; i<localCallbacks.size(); i++) {
						Callback<? super T> cb=localCallbacks.get(i);
						cb.complete((Promise)Promise.this);
					}
				}
			});
		}
	}
}
