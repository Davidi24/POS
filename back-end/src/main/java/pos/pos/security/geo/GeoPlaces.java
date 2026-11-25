package pos.pos.security.geo;

import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Where a network address is, as "Tirana, Albania" (user, 2026-11-25: "show the location of the devices so I know who it
 * is"). Read from a place database kept on this server, so addresses never leave it: DB-IP's free "IP to City Lite"
 * (CC BY 4.0: the app shows "Places by DB-IP" under the lists), downloaded at start when missing and again when older than a
 * month (`app.geo.*`). Private and local addresses have no place. Until the database is there, nothing has a place.
 */
@Slf4j
@Component
public class GeoPlaces {

    @Value("${app.geo.enabled:true}")
    private boolean enabled = true;

    @Value("${app.geo.directory:${user.home}/.pos/geo}")
    private String directory;

    /** %s = the month, yyyy-MM. */
    @Value("${app.geo.url:https://download.db-ip.com/free/dbip-city-lite-%s.mmdb.gz}")
    private String url;

    @Value("${app.geo.max-age-days:35}")
    private int maxAgeDays = 35;

    private volatile Reader reader;

    @PostConstruct
    void start() {
        if (!enabled) return;
        Thread.ofVirtual().name("geo-places").start(() -> {
            open();
            refreshIfOld();
        });
    }

    /** Once a day: a newer database when this one is a month old. */
    @Scheduled(initialDelay = 6, fixedDelay = 24, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    void daily() {
        if (enabled) refreshIfOld();
    }

    /** "Tirana, Albania", "Albania", or null (unknown, private, or no database yet). */
    public String placeOf(String ip) {
        Reader current = reader;
        if (current == null || ip == null || ip.isBlank()) return null;
        try {
            String text = ip.trim();
            int zone = text.indexOf('%');
            if (zone >= 0) text = text.substring(0, zone);
            if (!text.matches("[0-9a-fA-F:.]+")) return null;
            InetAddress address = InetAddress.getByName(text);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> found = current.get(address, Map.class);
            if (found == null) return null;
            String city = name(found.get("city"));
            String country = name(found.get("country"));
            if (city != null && country != null) return city + ", " + country;
            return country != null ? country : city;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String name(Object part) {
        if (!(part instanceof Map<?, ?> map)) return null;
        Object names = map.get("names");
        if (!(names instanceof Map<?, ?> byLanguage)) return null;
        Object english = byLanguage.get("en");
        return english instanceof String s && !s.isBlank() ? s : null;
    }

    private Path file() {
        return Path.of(directory, "places.mmdb");
    }

    private synchronized void open() {
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try {
            Reader previous = reader;
            reader = new Reader(file.toFile(), new CHMCache());
            if (previous != null) previous.close();
        } catch (IOException e) {
            log.warn("The place database {} couldn't be opened: {}", file, e.getMessage());
        }
    }

    private void refreshIfOld() {
        Path file = file();
        try {
            if (Files.isRegularFile(file)
                    && Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now().minus(Duration.ofDays(maxAgeDays)))) return;
            Files.createDirectories(file.getParent());
            YearMonth month = YearMonth.now(ZoneOffset.UTC);
            for (YearMonth tried : List.of(month, month.minusMonths(1))) {
                if (download(String.format(url, tried), file)) {
                    open();
                    log.info("Place database updated ({})", tried);
                    return;
                }
            }
            log.warn("The place database couldn't be downloaded; sign-ins show no places until it is.");
        } catch (Exception e) {
            log.warn("The place database couldn't be updated: {}", e.getMessage());
        }
    }

    private boolean download(String from, Path to) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<InputStream> answer = client.send(HttpRequest.newBuilder(URI.create(from)).timeout(Duration.ofMinutes(10))
                .header("User-Agent", "pos-server").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (answer.statusCode() != 200) {
            answer.body().close();
            return false;
        }
        Path part = to.resolveSibling(to.getFileName() + ".part");
        try (InputStream in = new GZIPInputStream(answer.body())) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        // A database that opens is the only one kept.
        try (Reader check = new Reader(part.toFile())) {
            check.getMetadata();
        } catch (IOException e) {
            Files.deleteIfExists(part);
            return false;
        }
        Files.move(part, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    @PreDestroy
    void close() throws IOException {
        Reader current = reader;
        reader = null;
        if (current != null) current.close();
    }
}
