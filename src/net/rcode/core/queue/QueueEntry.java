package net.rcode.core.queue;

import java.util.Map;

/**
 * Represents an entry on a queue
 * @author stella
 *
 */
public class QueueEntry {
	public String id;
	public Map<String, String> properties;
	
	public QueueEntry(String id, Map<String,String> properties) {
		this.id=id;
		this.properties=properties;
	}
}
