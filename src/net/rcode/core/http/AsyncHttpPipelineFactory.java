package net.rcode.core.http;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;

import static org.jboss.netty.channel.Channels.*;

/**
 * Factory for pipelines for http client connections
 * @author stella
 *
 */
public class AsyncHttpPipelineFactory implements ChannelPipelineFactory {

	@Override
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = pipeline();
		
		pipeline.addLast("codec", new HttpClientCodec());
		pipeline.addLast("inflater", new HttpContentDecompressor());
		pipeline.addLast("handler", new AsyncHttpResponseHandler());
		
		return pipeline;
	}

}
