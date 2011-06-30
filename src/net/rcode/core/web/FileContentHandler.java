package net.rcode.core.web;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Abstraction for accessing a physical file
 * @author stella
 *
 */
public interface FileContentHandler {
	/**
	 * @return etag or null
	 */
	public String getEtag();
	
	/**
	 * @return The content type
	 */
	public String contentType();
	
	/**
	 * If the content can be delivered in one go, then
	 * return it here.  Otherwise null and support chunkContent()
	 * @return content or null
	 * @throws Exception
	 */
	public ChannelBuffer getContent() throws Exception;
}
