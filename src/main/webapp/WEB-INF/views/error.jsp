<%@page import="org.apache.log4j.Logger"%>
<%@ include file="/WEB-INF/views/include.jsp" 
%><%@page contentType="text/plain" 
%><c:choose><c:when test="${not empty message}">${message}</c:when>
<c:otherwise>Unable to process request.</c:otherwise></c:choose>
