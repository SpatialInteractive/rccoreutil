package net.rcode.mrsession.util.couchdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import net.rcode.core.async.Promise;
import net.rcode.core.couchdb.CouchDb;
import net.rcode.core.couchdb.CouchResponse;
import net.rcode.core.http.AsyncHttpClient;
import net.rcode.mrsession.util.TestHelper;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CouchDbTest {
	static final String dbname="jcdbtest";
	private static AsyncHttpClient client;
	private static CouchDb couch;
	
	@BeforeClass
	public static void staticSetup() throws IOException, URISyntaxException {
		BasicConfigurator.configure();
		client=new AsyncHttpClient(TestHelper.getClientChannelFactory(), TestHelper.getClientOptions());
		URI uri=new URI("http://localhost:5984");
		couch=new CouchDb(client, uri);
	}

	
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testRequest() throws Exception {
		Promise<CouchResponse> p=couch.request("GET", "/_uuids?count=1", null, null);
		//Promise<CouchResponse> p=couch.request("GET", "/svn/mavenrepo/com/google/code/gson/gson/1.6/gson-1.6.jar", null, null);
		
		CouchResponse resp=p.waitFor();
		System.out.println("Response=" + resp);
		resp.ifError();
	}

	@Test
	public void testCreateDropRecreateDatabase() throws InterruptedException, IOException {
		
		couch.createDatabase(dbname).waitFor();
		
		assertTrue(couch.deleteDatabase(dbname).waitFor());
		assertFalse(couch.deleteDatabase(dbname).waitFor());
		assertTrue(couch.createDatabase(dbname).waitFor());
		assertTrue(couch.recreateDatabase(dbname).waitFor());
		assertTrue(couch.deleteDatabase(dbname).waitFor());
	}
	
	@Test
	public void testFetchUuids() throws InterruptedException, IOException {
		String[] uuids=couch.fetchUuids(10).waitFor();
		assertEquals(10, uuids.length);
		for (int i=0; i<uuids.length; i++) {
			assertTrue(uuids[i].length()>0);
			if (i>0) assertFalse(uuids[i].equals(uuids[i-1]));
		}
	}
	
	/*
	@Test
	public void testGetUuid() throws InterruptedException {
		long time=System.currentTimeMillis();
		
		final Set<String> uuids=new HashSet<String>();
		Sequence<Void> sequence=new Sequence<Void>();
		
		for (int i=0; i<300; i++) {
			sequence.add(new Promise.Initiator<Void>() {
				@Override
				public Promise<Void> initiate() throws IOException {
					return couch.getUuid().chain(new Promise.Chain<String,Void>() {
						@Override
						public Void chain(String uuid) throws Throwable {
							//System.out.println("Got uuid " + uuid);
							assertTrue(uuids.add(uuid));
							return null;
						}
					});
				}
			});
		}
		
		sequence.finish();
		sequence.waitFor();
		
		assertEquals(300, uuids.size());
		System.out.println("testGetUuid() runtime = " + (System.currentTimeMillis()-time) + "ms");
	}
	*/
	
	@Test
	public void testReadCreateDeleteDocument() throws InterruptedException, IOException {
		couch.recreateDatabase(dbname).waitFor();
		
		String docString="{\"a\":1}";
		JsonObject doc=new JsonParser().parse(docString).getAsJsonObject();
		
		CouchResponse cr;
		cr=couch.readDocument(dbname, "test1").waitFor();
		assertTrue(cr.notfound);
		
		cr=couch.createDocument(dbname, doc).waitFor();
		cr.ifError();
		assertTrue(cr.bodyJson.getAsJsonObject().get("ok").getAsBoolean());
		assertNotNull(cr.bodyJson.getAsJsonObject().get("id"));
		assertNotNull(cr.bodyJson.getAsJsonObject().get("rev"));

		String id=cr.getId();
		String rev=cr.rev;

		cr=couch.readDocument(dbname, id).waitFor();
		cr.ifError();
		assertEquals(1, cr.bodyJson.getAsJsonObject().get("a").getAsInt());
		
		cr=couch.deleteDocument(dbname, id, rev).waitFor();
		cr.ifError();
		
		cr=couch.readDocument(dbname, id).waitFor();
		assertTrue(cr.notfound);
	}
	
	@Test
	public void testHeadDocument() throws InterruptedException, IOException {
		CouchResponse cr;
		couch.recreateDatabase(dbname).waitFor();
		
		String docString="{\"a\":1}";
		JsonObject doc=new JsonParser().parse(docString).getAsJsonObject();
		cr=couch.createDocument(dbname, doc).waitFor();
		cr.ifError();
		String id=cr.bodyJson.getAsJsonObject().get("id").getAsString();
		
		cr=couch.headDocument(dbname, id).waitFor();
		cr.ifError();
		assertNotNull(cr.rev);
	}
}
