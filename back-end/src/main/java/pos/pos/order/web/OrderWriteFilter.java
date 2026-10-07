package pos.pos.order.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Atomic request replay and serialization of order writes plus stock-movement retries across instances. */
@RequiredArgsConstructor
public class OrderWriteFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final pos.pos.restaurant.service.RestaurantScopeService restaurantScope;
    private String schema = "public";
    public void setSchema(String schema) { this.schema = schema; }
    private String replayTable() {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalStateException("Invalid database schema");
        return "\"" + schema + "\".write_request_replays";
    }
    private static final Pattern PATH = Pattern.compile(".*/restaurants/([0-9a-fA-F-]{36})/(?:branches/[^/]+/(?:tables/[^/]+/)?orders|orders|inventory/(?:receive|waste|transfer|returns|adjustments))(?:/.*)?");

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
            || !PATH.matcher(request.getRequestURI()).matches() || request.getUserPrincipal() == null;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var match = PATH.matcher(request.getRequestURI());
        if (!match.matches()) { chain.doFilter(request, response); return; }
        String key = request.getHeader("Idempotency-Key");
        if (key == null && requiresIdempotencyKey(request.getMethod(), request.getRequestURI())) {
            response.sendError(400, "Idempotency-Key is required for this action"); return;
        }
        if (key != null && !key.matches("[A-Za-z0-9_-]{16,100}")) {
            response.sendError(400, "Invalid Idempotency-Key"); return;
        }
        byte[] body = request.getInputStream().readNBytes(1_048_577);
        if (body.length > 1_048_576) { response.sendError(413, "Order request is too large"); return; }
        String principalName = request.getUserPrincipal().getName();
        if (request.getUserPrincipal() instanceof org.springframework.security.core.Authentication authentication) {
            try { restaurantScope.requireAccessibleRestaurant(authentication, java.util.UUID.fromString(match.group(1))); }
            catch (pos.pos.exception.auth.AuthException denied) { response.sendError(denied.getStatus().value(), denied.getMessage()); return; }
            // Permission changes invalidate access to earlier replay responses.
            principalName += ":" + digest(authentication.getAuthorities().stream().map(Object::toString).sorted().collect(java.util.stream.Collectors.joining(",")));
        }
        final String owner = digest(principalName);
        String fingerprint = digest(request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString() + "\n" + new String(body, StandardCharsets.UTF_8));
        var cached = new ContentCachingResponseWrapper(response);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        try {
            transaction.executeWithoutResult(status -> {
                // Order writes share a restaurant lock for their table/order invariants. Inventory
                // stock deltas are atomic in SQL, so serialize only identical replay keys there.
                String lockName = isInventoryMovement(request.getMethod(), request.getRequestURI())
                        ? "write-replay:" + owner + ":" + key
                        : "orders:" + match.group(1);
                jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, lockName);
                if (key != null) {
                    var previous = jdbc.queryForList("select fingerprint, status, content_type, body from " + replayTable() + " where owner_id = ? and request_key = ?", owner, key);
                    if (!previous.isEmpty()) {
                        var row = previous.getFirst();
                        try {
                            if (!fingerprint.equals(row.get("fingerprint"))) {
                                cached.setStatus(409); cached.setContentType("application/json");
                                cached.getWriter().write("{\"status\":409,\"message\":\"This request key was already used for a different action\"}");
                            } else {
                                cached.setStatus(((Number)row.get("status")).intValue());
                                cached.setContentType((String)row.get("content_type"));
                                cached.setHeader("Idempotency-Replayed", "true");
                                cached.getOutputStream().write((byte[])row.get("body"));
                            }
                        } catch (IOException e) { throw new UncheckedIOException(e); }
                        return;
                    }
                }
                try { chain.doFilter(new BufferedRequest(request, body), cached); }
                catch (IOException e) { throw new UncheckedIOException(e); }
                catch (ServletException e) { throw new IllegalStateException(e); }
                if (cached.getStatus() >= 400) { status.setRollbackOnly(); return; }
                if (key != null) jdbc.update("insert into " + replayTable() + "(owner_id, request_key, fingerprint, status, content_type, body) values(?,?,?,?,?,?)",
                    owner, key, fingerprint, cached.getStatus(), cached.getContentType(), cached.getContentAsByteArray());
            });
            cached.copyBodyToResponse();
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(OrderWriteFilter.class).error("Order write transaction failed", e);
            // Never send a success body if committing the write failed.
            if (response.isCommitted()) throw e;
            response.reset(); response.setStatus(409); response.setContentType("application/json");
            response.getWriter().write("{\"status\":409,\"message\":\"The order could not be saved. Refresh and retry with the same request key.\"}");
        }
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static boolean requiresIdempotencyKey(String method, String requestUri) {
        return "POST".equals(method) && (
                requestUri.matches(".*/restaurants/[0-9a-fA-F-]{36}/orders/[^/]+/payments(?:/[^/]+/(?:refund|void))?")
                        || isInventoryMovement(method, requestUri)
        );
    }

    private static boolean isInventoryMovement(String method, String requestUri) {
        return "POST".equals(method)
                && requestUri.matches(".*/restaurants/[0-9a-fA-F-]{36}/inventory/(?:receive|waste|transfer|returns|adjustments)");
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return input.read(); }
                public boolean isFinished() { return input.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
