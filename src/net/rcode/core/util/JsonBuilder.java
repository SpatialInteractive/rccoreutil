package net.rcode.core.util;

import java.util.ArrayList;

import org.apache.commons.lang.StringEscapeUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * Builds up a json message textually
 * @author stella
 *
 */
public class JsonBuilder {
	private static final Gson gson=new Gson();
	private StringBuilder json;
	private ArrayList<Integer> states=new ArrayList<Integer>(15);
	private int state;
	
	private static final int STATE_TYPE_INITIAL=0x0;
	private static final int STATE_TYPE_OBJECT=0x1;
	private static final int STATE_TYPE_ARRAY=0x2;
	
	private static final int STATE_FLAG_FIRST=0x10;
	private static final int STATE_FLAG_KEY=0x20;
	
	private static final int STATE_MASK_TYPE=0xf;
	private static final int STATE_MASK_FLAGS=0xf0;
	
	public JsonBuilder() {
		json=new StringBuilder();
		state=STATE_TYPE_INITIAL | STATE_FLAG_FIRST;
	}
	
	public CharSequence getJson() {
		endAll();
		return json;
	}
	
	public String toString() {
		endAll();
		return json.toString();
	}
	
	public final JsonBuilder append(CharSequence s) {
		json.append(s);
		return this;
	}
	
	public final JsonBuilder append(char c) {
		json.append(c);
		return this;
	}
	
	private int stateType() {
		return state & STATE_MASK_TYPE;
	}
	
	private boolean hasFlag(int flag) {
		return (state & flag) == flag;
	}
	
	private void clearFlag(int flag) {
		state=state & ~flag;
	}
	
	private void setFlag(int flag) {
		state=state | flag;
	}
	
	private void comma() {
		if (!hasFlag(STATE_FLAG_FIRST)) {
			append(',');
		} else {
			clearFlag(STATE_FLAG_FIRST);
		}
	}
	
	public final JsonBuilder key(String k, boolean escape) {
		if (stateType()!=STATE_TYPE_OBJECT || hasFlag(STATE_FLAG_KEY)) {
			throw new IllegalArgumentException();
		}
		comma();
		
		append('"');
		if (escape) append(StringEscapeUtils.escapeJavaScript(k));
		else append(k);
		append("\":");
		
		setFlag(STATE_FLAG_KEY);
		return this;
	}
	
	public final JsonBuilder key(String k) {
		return key(k, true);
	}
	
	private void startValue() {
		int type=stateType();
		if (type==STATE_TYPE_INITIAL) {
			if (!hasFlag(STATE_FLAG_FIRST)) throw new IllegalStateException();
			clearFlag(STATE_FLAG_FIRST);
		} else if (type==STATE_TYPE_OBJECT) {
			if (!hasFlag(STATE_FLAG_KEY)) throw new IllegalStateException();
			clearFlag(STATE_FLAG_KEY);
		} else if (type==STATE_TYPE_ARRAY) {
			comma();
		}
	}
	
	public final JsonBuilder value(String v, boolean escape) {
		startValue();
		if (v==null) append("null");
		else {
			append('"');
			append(escape ? StringEscapeUtils.escapeJavaScript(v) : v);
			append('"');
		}
		return this;
	}
	
	public final JsonBuilder value(String v) {
		value(v, true);
		return this;
	}
	
	public final JsonBuilder valueNull() {
		startValue();
		append("null");
		return this;
	}
	
	public final JsonBuilder value(int i) {
		startValue();
		append(String.valueOf(i));
		return this;
	}
	
	public final JsonBuilder value(JsonElement element) {
		startValue();
		if (element==null || element.isJsonNull()) append("null");
		else append(gson.toJson(element));
		return this;
	}
	
	public final JsonBuilder startObject() {
		startValue();
		append('{');
		states.add(state);
		state=STATE_TYPE_OBJECT | STATE_FLAG_FIRST;
		return this;
	}
	
	public final JsonBuilder startArray() {
		startValue();
		append('[');
		states.add(state);
		state=STATE_TYPE_ARRAY | STATE_FLAG_FIRST;
		return this;
	}
	
	private int end() {
		int type=stateType();
		if (type==STATE_TYPE_OBJECT) {
			append('}');
		} else if (type==STATE_TYPE_ARRAY) {
			append(']');
		} else if (type==STATE_TYPE_INITIAL) {
			return type;
		}
		
		state=states.remove(states.size()-1);
		return type;
	}
	
	public final JsonBuilder endObject() {
		if (end()!=STATE_TYPE_OBJECT) {
			throw new IllegalStateException();
		}
		return this;
	}
	
	public final JsonBuilder endArray() {
		if (end()!=STATE_TYPE_ARRAY) {
			throw new IllegalStateException();
		}
		return this;
	}
	
	public final JsonBuilder endAll() {
		while (!states.isEmpty()) {
			end();
		}
		return this;
	}
}
