package net.rcode.core.httpserver.eventsource;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSourceChannelHandler extends SimpleChannelUpstreamHandler 
	implements ChannelDownstreamHandler 
{
	public static final EventSourceChannelHandler INSTANCE=new EventSourceChannelHandler();
	
	private static final Logger logger=LoggerFactory.getLogger(EventSourceChannelHandler.class);
	
	@Override
	public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt)
			throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }
        
        MessageEvent messageEvt=(MessageEvent) evt;
        Object message=messageEvt.getMessage();
        if (message instanceof EventSourceFrame) {
        	EventSourceFrame frame=(EventSourceFrame) message;
        	ChannelBuffer buffer=frame.finish();
        	
        	// Send it on
        	Channels.write(ctx, evt.getFuture(), buffer, messageEvt.getRemoteAddress());
        } else {
        	ctx.sendDownstream(evt);
        }
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		logger.error("Exception caught on EventSource channel(" + e.getChannel().getRemoteAddress() + ")", e.getCause());
		e.getChannel().close();
		
		super.exceptionCaught(ctx, e);
	}
	
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		logger.debug("EventSource channel closed");
		super.channelClosed(ctx, e);
	}
}
