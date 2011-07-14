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

package com.janrain.servlet;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Logs the processing time for each request.
 *
 * @author Jason Cowley
 */
public class ProcessTimeLoggingFilter implements Filter {

	private static final Logger logger = Logger.getLogger(ProcessTimeLoggingFilter.class);

	@Override
    public void init(FilterConfig filterConfig) throws ServletException {}

	@Override
    public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {

		HttpServletRequest servletRequest = (HttpServletRequest) request;
		if (servletRequest.getRequestURL().indexOf("http://localhost") != 0) {

			long start = System.currentTimeMillis();
			chain.doFilter(request, response);
			long stop = System.currentTimeMillis();

			StringBuilder output = new StringBuilder();
            output.append("Time [ ").append(stop - start).append(" ms ]");
            output.append(" Request [").append(servletRequest.getRequestURL());
			if (servletRequest.getQueryString() != null) {
                output.append("?").append(servletRequest.getQueryString());
			}
			output.append("]");
            output.append(" Remote Addr [ ").append(servletRequest.getRemoteAddr()).append(" ] ");

            String referer = servletRequest.getHeader("referer");
            if (StringUtils.isNotBlank(referer)) {
                output.append(" Referer [ ").append(referer).append(" ] ");
            }

            String userAgent = servletRequest.getHeader("user-agent");
            if (StringUtils.isNotBlank(userAgent)) {
                output.append(" [ ").append(userAgent).append(" ] ");
            }
            
			logger.debug(output);
		}
		else {
			chain.doFilter(request, response);
		}

	}

	@Override
    public void destroy() {}
}
