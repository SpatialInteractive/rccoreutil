package net.rcode.mrsession.util;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public class TestHelper {
	private static ChannelFactory clientChannelFactory;
	public static synchronized ChannelFactory getClientChannelFactory() {
		if (clientChannelFactory==null) {
			clientChannelFactory=new NioClientSocketChannelFactory(
					Executors.newCachedThreadPool(),
					Executors.newCachedThreadPool()
					);
		}
		
		return clientChannelFactory;
	}
	
	public static Map<String,Object> getClientOptions() {
		return Collections.emptyMap();
	}
	
}
