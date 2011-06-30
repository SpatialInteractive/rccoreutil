package net.rcode.core.web;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.Executor;

import net.rcode.assetserver.core.MimeMapping;
import net.rcode.assetserver.util.NamePattern;
import net.rcode.assetserver.util.PathUtil;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * Designed to handle serve a directory of files under an http uri.  Since filesystem
 * access is typically blocking, this handler does processing within worker threads.
 * <p>
 * 
 * 
 * @author stella
 *
 */
public class FilesRequestHandler extends ThreadedRequestHandler {
	private static final String[] FORBIDDEN_NAMES_STRINGS=new String[] {
		".",
		"..",
		"*~",
		"#*#",
		".#*",
		"%*%",
		"._*",
		"CVS",
		".cvsignore",
		"SCCS",
		"vssver.scc",
		".svn",
		".DS_Store",
		".git",
		".gitattributes",
		".gitignore",
		".gitmodules",
		".hg",
		".hgignore",
		".hgsub",
		".hgsubstate",
		".hgtags",
		".bzr",
		".bzrignore"		
	};
	private static final NamePattern FORBIDDEN_NAMES=new NamePattern(FORBIDDEN_NAMES_STRINGS);
	private static final String[] INDEX_NAMES=new String[] {
		"index.html"
	};
	private static final String DEFAULT_CHARSET="UTF-8";
	
	private File docRoot;
	private MimeMapping mimeMapping;

	public FilesRequestHandler(Executor executor, MimeMapping mimeMapping, File docRoot) {
		super(executor);
		this.docRoot=docRoot;
		this.mimeMapping=mimeMapping;
		if (this.mimeMapping==null) {
			this.mimeMapping=new MimeMapping();
			this.mimeMapping.loadDefaults();
		}
	}

	@Override
	protected void handleInThread() throws Exception {
		String uri=request.getUri();
		List<String> components=PathUtil.normalizePathComponents("", uri);
		StringBuilder path=new StringBuilder(uri.length()+20);
		boolean valid=true;
		for (String comp: components) {
			comp=urlDecode(comp);
			if (FORBIDDEN_NAMES.matches(comp) || comp.indexOf('/')>=0 || comp.indexOf('\\')>=0) {
				valid=false;
				break;
			}
			
			if (path.length()>0) path.append(File.separatorChar);
			path.append(comp);
		}
		
		if (!valid) {
			respondError(HttpResponseStatus.FORBIDDEN.getCode(), "Access to the requested resource is not permitted");
			return;
		}
		
		// Handle file or directory
		File targetFile=new File(docRoot, path.toString());
		if (targetFile.isFile()) {
			respondFile(targetFile);
			return;
		} else if (targetFile.isDirectory()) {
			for (String checkName: INDEX_NAMES) {
				File checkFile=new File(targetFile, checkName);
				if (checkFile.isFile()) {
					respondFile(checkFile);
					return;
				}
			}
		}
		
		// Not found
		respondError(HttpResponseStatus.NOT_FOUND.getCode(), "Resource not found");
		return;
	}

	protected void respondFile(File targetFile) throws Exception {
		FileContentHandler fch=selectHandler(targetFile);
		
		HttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		String etag=fch.getEtag();
		
		// Does etag match?
		if (etag!=null) {
			String ifNoneMatch=request.getHeader(HttpHeaders.Names.IF_NONE_MATCH);
			if (ifNoneMatch!=null && etag.equals(ifNoneMatch)) {
				HttpResponse notModifiedResponse=new DefaultHttpResponse(HttpVersion.HTTP_1_1, 
						HttpResponseStatus.NOT_MODIFIED);
				respond(notModifiedResponse);
				return;
			} else {
				response.addHeader(HttpHeaders.Names.ETAG, etag);
			}
		}
		
		// Set up headers
		String contentType=fch.contentType();
		if (contentType==null) {
			// Default based on mime match
			contentType=mimeMapping.lookup(targetFile.getName());
			if (contentType!=null && mimeMapping.isTextualMimeType(contentType)) {
				contentType+=";charset=" + DEFAULT_CHARSET;
			}
		}
		if (contentType!=null) {
			response.addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
		}
		
		// Respond
		ChannelBuffer content=fch.getContent();
		if (content!=null) {
			HttpHeaders.setContentLength(response, content.readableBytes());
			if (request.getMethod()!=HttpMethod.HEAD) {
				response.setContent(content);
			}
			respond(response);
		} else {
			respondError(HttpResponseStatus.NO_CONTENT.getCode(), "No content");
		}
	}

	private FileContentHandler selectHandler(File targetFile) {
		return new StaticFileContentHandler(targetFile);
	}

	private String urlDecode(String comp) {
		try {
			return URLDecoder.decode(comp, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error(e);
		}
	}
}
