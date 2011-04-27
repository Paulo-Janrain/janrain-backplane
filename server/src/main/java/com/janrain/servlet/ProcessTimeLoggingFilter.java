package com.janrain.servlet;

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

			logger.debug(output);
		}
		else {
			chain.doFilter(request, response);
		}

	}

	@Override
    public void destroy() {}
}
