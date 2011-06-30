package net.rcode.core.redis;

import java.util.ArrayList;
import java.util.List;

import net.rcode.core.async.Promise;
import net.rcode.core.util.Pair;

/**
 * A Multi command.
 * @author stella
 *
 */
public class RedisMulti implements RedisExecutor {
	protected List<Pair<RedisCommand, Promise<RedisResult>>> commands=new ArrayList<Pair<RedisCommand,Promise<RedisResult>>>();
	protected Promise<RedisResult> result;
	protected RedisBatchSource source=new RedisBatchSource() {
		@Override
		public void submitBatch(RedisExecutor executor) {
			executor.executeAndForget("MULTI");
			for (int i=0; i<commands.size(); i++) {
				Pair<RedisCommand,Promise<RedisResult>> item=commands.get(i);
				executor.execute(null, item.first);
			}
			executor.execute(result, "EXEC");
		}
	};
	
	public Promise<RedisResult> submit(Redis connection) {
		if (result!=null) {
			throw new IllegalStateException("RedisMulti can only be submitted once");
		}
		result=new Promise<RedisResult>();
		connection.execute(source);
		return result;
	}
	
	@Override
	public Promise<RedisResult> execute(RedisCommand cmd) {
		Promise<RedisResult> result=new Promise<RedisResult>();
		commands.add(new Pair<RedisCommand,Promise<RedisResult>>(cmd, result));
		return result;
	}

	@Override
	public void execute(Promise<RedisResult> result, RedisCommand cmd) {
		commands.add(new Pair<RedisCommand,Promise<RedisResult>>(cmd, result));
	}
	
	@Override
	public void execute(Promise<RedisResult> result, String command,
			Object... arguments) {
		execute(result, new RedisCommand(command, arguments));
	}
	
	@Override
	public Promise<RedisResult> execute(String command, Object... arguments) {
		return execute(new RedisCommand(command, arguments));
	}
	
	@Override
	public void executeAndForget(String command, Object... arguments) {
		RedisCommand cmd=new RedisCommand(command, arguments);
		commands.add(new Pair<RedisCommand,Promise<RedisResult>>(cmd, null));
	}

}
