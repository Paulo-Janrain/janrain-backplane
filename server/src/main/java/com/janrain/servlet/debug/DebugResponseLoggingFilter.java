package com.janrain.servlet.debug;

import org.apache.log4j.Logger;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * FOR DEBUGGING ONLY!!
 *
 * Logs the entire response sent to the client.
 *
 * From http://www.java-forums.org/java-servlet/20631-how-get-content-httpservletresponse.html
 */
public class DebugResponseLoggingFilter implements Filter {

	private static final Logger logger = Logger.getLogger(DebugResponseLoggingFilter.class);
	private static final String LINE = "---------------------------------------------------------------------------------\n";

	/**
	 * @see Filter#destroy()
	 */
	@Override
    public void destroy() {}

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	@Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest servletRequest = (HttpServletRequest) request;
		if (servletRequest.getRequestURL().indexOf("http://localhost") != 0) {
			OutputStreamResponseWrapper wrappedResponse = new OutputStreamResponseWrapper((HttpServletResponse) response, ByteArrayOutputStream.class);  
	        chain.doFilter(request, wrappedResponse);
	        ByteArrayOutputStream baos = (ByteArrayOutputStream) wrappedResponse.getRealOutputStream();

	        String output = baos.toString();

            StringBuilder meta = new StringBuilder();
            meta.append("Status: ").append(wrappedResponse.getStatus());
            for(Map.Entry<String, List<String>> headerEntry: wrappedResponse.getHeaders().entrySet()) {
                for(String value : headerEntry.getValue()) {
                    meta.append("\n").append(headerEntry.getKey()).append(": ").append(value);
                }
            }
            for(Cookie cookie : wrappedResponse.getCookies()) {
                meta.append("\nCookie: ").append(cookie.getName()).append("=").append(cookie.getValue());
            }

			logger.debug("Response for request [" + ((HttpServletRequest)request).getRequestURL() + "]\n"
					+ LINE
                    + meta.toString() + "\n"
					+ LINE
					+ output + "\n"
					+ LINE);
			response.getWriter().write(output);
		}
		else {
	        chain.doFilter(request, response);
		}
	}

	/**
	 * @see Filter#init(FilterConfig)
	 */
	@Override
    public void init(FilterConfig fConfig) throws ServletException {}

}
