package net.rcode.mrsession.util;

import net.rcode.core.util.JsonBuilder;

import org.junit.Test;

import com.google.gson.JsonParser;

import static org.junit.Assert.*;

public class JsonBuilderTest {

	@Test
	public void testTypical() {
		JsonBuilder b=new JsonBuilder();
		b.startObject();
		b.key("t");
		b.value("o");
		
		b.key("l");
		b.startArray();
		
		b.startObject();
		b.key("s").value("grp");
		b.key("k").value("req");
		b.key("v").value(1);
		b.endObject();
		
		b.value(2);
		b.endArray();
		b.endObject();
		
		String json=b.toString();
		testParse(json);
		assertEquals("{\"t\":\"o\",\"l\":[{\"s\":\"grp\",\"k\":\"req\",\"v\":1},2]}", json);
	}

	private void testParse(String json) {
		new JsonParser().parse(json);
	}
}
