package net.rcode.core.redis;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Encapsulates a redis command ready to be queued.  
 * @author stella
 *
 */
public class RedisCommand {
	public String command;
	private ByteBuffer buffer;
	
	/**
	 * Create a new RedisCommand.  It is important to give ownership of the buffer
	 * to this object and not use it after.
	 * @param command
	 * @param buffer
	 */
	public static RedisCommand fromBuffer(String command, ByteBuffer buffer) {
		RedisCommand ret=new RedisCommand();
		ret.command=command;
		ret.buffer=buffer;
		buffer.rewind();
		return ret;
	}
	
	private RedisCommand() { }
	
	public RedisCommand(String command, Object... arguments) {
		command=command.toUpperCase();
		this.command=command;
		this.buffer=RedisSerializer.INSTANCE.serialize(command, arguments);
	}
	
	public RedisCommand(String command, List<?> arguments) {
		this(command, arguments.toArray());
	}
	
	/**
	 * @return The upper-cased command
	 */
	public String getCommand() {
		return command;
	}
	
	/**
	 * @return freshly sliced buffer
	 */
	public ByteBuffer getBuffer() {
		return buffer.slice();
	}
	
	@Override
	public String toString() {
		return command;
	}
}
