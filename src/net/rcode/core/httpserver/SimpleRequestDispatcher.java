package net.rcode.core.httpserver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Simple dispatcher that contains a list of rules.  First rule to match
 * causes instantiation of a corresponding handler.
 * 
 * @author stella
 *
 */
public class SimpleRequestDispatcher implements HttpRequestDispatcher {
	private static class Rule {
		public Predicate predicate;
		public HttpRequestDispatcher subDispatcher;
		public Factory factory;
	}
	
	public static interface Predicate {
		public HttpRequest matches(HttpRequest request);
	}
	
	public static interface Factory {
		public HttpRequestHandler create() throws Exception;
	}
	
	public static class ClassFactory implements Factory {
		private final Class<? extends HttpRequestHandler> clazz;
		public ClassFactory(Class<? extends HttpRequestHandler> clazz) {
			this.clazz=clazz;
		}
		@Override
		public HttpRequestHandler create() throws Exception {
			return clazz.newInstance();
		}
	}
	
	public static class PrototypeFactory implements Factory {
		private final HttpRequestHandler prototype;
		public PrototypeFactory(HttpRequestHandler prototype) {
			this.prototype=prototype;
			// Try a clone
			try {
				prototype.clone();
			} catch (Throwable t) {
				throw new RuntimeException("Error cloning handler", t);
			}
		}
		@Override
		public HttpRequestHandler create() throws Exception {
			return prototype.clone();
		}
	}
	
	public static class PathPredicate implements Predicate {
		private String path;

		public PathPredicate(String path) {
			this.path=path;
		}
		
		@Override
		public HttpRequest matches(HttpRequest request) {
			String path=extractPath(request);
			if (this.path.equals(path)) return request;
			else return null;
		}
	}
	
	public static class PathPrefixPredicate implements Predicate {
		private String pathPrefix;
		private boolean stripPrefix;
		public PathPrefixPredicate(String pathPrefix, boolean stripPrefix) {
			this.pathPrefix = pathPrefix;
			this.stripPrefix = stripPrefix;
		}
		
		@Override
		public HttpRequest matches(HttpRequest request) {
			String uri=request.getUri();
			if (uri.startsWith(pathPrefix)) {
				if (pathPrefix.length()==uri.length()) {
					// Exact match
					if (stripPrefix) {
						return new RewriteHttpRequest(request, "/");
					}
					return request;
				} else {
					char next=uri.charAt(pathPrefix.length());
					if (next=='/' || next=='?') {
						if (stripPrefix) {
							return new RewriteHttpRequest(request, uri.substring(pathPrefix.length()));
						} else {
							return request;
						}
					} else {
						// Not a match
						return null;
					}
				}
			} else {
				return null;
			}
		}
	}
	
	public static class RewritePredicate implements Predicate {
		private Pattern matchPattern;
		private String replacePattern;

		public RewritePredicate(Pattern matchPattern, String replacePattern) {
			this.matchPattern = matchPattern;
			this.replacePattern = replacePattern;
		}
		
		@Override
		public HttpRequest matches(HttpRequest request) {
			Matcher m=matchPattern.matcher(request.getUri());
			StringBuffer sb=null;
			while (m.find()) {
				if (sb==null) sb=new StringBuffer(request.getUri().length()*2);
				m.appendReplacement(sb, replacePattern);
			}
			if (sb!=null) {
				m.appendTail(sb);
				return new RewriteHttpRequest(request, sb.toString());
			} else {
				// No match
				return null;
			}
		}
	}
	
	public static class StaticRewritePredicate implements Predicate {
		private String match;
		private String replace;
		public StaticRewritePredicate(String match, String replace) {
			this.match = match;
			this.replace = replace;
		}
		
		@Override
		public HttpRequest matches(HttpRequest request) {
			if (request.getUri().equals(match)) {
				return new RewriteHttpRequest(request, replace);
			} else {
				return null;
			}
		}
	}
	
	private List<Rule> rules=new ArrayList<Rule>();
	
	@Override
	public HttpDispatch dispatch(HttpRequest request) throws Exception {
		for (Rule rule: rules) {
			HttpRequest stepRequest;
			if (rule.predicate==null) stepRequest=request;
			else stepRequest=rule.predicate.matches(request);
			
			if (stepRequest!=null) {
				if (rule.subDispatcher!=null) {
					return rule.subDispatcher.dispatch(stepRequest);
				} else if (rule.factory!=null) {
					return new HttpDispatch(stepRequest, rule.factory.create());
				} else {
					// Preserve the request (support downstream rewriting)
					request=stepRequest;
				}
			}
		}
		
		return null;
	}

	static String extractPath(HttpRequest request) {
		String uri=request.getUri();
		int qpos=uri.indexOf('?');
		if (qpos<0) return uri;
		else return uri.substring(0, qpos);
	}

	public void add(Predicate predicate, Object handler) {
		Rule rule=new Rule();
		rule.predicate=predicate;
		rule.factory=factoryFrom(handler);
		rules.add(rule);
	}
	
	@SuppressWarnings("unchecked")
	private Factory factoryFrom(Object handler) {
		if (handler instanceof HttpRequestHandler) {
			return new PrototypeFactory((HttpRequestHandler) handler);
		} else if (handler instanceof Class) {
			Class clazz=(Class) handler;
			if (HttpRequestHandler.class.isAssignableFrom(clazz)) {
				return new ClassFactory(clazz);
			}
		}
		
		throw new IllegalArgumentException("Object " + handler + " is not a valid dispatcher factory");
	}

	public void path(String path, Object handler) {
		add(new PathPredicate(path), handler);
	}

	public void pathPrefix(String prefix, boolean stripPrefix, Object handler) {
		add(new PathPrefixPredicate(prefix, stripPrefix), handler);
	}
	
	public void rewrite(String matchPattern, String replacePattern) {
		Rule rule=new Rule();
		rule.predicate=new RewritePredicate(Pattern.compile(matchPattern), replacePattern);
		rules.add(rule);
	}
	
	public void rewriteStatic(String match, String replace) {
		Rule rule=new Rule();
		rule.predicate=new StaticRewritePredicate(match, replace);
		rules.add(rule);
	}
}
