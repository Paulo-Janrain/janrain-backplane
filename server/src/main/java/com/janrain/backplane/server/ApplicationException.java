/**
 * 
 */
package com.janrain.backplane.server;

import org.apache.log4j.Logger;

/**
 * A general error that occurred while attempting to process a request.
 * 
 * @author Jason Cowley
 */
public class ApplicationException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ApplicationException.class);
	
	public ApplicationException(String message) {
		super(message);
		logger.error("ERROR: " + message);
	}
	
	public ApplicationException(String message, Throwable error) {
		super(message, error);
		logger.error("ERROR: " + message, error);
	}

}
