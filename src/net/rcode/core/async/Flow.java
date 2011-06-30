package net.rcode.core.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An unbounded flow that ends with a known result type
 * @author stella
 *
 * @param <T>
 */
public class Flow<T> extends Promise<T> {
	private static final Logger logger=LoggerFactory.getLogger(Flow.class);
	
	public static int ONERROR_RESOLVE=0;
	public static int ONERROR_CONTINUE=1;
	
	String name;
	int sequence;
	int onError=ONERROR_RESOLVE;
	boolean stopped;
	Object current;
	Throwable error;
	Invoker ondeck;
	Promise<?> blocker;
	Promise<?> waitingOn;
	LinkedList<Invoker> pending=new LinkedList<Invoker>();
	Promise.Callback callback=new Promise.Callback() {
		@Override
		public void complete(Promise promise) {
			handleComplete(promise);
		}
	};
	
	final void handleComplete(Promise promise) {
		synchronized (this) {
			if (waitingOn!=null && waitingOn!=promise) {
				logger.error("Internal consistency error: Flow callback for promise not waiting on");
				return;
			}
			
			try {
				current=promise.forceImmediate();
			} catch (Throwable t) {
				logger.error("Asynchronous flow step resulted in error", t);
				error=t;
			}
			
			blocker=null;
			waitingOn=null;
		}
		
		schedule();
	}
	
	synchronized final Object consumeCurrent() {
		Object ret=current;
		current=null;
		return ret;
	}
	
	synchronized final void setNext(Object next) {
		current=next;
	}
	
	synchronized final void suspendForNext(Invoker suspender, Promise<?> blocker) {
		this.blocker=blocker;
	}
	
	synchronized final void handleError(Invoker source, Throwable t) {
		logger.error("Invoker " + source + " raised error on " + this, t);
		error=t;
	}
	
	public static interface Invoker {
		public void invoke(Flow<?> flow);
	}
	
