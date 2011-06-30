package net.rcode.core.httpserver.websocket;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform basic maintenance on the websocket channel
 * @author stella
 *
 */
public class StandardWebSocketChannelHandler extends
		SimpleChannelUpstreamHandler {
	private static final Logger logger=LoggerFactory.getLogger(StandardWebSocketChannelHandler.class);
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		logger.error("Exception caught while servicing websocket pipeline (" + e.getChannel().getRemoteAddress() + "). Closing connection.", e.getCause());
		ctx.getChannel().close();
	}
}
