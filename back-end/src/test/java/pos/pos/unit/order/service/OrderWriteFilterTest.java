package pos.pos.unit.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pos.pos.order.web.OrderWriteFilter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class OrderWriteFilterTest {
    private MockHttpServletRequest request(String body) {
        var req = new MockHttpServletRequest("POST","/restaurants/12345678-1234-1234-1234-123456789012/branches/branch/orders");
        req.setUserPrincipal(() -> "waiter-1"); req.addHeader("Idempotency-Key","12345678-1234-1234-1234-123456789012"); req.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return req;
    }
    @Test void replaysSameRequestAndRejectsKeyReuseForDifferentBody() throws Exception {
        var jdbc = mock(JdbcTemplate.class); var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        var stored = new ArrayList<Map<String,Object>>();
        when(jdbc.queryForList(anyString(),anyString(),anyString())).thenAnswer(i -> stored);
        when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any())).thenAnswer(i -> {
            var row = new HashMap<String,Object>(); row.put("fingerprint",i.getArgument(3)); row.put("status",i.getArgument(4)); row.put("content_type",i.getArgument(5)); row.put("body",i.getArgument(6)); stored.add(row); return 1;
        });
        var filter = new OrderWriteFilter(jdbc,manager,mock(pos.pos.restaurant.service.RestaurantScopeService.class)); var writes = new AtomicInteger();
        jakarta.servlet.FilterChain chain = (req,res) -> { writes.incrementAndGet(); var response = (jakarta.servlet.http.HttpServletResponse)res; response.setStatus(201); response.setContentType("application/json"); response.getWriter().write("{\"id\":\"created-order\"}"); };
        var first = new MockHttpServletResponse(); filter.doFilter(request("{}"),first,chain);
        var retry = new MockHttpServletResponse(); filter.doFilter(request("{}"),retry,chain);
        assertThat(first.getStatus()).isEqualTo(201); assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString()); assertThat(retry.getHeader("Idempotency-Replayed")).isEqualTo("true"); assertThat(writes).hasValue(1);
        var conflict = new MockHttpServletResponse(); filter.doFilter(request("{\"tableId\":\"different\"}"),conflict,chain);
        assertThat(conflict.getStatus()).isEqualTo(409); assertThat(writes).hasValue(1);
    }
    @Test void rollsBackFailedResponsesWithoutRememberingThem() throws Exception {
        var jdbc = mock(JdbcTemplate.class); var manager = mock(PlatformTransactionManager.class); var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status); when(jdbc.queryForList(anyString(),anyString(),anyString())).thenReturn(List.of());
        var filter = new OrderWriteFilter(jdbc,manager,mock(pos.pos.restaurant.service.RestaurantScopeService.class)); var response = new MockHttpServletResponse();
        filter.doFilter(request("{}"),response,(req,res) -> ((jakarta.servlet.http.HttpServletResponse)res).setStatus(400));
        assertThat(status.isRollbackOnly()).isTrue(); assertThat(response.getStatus()).isEqualTo(400);
        verify(jdbc,never()).update(anyString(),any(),any(),any(),any(),any(),any());
    }
}
