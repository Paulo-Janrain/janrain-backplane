package com.janrain.servlet;

import org.apache.log4j.Logger;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;

import javax.servlet.http.HttpServletRequest;

/**
 * A Spring MVC Exception resolver that actually logs the exceptions
 *
 * @author Jason Cowley
 */
public class LoggingMappingExceptionResolver extends
		SimpleMappingExceptionResolver {

	Logger logger = Logger.getLogger(LoggingMappingExceptionResolver.class);

	@Override
	protected void logException(Exception ex, HttpServletRequest request) {
		super.logException(ex, request);
		logger.error("Error processing request: " + request.getRequestURL(), ex);
	}
}
