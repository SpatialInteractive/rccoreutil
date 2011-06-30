package net.rcode.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.rcode.core.async.Flow;
import net.rcode.core.async.Promise;
import net.rcode.core.io.NamedThreadFactory;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces keys from a system random number source.  Keys are encoded
 * with "url-safe" base64 encoding.
 * <p>
 * This class manages a single worker thread for doing its work.
 * 
 * @author stella
 *
 */
public class SecureKeyGenerator {
	protected static final Logger logger=LoggerFactory.getLogger(SecureKeyGenerator.class);
	protected static final ExecutorService WORKER=Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("SecureKeyGenerator"));
	protected static final SecureKeyGenerator DEFAULT_INSTANCE=new SecureKeyGenerator();
	
	public static SecureKeyGenerator getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}
	
	interface Generator {
		public void generate(int bytes, Promise<String> result);
	}
	
	private static class FileGenerator implements Generator {
		FileInputStream input;
		
		public FileGenerator(File file) throws FileNotFoundException {
			this.input=new FileInputStream(file);
		}
		
		@Override
		public void generate(int bytes, Promise<String> result) {
			try {
				byte[] buffer=new byte[bytes];
				int length=0;
				while (length<buffer.length) {
					int r=input.read(buffer, length, buffer.length-length);
					if (r<0) {
						throw new RuntimeException("EOF reading random file");
					}
					length+=r;
				}
				
				String key=encodeBytes(buffer);
				result.resolve(key);
			} catch (Throwable t) {
				result.resolveError(t);
			}
		}
	}
	
	private static class SecureRandomGenerator implements Generator {
		SecureRandom random=new SecureRandom();

		@Override
		public void generate(int bytes, Promise<String> result) {
			try {
				byte[] buffer=new byte[bytes];
				random.nextBytes(buffer);
				result.resolve(encodeBytes(buffer));
			} catch (Throwable t) {
				result.resolveError(t);
			}
		}
	}
	
	protected Generator generator;
	
	public SecureKeyGenerator() {
		// Select a generator
		try {
			generator=new FileGenerator(new File("/dev/urandom"));
			logger.info("SecureKeyGenerator using key material from /dev/urandom");
		} catch (IOException e) { }
		
		if (generator==null) {
			try {
				generator=new FileGenerator(new File("/dev/random"));
				logger.info("SecureKeyGenerator using key material from /dev/random");
			} catch (IOException e) { }
		}
		
		if (generator==null) {
			generator=new SecureRandomGenerator();
			logger.info("SecureKeyGenerator using key material from SecureRandom");
		}
	}

	public Promise<String> generate(final int bytes) {
		final Promise<String> result=new Promise<String>();
		WORKER.execute(new Runnable() {
			@Override
			public void run() {
				generator.generate(bytes, result);
			}
		});
		return result;
	}
	
	public Flow.Initiator<String> generateStep(final int bytes) {
		return new Flow.Initiator<String>() {
			@Override
			public Promise<String> run() throws Throwable {
				return generate(bytes);
			}
			@Override
			public String toString() {
				return "Generate Random Key";
			}
		};
	}
	
	static String encodeBytes(byte[] buffer) {
		return Base64.encodeBase64URLSafeString(buffer);
	}
}
