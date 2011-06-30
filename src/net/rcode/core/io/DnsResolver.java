package net.rcode.core.io;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import net.rcode.core.async.Promise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide asynchronous Dns resolution services
 * @author stella
 *
 */
public class DnsResolver {
	public static final long DEFAULT_POSITIVE_TTL=60000;
	public static final long DEFAULT_NEGATIVE_TTL=10000;
	
	private static final Logger logger=LoggerFactory.getLogger(DnsResolver.class);
	
	private ExecutorService worker;
	private ConcurrentHashMap<String, Record> cache=new ConcurrentHashMap<String, Record>();
	
	private class Record implements Runnable {
		public String hostname;
		public Promise<InetAddress> address;
		public long created;
		public volatile boolean refreshRequested;
		public Record refreshing;
		
		public Record(String hostname) {
			this.hostname=hostname;
			this.address=new Promise<InetAddress>();
			this.created=System.currentTimeMillis();
		}
		
		@Override
		public void run() {
			long time=System.currentTimeMillis();
			if (logger.isDebugEnabled()) {
				logger.debug("Resolving dns for " + hostname);
			}
			
			try {
				InetAddress ia=InetAddress.getByName(hostname);
				if (logger.isInfoEnabled()) {
					logger.info("Resolved dns " + hostname + " -> " + ia + " (" + (System.currentTimeMillis()-time) + "ms)");
				}
				if (refreshing!=null) {
					refreshing=null;
					cache.put(hostname, this);
				}
				address.resolve(ia);
			} catch (Exception e) {
				logger.warn("Failed dns resolution " + hostname + " (" + (System.currentTimeMillis()-time) + "ms)", e);
				if (refreshing!=null) {
					refreshing.refreshRequested=false;
				}
				address.resolveError(e);
			}
		}
	}
	
	public static final DnsResolver DEFAULT=new DnsResolver(null);
	
	public DnsResolver(ExecutorService worker) {
		if (worker==null) {
			worker=createDefaultWorker();
		}
		this.worker=worker;
	}
	
	protected ExecutorService createDefaultWorker() {
		return Executors.newCachedThreadPool(new NamedThreadFactory("DnsResolver"));
	}
	
	public Promise<InetAddress> lookup(String hostname) {
		InetAddress addr=lookupImmediate(hostname);
		if (addr!=null) {
			return Promise.fixed(addr);
		}
		
		Record existing=cache.get(hostname);
		if (existing==null) {
			existing=new Record(hostname);
			cache.put(hostname, existing);
			worker.execute(existing);
		}
		
		validate(existing);
		return existing.address;
	}

	private void validate(Record record) {
		if (record.address.isResolved() && !record.refreshRequested) {
			long now=System.currentTimeMillis();
			if ((record.address.isError() && (record.created+DEFAULT_NEGATIVE_TTL)<now) ||
					(record.created+DEFAULT_POSITIVE_TTL)<now) {
				if (logger.isDebugEnabled()) logger.debug("Refreshing stale dns record for " + record.hostname);
				Record refresh=new Record(record.hostname);
				refresh.refreshRequested=true;
				refresh.refreshing=record;
				worker.execute(refresh);
			}
		}
	}
	
	private static final Pattern STATIC_PATTERN=Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.");
	/**
	 * Resolve some well know names that will not block
	 * @param hostname
	 * @return
	 */
	private InetAddress lookupImmediate(String hostname) {
		if (STATIC_PATTERN.matcher(hostname).matches()) {
			try {
				return InetAddress.getByName(hostname);
			} catch (UnknownHostException e) {
				throw new RuntimeException("Error statically resolving dns for " + hostname, e);
			}
		}
		return null;
	}
}
