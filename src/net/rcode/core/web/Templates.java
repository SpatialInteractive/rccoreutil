package net.rcode.core.web;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.rcode.core.httpserver.HttpState;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Wrapper around freemarker that provides access to templates.
 * 
 * @author stella
 *
 */
public class Templates {
	private Configuration config;
	private WeakHashMap<Template, Instance> cache=new WeakHashMap<Template, Templates.Instance>();
	
	public class Instance {
		private Template template;
		private AtomicInteger lastSize=new AtomicInteger(8192);
		
		public Instance(Template template) {
			this.template=template;
		}
		
		/**
		 * Render to a ChannelBuffer ready to be sent out with a response
		 * @param context
		 * @return ChannelBuffer
		 * @throws IOException 
		 * @throws TemplateException 
		 */
		public ChannelBuffer render(Object context) throws TemplateException, IOException {
			int sizeEstimate=lastSize.get() * 2;
			ChannelBuffer buffer=ChannelBuffers.dynamicBuffer(sizeEstimate);
			ChannelBufferOutputStream stream=new ChannelBufferOutputStream(buffer);
			OutputStreamWriter out=new OutputStreamWriter(stream, CharsetUtil.UTF_8);
			template.process(context, out);
			out.flush();
			
			lastSize.set(buffer.readableBytes());
			
			return buffer;
		}

		/**
		 * Use a worker thread to render the template and return a response (error or success)
		 * @param http
		 * @param model
		 */
		public void render(final HttpState http, final Map<String, Object> model) {
			http.getServer().getWebWorkerExecutor().execute(new Runnable() {
				@Override
				public void run() {
					try {
						ChannelBuffer buffer=render(model);
						HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
						response.setContent(buffer);
						response.addHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=utf-8");
						http.respond(response);
					} catch (Throwable t) {
						http.respondError(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error rendering template", t);
					}
				}
			});
		}
	}
	
	public Templates(File basedir) throws IOException {
		config=new Configuration();
		config.setDirectoryForTemplateLoading(basedir);
		config.setTagSyntax(Configuration.ANGLE_BRACKET_TAG_SYNTAX);
		config.setDefaultEncoding("UTF-8");
		config.setOutputEncoding("UTF-8");
		config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
	}
	
	public Instance getTemplate(String name) throws IOException {
		Template t=config.getTemplate(name);
		Instance inst=cache.get(t);
		if (inst==null) {
			inst=new Instance(t);
			cache.put(t, inst);
		}
		
		return inst;
	}
	
	public Configuration getConfig() {
		return config;
	}

}
