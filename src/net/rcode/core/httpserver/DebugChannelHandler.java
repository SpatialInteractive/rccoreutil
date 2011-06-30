package net.rcode.core.httpserver;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DebugChannelHandler extends SimpleChannelUpstreamHandler {
	public static final DebugChannelHandler INSTANCE=new DebugChannelHandler();
	private static final Logger logger=LoggerFactory.getLogger(DebugChannelHandler.class);
	
	private DebugChannelHandler() {
	}
	
	public boolean isEnabled() {
		return logger.isDebugEnabled();
	}
	
	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		logger.debug("channelConnected(" + e.getChannel().getRemoteAddress() + ")");
		super.channelConnected(ctx, e);
	}
	
	@Override
	public void channelDisconnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		logger.debug("channelDisconnected(" + e.getChannel().getRemoteAddress() + ")");
		super.channelDisconnected(ctx, e);
	}
	
	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		logger.debug("channelOpen(" + e.getChannel().getRemoteAddress() + ")");
		super.channelOpen(ctx, e);
	}
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		logger.debug("channelClosed(" + e.getChannel().getRemoteAddress() + ")");
		super.channelClosed(ctx, e);
	}
}
