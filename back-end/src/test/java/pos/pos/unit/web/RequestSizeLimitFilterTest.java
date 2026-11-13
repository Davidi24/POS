package pos.pos.unit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pos.pos.config.web.RequestSizeLimitFilter;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestSizeLimitFilter")
class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(100);

    @Test
    @DisplayName("refuses a declared body over the limit with 413 before reaching the app")
    void declaredTooLarge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        request.setContentType("application/json");
        request.setContent(new byte[101]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> reached = new AtomicReference<>(false);
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("too large");
        assertThat(reached.get()).isFalse();
    }

    @Test
    @DisplayName("lets a body within the limit through untouched")
    void withinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        request.setContentType("application/json");
        request.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> body = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> body.set(new String(req.getInputStream().readAllBytes())));
        assertThat(body.get()).isEqualTo("{\"a\":1}");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("stops reading a body of unknown length once it passes the limit")
    void chunkedTooLarge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(new byte[500]);
        FilterChain chain = (ServletRequest req, jakarta.servlet.ServletResponse res) -> {
            InputStream input = req.getInputStream();
            assertThatThrownBy(input::readAllBytes).isInstanceOf(RequestSizeLimitFilter.PayloadTooLargeException.class);
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    @DisplayName("leaves file uploads to the multipart limits")
    void multipartSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        request.setContentType("multipart/form-data; boundary=x");
        request.setContent(new byte[5000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> reached = new AtomicReference<>(false);
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(reached.get()).isTrue();
    }
}
