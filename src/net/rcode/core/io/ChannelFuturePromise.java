package net.rcode.core.io;

import java.util.concurrent.CancellationException;

import net.rcode.core.async.Promise;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * A Promise that bridges a ChannelFuture
 * @author stella
 *
 */
public class ChannelFuturePromise extends Promise<Channel> implements ChannelFutureListener {
	public ChannelFuturePromise(ChannelFuture future) {
		future.addListener(this);
	}

	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		if (future.isSuccess()) {
			resolve(future.getChannel());
		} else {
			if (future.isCancelled()) {
				resolveError(new CancellationException());
			} else {
				Throwable cause=future.getCause();
				if (cause!=null) {
					resolveError(cause);
				} else {
					resolveError(new IllegalStateException());
				}
			}
		}
	}
}
