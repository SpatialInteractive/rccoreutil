package net.rcode.core.httpserver;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Base class for JSON http services
 * @author stella
 *
 */
public abstract class JsonHttpRequestHandler extends DefaultHttpRequestHandler {
	private static final Gson GSON_PARSER;
	static {
		GsonBuilder gb=new GsonBuilder();
		gb.disableHtmlEscaping();
		GSON_PARSER=gb.create();
	}
	
	private JsonElement requestJson;
	
	protected JsonElement readRequestJson() throws Exception{
		if (requestJson!=null) return requestJson;
		if (requestData==null) return null;
		
		ChannelBufferInputStream stream=new ChannelBufferInputStream(requestData);
		InputStreamReader reader=new InputStreamReader(stream, CharsetUtil.UTF_8);
		requestJson=new JsonParser().parse(reader);
		return requestJson;
	}
	
	protected void respondJson(JsonElement json) throws Exception {
		HttpResponse response;
		
		if (json==null) {
			logger.warn("Null JSON response");
			response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
		} else {
			DynamicChannelBuffer buffer=new DynamicChannelBuffer(4096);
			OutputStream out=new ChannelBufferOutputStream(buffer);
			OutputStreamWriter writer=new OutputStreamWriter(out, CharsetUtil.UTF_8);
			
			GSON_PARSER.toJson(json, writer);
			writer.flush();
			
			response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			response.addHeader("Content-Type", "application/json;charset=utf-8");
			response.setContent(buffer);
		}		

		respond(response);
	}
}
