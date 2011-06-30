package net.rcode.core.redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;

public class RedisConversions {
	/**
	 * Convert a RedisResult to a Boolean true if the integral result!=0.
	 */
	public static final Promise.Chain<RedisResult,Boolean> INTEGER_RESULT_TO_BOOLEAN=new Promise.Chain<RedisResult,Boolean>() {
		@Override
		public Boolean chain(RedisResult result) throws Throwable {
			result.ifError();
			return result.getInteger()!=0;
		}
	};
	
	/**
	 * Convert a Redis MultiBulk result to a collection of strings.  If the result is null, an empty collection
	 * is returned.
	 */
	public static final Promise.Chain<RedisResult, Collection<String>> MULTI_BULK_RESULT_TO_STRINGS=new Promise.Chain<RedisResult, Collection<String>>() {
		@Override
		public Collection<String> chain(RedisResult input) throws Throwable {
			input.ifError();
			if (input.isNull()) return Collections.emptyList();
			RedisMultiResult multi=input.getAsMulti();
			ArrayList<String> ret=new ArrayList<String>(multi.getCount());
			for (int i=0; i<multi.getCount(); i++) {
				ret.add(multi.get(i).getString());
			}
			return ret;
		}
	};
	
	public static final Promise.Chain<RedisResult, Integer> INTEGER_RESULT_TO_INTEGER=new Promise.Chain<RedisResult,Integer>() {
		@Override
		public Integer chain(RedisResult input) throws Throwable {
			input.ifError();
			return input.getInteger();
		}
	};
	
	/**
	 * Just does error checking on the result and evaluates to void
	 */
	public static final Promise.Chain<RedisResult,Void> RESULT_TO_VOID=new Promise.Chain<RedisResult,Void>() {
		@Override
		public Void chain(RedisResult input) throws Throwable {
			input.ifError();
			return null;
		}
		
	};

	public static final Promise.Chain<RedisResult,String> BULK_TO_STRING=new Promise.Chain<RedisResult, String>() {
		@Override
		public String chain(RedisResult input) throws Throwable {
			input.ifError();
			return input.getString();
		}
	};

	/**
	 * Convert an interleaved Multi Bulk reply to map of strings
	 */
	public static final Promise.Chain<RedisResult,Map<String,String>> INTERLEAVED_MULTIBULK_TO_MAP = new Promise.Chain<RedisResult, Map<String,String>>() {
		@Override
		public Map<String, String> chain(RedisResult input) throws Throwable {
			input.ifError();
			RedisMultiResult mb=input.getAsMulti();
			HashMap<String,String> ret=new HashMap<String, String>(mb.getCount()*2);
			for (int i=0; i<mb.getCount(); i+=2) {
				ret.put(mb.get(i).getString(), mb.get(i+1).getString());
			}
			return ret;
		}
	};
	
}
