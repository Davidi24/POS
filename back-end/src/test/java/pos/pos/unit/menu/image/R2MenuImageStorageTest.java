package pos.pos.unit.menu.image;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.menu.image.R2MenuImageStorage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class R2MenuImageStorageTest {
    private static final byte[] IMAGE = "decoded image bytes".getBytes(StandardCharsets.UTF_8);
    private HttpServer server;
    private final AtomicReference<String> failingMethod = new AtomicReference<>();
    private final AtomicReference<byte[]> saved = new AtomicReference<>();
    private R2MenuImageStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/menu-bucket/", exchange -> {
            String method = exchange.getRequestMethod();
            if (method.equals(failingMethod.get())) {
                byte[] error = "<Error><Code>InternalError</Code><Message>simulated storage failure</Message></Error>"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, error.length);
                exchange.getResponseBody().write(error);
                exchange.close();
                return;
            }
            switch (method) {
                case "PUT" -> {
                    saved.set(exchange.getRequestBody().readAllBytes());
                    exchange.getResponseHeaders().add("ETag", "\"test-etag\"");
                    exchange.sendResponseHeaders(200, -1);
                }
                case "GET" -> {
                    byte[] body = IMAGE;
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
                case "DELETE" -> exchange.sendResponseHeaders(204, -1);
                default -> exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
        server.start();
        storage = new R2MenuImageStorage("http://127.0.0.1:" + server.getAddress().getPort(),
                "access", "secret", "menu-bucket", "https://images.example.invalid");
    }

    @AfterEach
    void tearDown() {
        storage.destroy();
        server.stop(0);
    }

    @Test
    void storesLoadsBuildsPublicUrlAndDeletesObjects() {
        String key = storage.store(UUID.randomUUID(), IMAGE, "image/png", ".png");
        assertTrue(key.startsWith("menu-images/"));
        // The SDK may wrap request bytes in its signed/chunked transfer encoding.
        assertTrue(new String(saved.get(), StandardCharsets.UTF_8).contains(new String(IMAGE, StandardCharsets.UTF_8)));
        assertEquals("https://images.example.invalid/" + key, storage.urlFor(key));
        assertEquals(Optional.of(key), storage.keyOf(storage.urlFor(key)));
        var loaded = storage.load(key).orElseThrow();
        assertArrayEquals(IMAGE, loaded.content());
        assertEquals("image/png", loaded.contentType());
        storage.delete(key);
    }

    @Test
    void mapsPutGetAndDeleteFailuresToRetryableServiceUnavailable() {
        failingMethod.set("PUT");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> storage.store(UUID.randomUUID(), IMAGE, "image/png", ".png")).getStatusCode().value());
        failingMethod.set("GET");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> storage.load("menu-images/00000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002.png"))
                .getStatusCode().value());
        failingMethod.set("DELETE");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> storage.delete("menu-images/00000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002.png"))
                .getStatusCode().value());
    }
}
