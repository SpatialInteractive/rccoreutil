package net.rcode.core.redis;

/**
 * Since Redis connections are capable of multiplexing lots of traffic
 * over single connections, connection pools, per se, are not the correct
 * construct.
 * <p>
 * A redis connection can be in streaming or subscribed mode.  A single
 * connection in each mode should suffice and this class manages the
 * shared connection.  It also has a method for obtaining new connections.
 * <p>
 * TODO: If the connections quit, bad things happen.
 * 
 * @author stella
 *
 */
public class RedisConnections {
	private RedisManager manager;
	private String host;
	private int port;
	
	// - Cached connections
	private Redis streamingConnection;
	private Redis subscribedConnection;
	
	public RedisConnections(RedisManager manager, String host, int port) {
		this.manager=manager;
		this.host=host;
		this.port=port;
	}
	
	/**
	 * Connections to localhost
	 * @param manager
	 */
	public RedisConnections(RedisManager manager) {
		this(manager, "localhost", RedisManager.DEFAULT_PORT);
	}
	
	public RedisManager getManager() {
		return manager;
	}
	
	public synchronized Redis getStreamingConnection() {
		if (streamingConnection==null) {
			streamingConnection=manager.connect(host,port);
		}
		return streamingConnection;
	}
	public synchronized Redis getSubscribedConnection() {
		if (subscribedConnection==null) {
			subscribedConnection=manager.connect(host,port);
		}
		return subscribedConnection;
	}
}
