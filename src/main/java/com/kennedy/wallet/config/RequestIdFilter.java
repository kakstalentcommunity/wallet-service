package com.kennedy.wallet.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.UUID;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 101)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 1_048_576;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader("X-Request-Id");
        if (id == null || !id.matches("[A-Za-z0-9-]{1,64}")) id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-Id", id);
        try {
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body exceeds the allowed size");
                return;
            }
            byte[] body = readBounded(request.getInputStream());
            if (body.length > 0) {
                String type = request.getContentType();
                if (type == null || !(type.toLowerCase().startsWith("application/json")
                        || type.toLowerCase().startsWith("application/x-www-form-urlencoded"))) {
                    reject(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Request body must be JSON");
                    return;
                }
            }
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } catch (BodyTooLargeException ex) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body exceeds the allowed size");
        } finally {
            MDC.remove("requestId");
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }

    private byte[] readBounded(InputStream source) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (output.size() + count > MAX_BODY_BYTES) throw new BodyTooLargeException();
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static final class BodyTooLargeException extends IOException {}

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async request reading is not supported");
                }
            };
        }
        @Override public BufferedReader getReader() {
            Charset charset = Charset.forName(getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
