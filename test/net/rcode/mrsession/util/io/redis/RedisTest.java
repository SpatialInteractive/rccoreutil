package net.rcode.mrsession.util.io.redis;

import java.io.IOException;

import net.rcode.core.redis.Redis;
import net.rcode.core.redis.RedisManager;
import net.rcode.core.redis.RedisResult;
import net.rcode.core.redis.Redis.SubscriptionHandler;
import net.rcode.mrsession.util.TestHelper;

import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class RedisTest {
	private static RedisManager rm;
	private Redis redis;

	@BeforeClass
	public static void init() throws IOException {
		BasicConfigurator.configure();
	}

	
	@Before
	public void setup() throws InterruptedException, IOException {
		System.out.println("--- > Creating redis");
		rm=new RedisManager(TestHelper.getClientChannelFactory(), TestHelper.getClientOptions());
		
		this.redis=rm.connect();
	}
	
	@After
	public void teardown() throws IOException, InterruptedException {
		System.out.println("==== > Tearing down redis");
		redis.quit().waitFor();
		redis=null;
	}
	
	@Test
	public void testPing() throws IOException, InterruptedException {
		RedisResult result;
		
		result=redis.execute("PING").waitFor();
		System.out.println("Got result: " + result);
	}

	@Test
	public void testPing2() throws IOException, InterruptedException {
		RedisResult result;
		
		result=redis.execute("PING").waitFor();
		System.out.println("Got result: " + result);
		result=redis.execute("PING").waitFor();
		System.out.println("Got result: " + result);
		result=redis.execute("PING").waitFor();
		System.out.println("Got result: " + result);
		result=redis.execute("PING").waitFor();
		System.out.println("Got result: " + result);
	}

	@Test
	public void testPubSub() throws InterruptedException, IOException {
		Redis subRedis=rm.connect();
		System.out.println("Subscribing");
		subRedis.subscribe(new SubscriptionHandler() {
			@Override
			public void handleMessage(String channel, RedisResult message) {
				System.out.println("Received message from " + channel + ": " + message);
			}
		}, "testchannel");
		Thread.sleep(500);
		
		System.out.println("PUBLISH1: " + redis.execute("PUBLISH", "testchannel", "test... 1").waitFor());
		System.out.println("PUBLISH2: " + redis.execute("PUBLISH", "testchannel", "test... 2").waitFor());
		System.out.println("PUBLISH3: " + redis.execute("PUBLISH", "testchannel", "test... 3").waitFor());
		Thread.sleep(5000);
		
		System.out.println("Stopping connection");
		subRedis.quit().waitFor();
		System.out.println("Connection stopped");
		
		Thread.sleep(1000);
	}
}
