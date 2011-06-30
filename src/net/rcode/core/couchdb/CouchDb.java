package net.rcode.core.couchdb;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;
import net.rcode.core.http.AsyncHttpClient;
import net.rcode.core.http.AsyncHttpRequest;
import net.rcode.core.http.AsyncHttpResponse;
import net.rcode.core.io.BlockOutputStream;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Key access points for interacting with a CouchDB database.
 * 
 * @author stella
 *
 */
public class CouchDb {
	public static final int UUID_BATCH_SIZE=100;
	private static Gson GSON=new Gson();
	
	private Logger logger;
	
	private AsyncHttpClient client;
	private boolean secure;
	private String hostname;
	private int port;
	private String hostHeader;
	private String pathPrefix;
	
	/**
	 * Array of prefetched uuids.  synchronize on the array.  Pop off of the back.
	 */
	private ArrayList<String> prefetchedUuids=new ArrayList<String>();
	
	
	public CouchDb(AsyncHttpClient client, URI uri) {
		this.client=client;
		
		String scheme=uri.getScheme();
		if (scheme==null) scheme="http";
		if ("https".equals(scheme)) secure=true;
		
		hostname=uri.getHost();
		port=uri.getPort();
		if (port<0) {
			hostHeader=hostname;
			if ("https".equals(scheme)) port=443;
			else port=80;
		} else {
			this.hostHeader=hostname + ':' + port;
		}
		
		this.pathPrefix=uri.getRawPath();
		if (this.pathPrefix==null) this.pathPrefix="";
		if (this.pathPrefix.endsWith("/")) this.pathPrefix=this.pathPrefix.substring(0, this.pathPrefix.length()-1);
		
		// Logger
		logger=LoggerFactory.getLogger(getClass().getName() + ":" + hostHeader);
		
	}
	
