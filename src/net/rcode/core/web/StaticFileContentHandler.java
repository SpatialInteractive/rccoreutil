package net.rcode.core.web;

import java.io.File;
import java.io.FileInputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * File handler plugin for serving static files
 * @author stella
 *
 */
public class StaticFileContentHandler implements FileContentHandler {
	private File file;
	
	public StaticFileContentHandler(File file) {
		this.file=file;
	}
	
	@Override
	public String getEtag() {
		return "f." + file.length() + "." + file.lastModified();	
	}

	@Override
	public String contentType() {
		return null;
	}

	@Override
	public ChannelBuffer getContent() throws Exception {
		ChannelBuffer content=ChannelBuffers.dynamicBuffer((int)file.length());
		FileInputStream in=new FileInputStream(file);
		try {
			byte[] buffer=new byte[4096];
			for (;;) {
				int r=in.read(buffer);
				if (r<0) break;
				content.writeBytes(buffer, 0, r);
			}
		} finally {
			in.close();
		}
		
		return content;
	}

}
