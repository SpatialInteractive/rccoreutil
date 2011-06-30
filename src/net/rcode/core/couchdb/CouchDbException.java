package net.rcode.core.couchdb;

public class CouchDbException extends RuntimeException {
	public CouchDbException(String msg) {
		super(msg);
	}
	public CouchDbException(String msg, Throwable t) {
		super(msg, t);
	}
}
