package net.rcode.core.couchdb;

import java.io.IOException;
import java.io.InputStreamReader;

import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import net.rcode.core.http.AsyncHttpRequest;
import net.rcode.core.http.AsyncHttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Encapsulate vital statistics about a couch response
 * @author stella
 *
 */
public class CouchResponse {
	public long runtime;
	
	/**
	 * HTTP status code of the response
	 */
	public int statusCode;
	
	/**
	 * If the body was decoded as json, this is it
	 */
	public JsonObject bodyJson;
	
	/**
	 * True if the response represents a conflict
	 */
	public boolean conflict;
	
	/**
	 * True if the response is for a not found document
	 */
	public boolean notfound;
	
	/**
	 * The revision from the etag header
	 */
	public String rev;

	private AsyncHttpRequest request;
	
	private static final JsonParser jsonParser=new JsonParser();
	
	public CouchResponse(AsyncHttpRequest request, AsyncHttpResponse response) throws IOException {
		this.request=request;
		this.runtime=response.runtime;
		this.statusCode=response.header.getStatus().getCode();
		
		if (!response.content.readable()) {
			this.bodyJson=null;
		} else {
			this.bodyJson=parseResponse(response).getAsJsonObject();
		}
		
		if (this.statusCode==409) {
			this.conflict=true;
		} else if (this.statusCode==404) {
			this.notfound=true;
		}
		
		String etag=response.header.getHeader("etag");
		if (etag!=null && etag.length()>2 && etag.charAt(0)=='"' && etag.charAt(etag.length()-1)=='"') {
			etag=etag.substring(1, etag.length()-1);
		}
		this.rev=etag;
	}
	
	private JsonElement parseResponse(AsyncHttpResponse response) throws IOException {
		String encoding=null;	// TODO: Read encoding from http server
		if (encoding==null) encoding="UTF-8";
		
		ChannelBufferInputStream stream=new ChannelBufferInputStream(response.content);
		InputStreamReader in=new InputStreamReader(stream, encoding);
		return jsonParser.parse(in);
	}

	@Override
	public String toString() {
		return String.format("CouchResponse(statusCode=%s,runtime=%sms,rev=%s,body=%s,request=%s)", statusCode, runtime, rev, new Gson().toJson(bodyJson), request);
	}

	/**
	 * If the couch response represents a failure or non ok status, throw an exception
	 * @throws RuntimeException
	 */
	public void ifError() throws RuntimeException {
		if (statusCode<200 || statusCode>=300) {
			throw new RuntimeException("Non successful status code " + statusCode + " from couchdb for " + this);
		}
	}
	
	public String getId() {
		if (bodyJson!=null) {
			JsonElement idElement=bodyJson.getAsJsonObject().get("id");
			if (idElement!=null) return idElement.getAsString();
		}
		return null;
	}
}
