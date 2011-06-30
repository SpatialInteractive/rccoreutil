package net.rcode.core.httpserver.eventsource;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * A frame of data to be sent to an EventSource client
 * @author stella
 *
 */
public class EventSourceFrame {
	private ChannelBuffer buffer;
	private Writer out;
	private boolean finished;
	
	public EventSourceFrame(int estimatedSize) {
		buffer=ChannelBuffers.dynamicBuffer(estimatedSize);
		out=new OutputStreamWriter(new ChannelBufferOutputStream(buffer),
				CharsetUtil.UTF_8);
	}
	
	public ChannelBuffer getBuffer() {
		return buffer;
	}
	
	public ChannelBuffer finish() {
		if (!finished) {
			try {
				out.write('\n');
				out.flush();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			finished=true;
		}
		return buffer;
	}
	
	public void addComment(CharSequence value) throws IOException {
		if (finished) throw new IllegalStateException();
		assertValue(value);
		try {
			out.write(':');
			out.append(value);
			out.write('\n');
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public void addField(CharSequence name, CharSequence value) {
		if (finished) throw new IllegalStateException();
		assertName(name);
		assertValue(value);
		try {
			out.append(name);
			out.write(':');
			out.append(value);
			out.write('\n');
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void assertName(CharSequence value) {
		for (int i=0; i<value.length(); i++) {
			char c=value.charAt(i);
			if (c=='\n' || c=='\r' || c==':') {
				throw new IllegalArgumentException("EventSource value cannot contain special characters");
			}
		}
	}

	private void assertValue(CharSequence value) {
		for (int i=0; i<value.length(); i++) {
			char c=value.charAt(i);
			if (c=='\n' || c=='\r') {
				throw new IllegalArgumentException("EventSource value cannot contain line break characters");
			}
		}
	}
}
