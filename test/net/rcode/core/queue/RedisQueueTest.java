package net.rcode.core.queue;

import java.util.HashMap;
import java.util.Map;

import net.rcode.core.redis.RedisConnections;
import net.rcode.core.redis.RedisManager;
import net.rcode.mrsession.util.TestHelper;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class RedisQueueTest {
	static RedisManager rm;
	static RedisConnections connections;
	
	@BeforeClass
	public static void setUp() {
		rm=new RedisManager(TestHelper.getClientChannelFactory(), TestHelper.getClientOptions());
		rm.setDatabase(15);	// Test database
		
		connections=new RedisConnections(rm);
		connections.getStreamingConnection().execute("FLUSHDB");
	}
	
	@Test
	public void testPutTakeCommit() throws InterruptedException {
		Map<String, String> msg=new HashMap<String, String>();
		RedisQueue client=new RedisQueue(connections.getStreamingConnection(), "testqueue1");

		msg.put("test", "1");
		String id1=client.put(msg, 0).waitFor();
		
		msg.put("test", "2");
		String id2=client.put(msg, 0).waitFor();
		
		System.out.format("Put items: Id1=%s, Id2=%s\n", id1, id2);
		assertTrue(Integer.parseInt(id2)>Integer.parseInt(id1));
		
		QueueEntry entry1=client.take().waitFor();
		QueueEntry entry2=client.take().waitFor();
		assertNull(client.take().waitFor());
		
		assertEquals(id1, entry1.id);
		assertEquals(id2, entry2.id);
		assertEquals("1", entry1.properties.get("test"));
		assertEquals("2", entry2.properties.get("test"));
		
		client.commit(id1);
		client.commit(id2);
	}
	
	@Test
	public void testPutTakeWithExpiry() throws InterruptedException {
		Map<String, String> msg=new HashMap<String, String>();
		RedisQueue client=new RedisQueue(connections.getStreamingConnection(), "testqueue2");
		
		msg.put("test", "1");
		String id=client.put(msg, 1).waitFor();
		Thread.sleep(3000);
		QueueEntry entry=client.take().waitFor();
		assertNull("entry should be null: " + entry, entry);
	}

	@Test
	public void testPutTakeMultiWithExpiry() throws InterruptedException {
		Map<String, String> msg=new HashMap<String, String>();
		RedisQueue client=new RedisQueue(connections.getStreamingConnection(), "testqueue2");
		
		msg.put("test", "1");
		String id1=client.put(msg, 1).waitFor();

		msg.put("test", "2");
		String id2=client.put(msg, 0).waitFor();
		
		Thread.sleep(3000);
		QueueEntry entry=client.take().waitFor();
		assertEquals(id2, entry.id);
		assertEquals("2", entry.properties.get("test"));
		
		entry=client.take().waitFor();
		assertNull("entry should be null: " + entry, entry);
	}

}

