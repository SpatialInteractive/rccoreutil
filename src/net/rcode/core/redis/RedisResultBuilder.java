package net.rcode.core.redis;

import java.nio.ByteBuffer;

/**
 * A RedisParser.Events class that constructs RedisResult instances from
 * the event stream.  Override the handleResult method to get them.
 * 
 * @author stella
 *
 */
public abstract class RedisResultBuilder implements RedisParser.Events {
	private RedisResult[] pendingMulti;
	private int pendingMultiIndex;

	protected abstract void handleResult(RedisResult result);
	
	@Override
	public final void handleStartMultiBulk(int count) {
		if (pendingMulti!=null) {
			throw new IllegalStateException("Cannot nest multi responses");
		}
		pendingMultiIndex=0;
		if (count>=0) {
			pendingMulti=new RedisResult[count];
		}
	}
	@Override
	public final void handleEndMultiBulk() {
		// It may be nil multi 
		if (pendingMulti!=null) {
			handleResult(new RedisMultiResult(pendingMulti));
			pendingMulti=null;
		} else {
			handleResult(new RedisMultiResult());
		}
	}

	private void addResult(RedisResult result) {
		if (pendingMulti!=null) {
			pendingMulti[pendingMultiIndex++]=result;
		} else {
			handleResult(result);
		}
	}
	
	private final ByteBuffer dupBuffer(ByteBuffer contents) {
		if (contents==null) return null;
		ByteBuffer dup=ByteBuffer.allocate(contents.remaining());
		dup.put(contents);
		dup.flip();
		return dup;
	}
	
	@Override
	public final void handleBulk(ByteBuffer contents) {
		addResult(new RedisPrimitiveResult(RedisResult.TYPE_BULK, dupBuffer(contents)));
	}

	@Override
	public final void handleError(ByteBuffer contents) {
		addResult(new RedisPrimitiveResult(RedisResult.TYPE_ERROR, dupBuffer(contents)));
	}

	@Override
	public final void handleStatus(ByteBuffer contents) {
		addResult(new RedisPrimitiveResult(RedisResult.TYPE_STATUS, dupBuffer(contents)));
	}

	@Override
	public final void handleInteger(int value) {
		addResult(new RedisIntegerResult(value));
	}
}
