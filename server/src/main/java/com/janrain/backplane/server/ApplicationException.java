/*
 * Copyright 2011 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
