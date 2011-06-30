package net.rcode.mrsession.util.io.redis;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import net.rcode.core.redis.RedisSerializer;

import org.junit.Test;
import static org.junit.Assert.*;

public class RedisSerializerTest {
	@Test
	public void testSimple() {
		ByteBuffer b=new RedisSerializer().serialize("SET", "mykey", "myvalue");
		
		Charset UTF8=Charset.forName("UTF8");
		CharBuffer cb=UTF8.decode(b);
		String msg=cb.toString();
		assertEquals("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n", msg);
	}
	
	@Test
	public void testLarger() {
		ByteBuffer b=new RedisSerializer().serialize("SET", "mykey", "myvalue1234567890");
		
		Charset UTF8=Charset.forName("UTF8");
		CharBuffer cb=UTF8.decode(b);
		String msg=cb.toString();
		assertEquals("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$17\r\nmyvalue1234567890\r\n", msg);
	}

}