	public static String encodeURI(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// Cannot happen
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Creates a database
	 * @param database
	 * @return true if exists, false if not
	 * @throws IOException 
	 */
	public Promise<Boolean> createDatabase(String database) {
		return request("PUT", dbPath(database), null, null)
			.chain(new Promise.Chain<CouchResponse,Boolean>() {
				@Override
				public Boolean chain(CouchResponse response) throws Throwable {
					if (response.statusCode==201) return true;
					else if (response.statusCode==412) return false;
					
					response.ifError();
					return false;
				}
			});
	}
	
	/**
	 * Delete the database.  
	 * @param database
	 * @return
	 * @throws IOException
	 */
	public Promise<Boolean> deleteDatabase(String database) {
		return request("DELETE", dbPath(database), null, null)
		.chain(new Promise.Chain<CouchResponse,Boolean>() {
			@Override
			public Boolean chain(CouchResponse response) throws Throwable {
				if (response.statusCode==200) return true;
				else if (response.statusCode==404) return false;
				
				response.ifError();
				return false;
			}
		});
	}
	
	/**
	 * Recreate a database in one step.  Returns the create which should always be true
	 * @param database
	 * @return
	 * @throws IOException
	 */
	public Promise<Boolean> recreateDatabase(final String database) {
		return deleteDatabase(database).chain(new Promise.AsyncChain<Boolean,Boolean>() {
			@Override
			public void chain(Boolean input, Promise<Boolean> result)
					throws Throwable {
				createDatabase(database).forward(result);
			}
		});
	}
	
	/**
	 * Get a document in the form of a CouchResponse
	 * @param database
	 * @param id
	 * @return
	 * @throws IOException
	 */
	public Promise<CouchResponse> readDocument(String database, String id) {
		return request("GET", docPath(database, id), null, null);
	}

	public Flow.Initiator<CouchResponse> readDocumentStep(final String database, final String id) {
		return new Flow.Initiator<CouchResponse>() {
			@Override
			public Promise<CouchResponse> run() throws Throwable {
				return readDocument(database, id);
			}
		};
	}

	/**
	 * Get document headers
	 * @param database
	 * @param id
	 * @return CouchResponse (bodyJson will be null)
	 * @throws IOException
	 */
	public Promise<CouchResponse> headDocument(String database, String id) {
		// TODO: http://java.net/jira/browse/GRIZZLY-1000  Grizzly not properly supporting HEAD
		return request("GET", docPath(database, id), null, null);
	}
	
	public Flow.Initiator<CouchResponse> headDocumentStep(final String database, final String id) {
		return new Flow.Initiator<CouchResponse>() {
			@Override
			public Promise<CouchResponse> run() throws Throwable {
				return headDocument(database, id);
			}
		};
	}
	
	/**
	 * Creates a document with a generated id, retrying as necessary on conflict.
	 * 
	 * @param database
	 * @param document
	 * @return response
	 * @throws IOException 
	 */
	public Promise<CouchResponse> createDocument(final String database, final JsonObject document) {
		Flow<CouchResponse> flow=new Flow<CouchResponse>();
		
		createDocumentHelp(database, document, flow);
		
		return flow;
	}
	
	public Flow.Initiator<CouchResponse> createDocumentStep(final String database, final JsonObject document) {
		return new Flow.Initiator<CouchResponse>() {
			@Override
			public Promise<CouchResponse> run() throws Throwable {
				return createDocument(database, document);
			}
			@Override
			public String toString() {
				return "Create document";
			}
		};
	}
	
	protected void createDocumentHelp(final String database, final JsonObject document, final Flow<CouchResponse> flow) {
		flow.add(new Flow.Initiator<String>() {
			@Override
			public Promise<String> run() throws IOException {
				return getUuid();
			}
			@Override
			public String toString() {
				return "Get New Uuid";
			}
		});
		
		flow.add(new Flow.AsyncTransform<String,CouchResponse>() {
			@Override
			public Promise<CouchResponse> run(String id) throws IOException {
				return updateDocument(database, id, document);
			}
			@Override
			public String toString() {
				return "updateDocument";
			}
		});
		
		flow.add(new Flow.AsyncTransform<CouchResponse,CouchResponse>() {
			@Override
			public Promise<CouchResponse> run(CouchResponse response) {
				if (response.conflict) {
					// Loop
					createDocumentHelp(database, document, flow);
					return null;
				}
				
				flow.resolve(response);
				return null;
			}
			@Override
			public String toString() {
				return "validate document";
			}
		});
	}
	
	/**
	 * Update a document (or create with a known id).  Can also be used to create a document with a known id.
	 *  
	 * @param database
	 * @param id
	 * @param document
	 * @return
	 * @throws IOException 
	 */
	public Promise<CouchResponse> updateDocument(String database, String id, JsonObject document) {
		return updateDocument(database, id, null, document);
	}
	
	public Promise<CouchResponse> updateDocument(String database, String id, String rev, JsonObject document) {
		Map<String,String> headers=new HashMap<String, String>();
		headers.put("Content-Type", "application/json;charset=UTF-8");
		
		String path=docPath(database, id, rev);
		return request("PUT", path, serializeDocument(document), headers);
	}
	
	/**
	 * Delete a document of a specific revision
	 * @param database
	 * @param id
	 * @param rev
	 * @return resposne
	 * @throws IOException
	 */
	public Promise<CouchResponse> deleteDocument(String database, String id, String rev) {
		return request("DELETE", docPath(database, id) + "?rev=" + encodeURI(rev), null, null);
	}
	
	/**
	 * Gets the current document revision and deletes it in one step
	 * @param database
	 * @param id
	 * @return CouchResponse of either the HEAD (if not found) or DELETE
	 * @throws IOException 
	 */
	public Promise<CouchResponse> deleteDocumentNoRevision(final String database, final String id) {
		DeleteDocumentFlow flow=new DeleteDocumentFlow(database, id);
		flow.start();
		return flow;
	}
	
	private class DeleteDocumentFlow extends Flow<CouchResponse> {
		String database;
		String id;
		
		public DeleteDocumentFlow(String database, String id) {
			this.database=database;
			this.id=id;
		}
		
		public void start() {
			add(headDocumentStep(database, id));
			add(new Flow.AsyncTransform<CouchResponse,CouchResponse>() {
				@Override
				public String toString() {
					return "Delete document";
				}
				
				@Override
				public Promise<CouchResponse> run(CouchResponse previous)
						throws Throwable {
					if (previous.notfound) {
						// That's it
						resolve(previous);
						stop();
						return null;
					}
					previous.ifError();
					
					return deleteDocument(database, id, previous.rev);
				}
			});
			add(new Flow.Consumer<CouchResponse>() {
				public String toString() {
					return "Detect conflict";
				}

				@Override
				public void run(CouchResponse previous) throws Throwable {
					if (previous.conflict) {
						// Otherwise, try again
						logger.debug("Looping to delete conflicting document");
						start();
						return;
					}
					
					resolve(previous);
				}
			});
		}
	}
	
	/**
	 * Perform an interlocked update of a document with an updater step that can be applied repeatedly
	 * in the event of conflict.
	 * @param database
	 * @param id
	 * @param updater
	 * @return last update response
	 */
	public Promise<CouchResponse> updateDocument(String database, String id, Flow.AsyncTransform<JsonObject, JsonObject> updater) {
		UpdateDocumentFlow flow=new UpdateDocumentFlow(database, id, updater);
		flow.start();
		return flow;
	}
	
	private class UpdateDocumentFlow extends Flow<CouchResponse> {
		String database;
		String id;
		AsyncTransform<JsonObject, JsonObject> updater;
		String revision;
		
		public UpdateDocumentFlow(String database, String id,
				AsyncTransform<JsonObject, JsonObject> updater) {
			this.database=database;
			this.id=id;
			this.updater=updater;
		}
		
		public void start() {
			add(new Flow.Initiator<CouchResponse>() {
				@Override
				public Promise<CouchResponse> run() throws Throwable {
					return readDocument(database, id);
				}
				@Override
				public String toString() {
					return "Retreive base revision";
				}
			});
			add(new Flow.AsyncTransform<CouchResponse,JsonObject>() {
				@Override
				public String toString() {
					return "Record revision";
				}

				@Override
				public Promise<JsonObject> run(CouchResponse response)
						throws Throwable {
					JsonObject current;
					if (response.notfound) current=null;
					else current=response.bodyJson.getAsJsonObject();
					
					revision=response.rev;
					
					return Promise.fixed(current);
				}
			});
			add(updater);
			add(new Flow.AsyncTransform<JsonObject,CouchResponse>() {
				@Override
				public String toString() {
					return "Update Document at Revision";
				}

				@Override
				public Promise<CouchResponse> run(JsonObject newDocument)
						throws Throwable {
					if (newDocument==null) {
						// Opted to now update the document.  Done.
						stop();
						resolve(null);
						return null;
					} else {
						return updateDocument(database, id, revision, newDocument);
					}
				}
			});
			add(new Flow.Consumer<CouchResponse>() {
				@Override
				public String toString() {
					return "Detect conflict";
				}
				@Override
				public void run(CouchResponse response) throws Throwable {
					if (response.conflict) {
						logger.debug("Looping to update document " + database + "/" + id + " (" + revision + ") due to conflict.");
						start();
						return;
					}
					
					// Otherwise, this is the answer
					resolve(response);
				}
			});
		}
	}
	
	byte[] serializeDocument(JsonElement document) {
		try {
			BlockOutputStream out=new BlockOutputStream();
			OutputStreamWriter writer=new OutputStreamWriter(out, "UTF-8");
			GSON.toJson(document, writer);
			writer.flush();
			
			return out.getBytes();
		} catch (IOException e) {
			throw new CouchDbException("Error serializing json document", e);
		}
	}
	
	
	
	/**
	 * Ask the datastore for a batch of uuids.
	 * @return uuids
	 * @throws IOException 
	 */
	public Promise<String[]> fetchUuids(int count) {
		return request("GET", "/_uuids?count=" + count, null, null).chain(new Promise.Chain<CouchResponse,String[]>() {
			@Override
			public String[] chain(CouchResponse response) throws Throwable {
				response.ifError();
				
				if (logger.isDebugEnabled()) logger.debug("Fetched batch of uuids in " + response.runtime + "ms");
				JsonArray ary=response.bodyJson.getAsJsonObject().get("uuids").getAsJsonArray();
				String[] ids=new String[ary.size()];
				for (int i=0; i<ids.length; i++) {
					ids[i]=ary.get(i).getAsString();
				}
				
				return ids;
			}
		});
	}
	
	
	
	/**
	 * Get the next uuid from the shared cache
	 * @return uuid
	 * @throws IOException
	 */
	public Promise<String> getUuid() {
		synchronized(prefetchedUuids) {
			if (!prefetchedUuids.isEmpty()) {
				return Promise.fixed(prefetchedUuids.remove(prefetchedUuids.size()-1));
			}
		}
		
		// Non-happy path.  Need to refresh.
		// TODO: Add some synchronization here so we aren't requesting 10000 batches at a time
		return fetchUuids(UUID_BATCH_SIZE).chain(new Promise.Chain<String[],String>() {
			@Override
			public String chain(String[] input) throws Throwable {
				synchronized (prefetchedUuids) {
					prefetchedUuids.addAll(Arrays.asList(input));
					return prefetchedUuids.remove(prefetchedUuids.size()-1);
				}
			}
		});
	}
	
	public String docPath(String database, String id, String rev) {
		if (id==null || database==null) {
			// Really, really bad things can happen if we get the path a little wrong
			throw new IllegalArgumentException("database and id must be non null");
		}
		
		StringBuilder builder=new StringBuilder((database.length()+id.length()) * 4);
		builder.append(pathPrefix);
		builder.append('/');
		builder.append(encodeURI(database));
		builder.append('/');
		builder.append(encodeURI(id));
		
		if (rev!=null) {
			builder.append("?rev=");
			builder.append(encodeURI(rev));
		}
		return builder.toString();
	}
	
	public String docPath(String database, String id) {
		return docPath(database, id, null);
	}
	
	public String dbPath(String database) {
		if (database==null) {
			throw new IllegalArgumentException("database must not be null");
		}
		return pathPrefix + '/' + encodeURI(database);
	}
	
	public Promise<CouchResponse> request(String method, String path, byte[] body, Map<String,String> headers) {
		if (logger.isDebugEnabled()) {
			StringBuilder sb=new StringBuilder();
			sb.append("CouchDb request to ").append(hostHeader).append(pathPrefix).append(path);
			sb.append(" [").append(method).append("]");
			if (body!=null) {
				sb.append(":\n\t");
				try {
					sb.append(new String(body, "UTF-8"));
				} catch (IOException e) {
					// Ignore
				}
			}
			logger.debug(sb.toString());
		}
		
		HttpRequest requestPacket=new DefaultHttpRequest(HttpVersion.HTTP_1_1, 
				HttpMethod.valueOf(method), path);
		requestPacket.addHeader("Host", hostHeader);

		if (headers==null) headers=new HashMap<String, String>();
		
		// Default headers
		if (body!=null) {
			HttpHeaders.setContentLength(requestPacket, body.length);
			requestPacket.setContent(ChannelBuffers.wrappedBuffer(body));
		}
		
		for (Map.Entry<String, String> header: headers.entrySet()) {
			requestPacket.addHeader(header.getKey(), header.getValue());
		}
		
		final AsyncHttpRequest request=new AsyncHttpRequest(requestPacket);
		
		AsyncHttpClient.Transaction transaction=client.startTransaction(hostname, port);
		transaction.submit(request);
		
		Promise<CouchResponse> ret=request.getResponse().chain(new Promise.Chain<AsyncHttpResponse,CouchResponse>() {
			@Override
			public CouchResponse chain(AsyncHttpResponse input) throws IOException {
				return new CouchResponse(request, input);
			}
		});
		
		return ret;
	}
}
