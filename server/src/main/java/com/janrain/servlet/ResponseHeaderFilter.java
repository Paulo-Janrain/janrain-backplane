package com.janrain.servlet;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

/**
 * A filter for adding headers to HTTP responses, e.g. Cache-Control, P3P.
 */
public class ResponseHeaderFilter implements Filter {

	FilterConfig fc;

	@Override
    public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {

		HttpServletResponse response = (HttpServletResponse) res;

		// set the provided HTTP response parameters
		for (Enumeration<?> e = fc.getInitParameterNames(); e.hasMoreElements();) {
			String headerName = (String)e.nextElement();
			response.addHeader(headerName, fc.getInitParameter(headerName));
		}

		// pass the request/response on
		chain.doFilter(req, response);
	}

	@Override
    public void init(FilterConfig filterConfig) {
		this.fc = filterConfig;
	}

	@Override
    public void destroy() {
        //noinspection AssignmentToNull
        this.fc = null;
	}
}
