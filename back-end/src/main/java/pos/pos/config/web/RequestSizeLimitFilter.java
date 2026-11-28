package pos.pos.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Caps request bodies that aren't file uploads (JSON, forms) so a huge body can't tie up memory. File uploads are
 * capped by spring.servlet.multipart instead.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    public static final long DEFAULT_LIMIT = 1_048_576;

    private final long limit;

    public RequestSizeLimitFilter(long limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            reject(response);
            return;
        }
        chain.doFilter(declared >= 0 ? request : new LimitedRequest(request, limit), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":413,\"message\":\"The request is too large\"}");
    }

    /** Thrown while reading a body without a declared length once it passes the limit. */
    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException() {
            super("The request is too large");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream delegate = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int value = delegate.read();
                        if (value >= 0 && ++read > limit) {
                            throw new PayloadTooLargeException();
                        }
                        return value;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = delegate.read(buffer, offset, length);
                        if (count > 0) {
                            read += count;
                            if (read > limit) {
                                throw new PayloadTooLargeException();
                            }
                        }
                        return count;
                    }

                    @Override
                    public boolean isFinished() {
                        return delegate.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        delegate.setReadListener(listener);
                    }
                };
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