	public abstract static class AsyncTransform<P,N> implements Invoker {
		@Override
		@SuppressWarnings("unchecked")
		public final void invoke(Flow<?> flow) {
			try {
				P previous=(P) flow.consumeCurrent();
				Promise<N> next=run(previous);
				if (next!=null) flow.suspendForNext(this, next);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
		
		public abstract Promise<N> run(P previous) throws Throwable;
	}
	
	public abstract static class Transform<P,N> implements Invoker {
		@Override
		@SuppressWarnings("unchecked")
		public final void invoke(Flow<?> flow) {
			try {
				P previous=(P) flow.consumeCurrent();
				N next=run(previous);
				flow.setNext(next);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
		
		public abstract N run(P previous) throws Throwable;
	}
	
	public abstract static class Initiator<N> implements Invoker {
		@Override
		public final void invoke(Flow<?> flow) {
			try {
				flow.consumeCurrent();
				Promise<N> next=run();
				flow.suspendForNext(this, next);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
		public abstract Promise<N> run() throws Throwable;
	}
	
	public abstract static class Consumer<P> implements Invoker {
		@Override
		@SuppressWarnings("unchecked")
		public final void invoke(Flow<?> flow) {
			try {
				P previous=(P) flow.consumeCurrent();
				run(previous);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
		public abstract void run(P previous) throws Throwable;
	}
	
	public static class ChainInvoker<P,N> implements Invoker {
		private Chain<P,N> chain;
		public ChainInvoker(Chain<P,N> chain) {
			this.chain=chain;
		}
		@SuppressWarnings("unchecked")
		@Override
		public void invoke(Flow<?> flow) {
			try {
				P previous=(P) flow.consumeCurrent();
				N next=chain.chain(previous);
				flow.setNext(next);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
	}
	
	public static class AsyncChainInvoker<P,N> implements Invoker {
		private AsyncChain<P, N> chain;
		public AsyncChainInvoker(AsyncChain<P,N> chain) {
			this.chain=chain;
		}
		@SuppressWarnings("unchecked")
		@Override
		public void invoke(Flow<?> flow) {
			try {
				P previous=(P) flow.consumeCurrent();
				Promise<N> next=new Promise<N>();
				chain.chain(previous, next);
				flow.suspendForNext(this, next);
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
	}
	
	public abstract static class Action implements Invoker {
		@Override
		public void invoke(Flow<?> flow) {
			try {
				flow.consumeCurrent();
				run();
			} catch (Throwable t) {
				flow.handleError(this, t);
			}
		}
		
		public abstract void run() throws Throwable;
	}
	
	public static class Steps extends ArrayList<Invoker> {
		public void addTo(Flow flow) {
			for (int i=0; i<size(); i++) {
				flow.add(get(i));
			}
		}

	}
	
	public Flow() {
		init();
	}
	
	public Flow(String name) {
		this.name=name;
		init();
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setOnError(int onError) {
		this.onError = onError;
	}
	
	protected void init() {
	}
	
	final boolean canRunImmediate() {
		return ondeck==null && blocker==null && pending.isEmpty() && !stopped;
	}
	
	final void assertNotStopped() {
		if (stopped) {
			throw new IllegalStateException("Cannot add steps to a stopped flow");
		}
	}
	
	final void logInvocation(Invoker invoker) {
		sequence++;
		if (logger.isDebugEnabled()) {
			logger.debug(name + "#" + sequence + ": Invoke {" + invoker + "} (current=" + current + ") on " + this);
		}
	}
	
	@SuppressWarnings("unchecked")
	final void schedule() {
		for (;;) {
			Promise localBlocker=null;
			Throwable localError=null;
			
			synchronized (this) {
				if (stopped) return;
				if (ondeck!=null || waitingOn!=null) return;
				if (blocker!=null) {
					waitingOn=blocker;
					localBlocker=blocker;
				}
				if (error!=null) {
					if (onError==ONERROR_RESOLVE) {
						localError=error;
						error=null;
						stopped=true;
					}
				}
			}

			// Handle any result from previous
			if (localError!=null) {
				resolveError(error);
				return;
			}

			// Wire up any blocker
			if (localBlocker!=null) {
				localBlocker.force(callback);
				return;
			}
			
			// Schedule any new one
			Invoker next;
			synchronized (this) {
				if (pending.isEmpty()) return;	
				next=pending.removeFirst();
				ondeck=next;
			}
			
			try {
				logInvocation(next);
				next.invoke(this);
			} finally {
				synchronized(this) {
					ondeck=null;
				}
			}
		}
	}
	
	public <N> void start(Promise<N> initial) {
		add(initial);
	}
	
	public void add(Invoker invoker) {
		boolean immediate;
		synchronized (this) {
			if (canRunImmediate()) {
				ondeck=invoker;
				immediate=true;
			} else {
				immediate=false;
				pending.add(invoker);
			}
		}
		
		if (immediate) {
			try {
				logInvocation(invoker);
				invoker.invoke(this);
			} finally {
				synchronized (this) {
					ondeck=null;
				}
			}
		}
		
		schedule();
	}
	
	public <N> void add(final Promise<N> promise) {
		boolean immediate=false;
		synchronized (this) {
			if (canRunImmediate()) {
				blocker=promise;
				immediate=true;
			}
		}
		
		if (immediate) {
			schedule();
		} else {
			add(new Invoker() {
				@Override
				public void invoke(Flow<?> flow) {
					flow.suspendForNext(this, promise);
				}
			});
		}
	}

	public <FROM,TO> void add(final Chain<FROM, TO> chain) {
		add(new ChainInvoker<FROM,TO>(chain));
	}
	
	public <FROM,TO> void add(final AsyncChain<FROM,TO> chain) {
		add(new AsyncChainInvoker<FROM,TO>(chain));
	}
	
	public final void finishWithLastResult() {
		add(new Invoker() {
			@SuppressWarnings("unchecked")
			@Override
			public void invoke(Flow<?> flow) {
				resolve((T) flow.consumeCurrent());
			}
		});
	}
	
	public final void finish(final T value) {
		add(new Invoker() {
			@SuppressWarnings("unchecked")
			@Override
			public void invoke(Flow<?> flow) {
				resolve(value);
			}
		});
	}
	
	
	protected void cleanup() {
	}
	
	public void resolve(T resolution) {
		synchronized (this) {
			stopped=true;
		}
		cleanup();
		super.resolve(resolution);
	}
	
	@Override
	public void resolveError(Throwable t) {
		synchronized (this) {
			stopped=true;
		}
		cleanup();
		super.resolveError(t);
	}
	
	public synchronized void add(Collection<? extends Invoker> steps) {
		for (Invoker step: steps) {
			add(step);
		}
	}
	
	public synchronized void stop() {
		stopped=true;
		waitingOn=null;
		blocker=null;
	}
	
	@Override
	public String toString() {
		return "Flow(" + name + ") -> " + super.toString();
	}
}
