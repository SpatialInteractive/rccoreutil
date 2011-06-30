package net.rcode.core.redis;

import net.rcode.core.async.Flow;

public class RedisSteps {
	public static final Flow.Consumer<RedisResult> FAIL_ON_ERROR=new Flow.Consumer<RedisResult>() {
		@Override
		public void run(RedisResult result) throws Throwable {
			result.ifError();
		}
	};

}
