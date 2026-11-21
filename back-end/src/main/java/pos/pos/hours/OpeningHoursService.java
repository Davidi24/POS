package pos.pos.hours;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;

import java.sql.Time;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opening hours (user, 2026-11-20, step 4): each branch has its own week, any number of openings a day (each from when it
 * opens to when it closes; a closing at or before the opening runs past midnight), special days for a date or a range,
 * closed or with their own openings, and "Open outside the hours" for now. A branch whose hours were never set counts as
 * always open, so nothing is blocked before someone sets them.
 */
@Service
public class OpeningHoursService {

    public static final int MAX_OPENINGS_A_DAY = 6;
    public static final int MAX_SPECIAL_DAYS_RANGE = 366;
    public static final int MAX_CALENDAR_DAYS = 400;

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public OpeningHoursService(NamedParameterJdbcTemplate jdbc,
                               @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    /** One opening: from [opens] to [closes]; closing at or before opening runs past midnight. */
    public record Span(LocalTime opens, LocalTime closes) {
        boolean pastMidnight() {
            return !closes.isAfter(opens);
        }
    }

    /** A weekday (1 = Monday … 7 = Sunday) and its openings; none = closed that day. */
    public record Day(int day, List<Span> spans) {
    }

    public record SpecialDay(UUID id, LocalDate startsOn, LocalDate endsOn, boolean closed, List<Span> spans, String note) {
    }

    /**
     * Whether the branch is open now: [set] false = hours never set (counts as open); [why] HOURS, SPECIAL, OPENED (opened
     * outside the hours) or NOT_SET; [until] when the opening it's in ends; [nextOpen] when it opens next while closed.
     */
    public record OpenNow(boolean open, boolean set, String why, OffsetDateTime until, OffsetDateTime nextOpen,
                          OffsetDateTime openedUntil, String note) {
    }

    public record Hours(boolean set, String timezone, List<Day> week, List<SpecialDay> specialDays, OpenNow now) {
    }

    /** One date as it is: closed or its openings, and whether a special day made it so. */
    public record CalendarDay(LocalDate date, boolean closed, List<Span> spans, boolean special, String note) {
    }

    // ---- Reading ----

    @Transactional(readOnly = true)
    public Hours hours(UUID branchId) {
        ZoneId zone = zoneOf(branchId);
        LocalDate today = LocalDate.now(zone);
        return new Hours(isSet(branchId), zone.getId(), week(branchId), specialDays(branchId, today.minusDays(30), today.plusYears(2)),
                now(branchId, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public OpenNow now(UUID branchId, OffsetDateTime at) {
        if (!isSet(branchId)) return new OpenNow(true, false, "NOT_SET", null, null, null, null);
        ZoneId zone = zoneOf(branchId);
        Window window = windowAt(branchId, zone, at);
        OffsetDateTime openedUntil = jdbc.query("select open_until from " + schema + "branch_open_overrides where branch_id = :b"
                        + " and closed_at is null and opened_at <= :at and open_until > :at order by open_until desc limit 1",
                Map.of("b", branchId, "at", at), (row, i) -> row.getObject("open_until", OffsetDateTime.class)).stream().findFirst().orElse(null);
        if (window != null) return new OpenNow(true, true, window.special ? "SPECIAL" : "HOURS", window.end, null, openedUntil, window.note);
        OffsetDateTime next = nextOpening(branchId, zone, at);
        String note = specialNote(branchId, at.atZoneSameInstant(zone).toLocalDate());
        if (openedUntil != null) return new OpenNow(true, true, "OPENED", openedUntil, next, openedUntil, note);
        return new OpenNow(false, true, specialCovers(branchId, at.atZoneSameInstant(zone).toLocalDate()) ? "SPECIAL" : "HOURS", null, next, null, note);
    }

    /** Whether the branch is open at [at] by its hours (opening outside the hours is only ever for now). */
    @Transactional(readOnly = true)
    public boolean isOpenAt(UUID branchId, OffsetDateTime at) {
        if (!isSet(branchId)) return true;
        return windowAt(branchId, zoneOf(branchId), at) != null;
    }

    /** Each date from [from] to [to] as it is. A branch whose hours were never set has an empty calendar. */
    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(UUID branchId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw bad("Choose a date range");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_CALENDAR_DAYS) throw bad("Choose at most " + MAX_CALENDAR_DAYS + " days");
        if (!isSet(branchId)) return List.of();
        Map<Integer, List<Span>> week = weekMap(branchId);
        List<SpecialDay> specials = specialDays(branchId, from, to);
        List<CalendarDay> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            SpecialDay special = specialOn(specials, d);
            List<Span> spans = special != null ? (special.closed() ? List.of() : special.spans()) : week.getOrDefault(d.getDayOfWeek().getValue(), List.of());
            out.add(new CalendarDay(d, spans.isEmpty(), spans, special != null, special == null ? null : special.note()));
        }
        return out;
    }

    /**
     * The week in a line for guests (the booking page and booking emails): "Mon–Fri 12:00–15:00, 19:00–23:00 · Sat
     * 19:00–00:30 · Sun closed", days with the same openings together; null while the hours aren't set.
     */
    @Transactional(readOnly = true)
    public String weekText(UUID branchId, String language) {
        if (branchId == null || !isSet(branchId)) return null;
        java.util.Locale locale = java.util.Locale.forLanguageTag(language == null ? "en" : language);
        Map<Integer, List<Span>> week = weekMap(branchId);
        List<String> parts = new ArrayList<>();
        int day = 1;
        while (day <= 7) {
            List<Span> spans = week.getOrDefault(day, List.of());
            int last = day;
            while (last < 7 && week.getOrDefault(last + 1, List.of()).equals(spans)) last++;
            String days = dayName(day, locale) + (last > day ? "–" + dayName(last, locale) : "");
            String times = spans.isEmpty() ? pos.pos.i18n.Words.t(language, "closed")
                    : String.join(", ", spans.stream().map(sp -> sp.opens() + "–" + sp.closes()).toList());
            parts.add(days + " " + times);
            day = last + 1;
        }
        return String.join(" · ", parts);
    }

    private static String dayName(int day, java.util.Locale locale) {
        return java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, locale);
    }

    // ---- Changing ----

    @Transactional
    public Hours saveWeek(UUID branchId, List<Day> days) {
        if (days == null) throw bad("Send the week");
        Map<Integer, List<Span>> byDay = new HashMap<>();
        for (Day day : days) {
            if (day == null || day.day() < 1 || day.day() > 7) throw bad("Days go from 1 (Monday) to 7 (Sunday)");
            if (byDay.containsKey(day.day())) throw bad("Each day only once");
            byDay.put(day.day(), check(day.spans()));
        }
        jdbc.update("delete from " + schema + "branch_opening_hours where branch_id = :b", Map.of("b", branchId));
        byDay.forEach((day, spans) -> spans.forEach(span -> {
            Map<String, Object> p = new HashMap<>();
            p.put("id", UUID.randomUUID());
            p.put("b", branchId);
            p.put("d", day);
            p.put("o", Time.valueOf(span.opens()));
            p.put("c", Time.valueOf(span.closes()));
            jdbc.update("insert into " + schema + "branch_opening_hours (id, branch_id, day_of_week, opens, closes) values (:id, :b, :d, :o, :c)", p);
        }));
        jdbc.update("update " + schema + "branches set opening_hours_set = true where id = :b", Map.of("b", branchId));
        return hours(branchId);
    }

    @Transactional
    public SpecialDay saveSpecialDay(UUID branchId, UUID id, LocalDate startsOn, LocalDate endsOn, boolean closed, List<Span> spans,
                                     String note, UUID actorId) {
        if (startsOn == null) throw bad("Choose the day");
        LocalDate ends = endsOn == null ? startsOn : endsOn;
        if (ends.isBefore(startsOn)) throw bad("The last day comes after the first");
        if (ChronoUnit.DAYS.between(startsOn, ends) >= MAX_SPECIAL_DAYS_RANGE) throw bad("Choose at most a year of days");
        List<Span> checked = closed ? List.of() : check(spans);
        if (!closed && checked.isEmpty()) throw bad("Add when it opens and closes, or mark it closed");
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 255) throw bad("Keep the note under 255 characters");
        Map<String, Object> p = new HashMap<>();
        p.put("b", branchId);
        p.put("s", startsOn);
        p.put("e", ends);
        p.put("id", id == null ? new UUID(0, 0) : id);
        Integer overlapping = jdbc.queryForObject("select count(*) from " + schema + "branch_special_days where branch_id = :b and id <> :id"
                + " and starts_on <= :e and ends_on >= :s", p, Integer.class);
        if (overlapping != null && overlapping > 0) throw new AuthException("Some of those days already have special hours", HttpStatus.CONFLICT);
        UUID dayId = id == null ? UUID.randomUUID() : id;
        p.put("id", dayId);
        p.put("closed", closed);
        p.put("note", cleanNote);
        p.put("actor", actorId);
        if (id == null) {
            jdbc.update("insert into " + schema + "branch_special_days (id, branch_id, starts_on, ends_on, closed, note, created_by)"
                    + " values (:id, :b, :s, :e, :closed, :note, :actor)", p);
        } else {
            int changed = jdbc.update("update " + schema + "branch_special_days set starts_on = :s, ends_on = :e, closed = :closed, note = :note"
                    + " where id = :id and branch_id = :b", p);
            if (changed == 0) throw new AuthException("Special day not found", HttpStatus.NOT_FOUND);
            jdbc.update("delete from " + schema + "branch_special_day_hours where special_day_id = :id", Map.of("id", dayId));
        }
        for (Span span : checked) {
            Map<String, Object> h = new HashMap<>();
            h.put("id", UUID.randomUUID());
            h.put("day", dayId);
            h.put("o", Time.valueOf(span.opens()));
            h.put("c", Time.valueOf(span.closes()));
            jdbc.update("insert into " + schema + "branch_special_day_hours (id, special_day_id, opens, closes) values (:id, :day, :o, :c)", h);
        }
        return new SpecialDay(dayId, startsOn, ends, closed, checked, cleanNote);
    }

    @Transactional
    public void deleteSpecialDay(UUID branchId, UUID id) {
        int deleted = jdbc.update("delete from " + schema + "branch_special_days where id = :id and branch_id = :b", Map.of("id", id, "b", branchId));
        if (deleted == 0) throw new AuthException("Special day not found", HttpStatus.NOT_FOUND);
    }

    /** Opens a closed branch for now, until [until] (at most 18 hours ahead). */
    @Transactional
    public OpenNow openNow(UUID branchId, OffsetDateTime until, UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (until == null || !until.isAfter(now)) throw bad("Choose until when it stays open");
        if (Duration.between(now, until).toHours() > 18) throw bad("Open it for at most 18 hours");
        Map<String, Object> p = new HashMap<>();
        p.put("id", UUID.randomUUID());
        p.put("b", branchId);
        p.put("at", now);
        p.put("until", until);
        p.put("actor", actorId);
        jdbc.update("update " + schema + "branch_open_overrides set closed_at = :at where branch_id = :b and closed_at is null and open_until > :at", p);
        jdbc.update("insert into " + schema + "branch_open_overrides (id, branch_id, opened_at, open_until, opened_by) values (:id, :b, :at, :until, :actor)", p);
        return now(branchId, now);
    }

    /** Ends opening outside the hours. */
    @Transactional
    public OpenNow closeNow(UUID branchId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("update " + schema + "branch_open_overrides set closed_at = :at where branch_id = :b and closed_at is null and open_until > :at",
                Map.of("b", branchId, "at", now));
        return now(branchId, now);
    }

    /**
     * The POS while the branch is closed (user, 2026-11-20): nobody seats guests or starts orders; open orders can still be
     * paid. Someone with "Open outside the hours" opens it for now.
     */
    @Transactional(readOnly = true)
    public void assertOpenNow(UUID branchId) {
        OpenNow now = now(branchId, OffsetDateTime.now(ZoneOffset.UTC));
        if (!now.open()) {
            throw new AuthException("The branch is closed now. Someone who can open it outside the hours can open it.", HttpStatus.CONFLICT);
        }
    }

    // ---- Inside ----

    private record Window(OffsetDateTime start, OffsetDateTime end, boolean special, String note) {
    }

    private static List<Span> check(List<Span> spans) {
        if (spans == null) return List.of();
        if (spans.size() > MAX_OPENINGS_A_DAY) throw bad("At most " + MAX_OPENINGS_A_DAY + " openings a day");
        List<Span> sorted = new ArrayList<>();
        for (Span span : spans) {
            if (span == null || span.opens() == null || span.closes() == null) throw bad("Each opening needs when it opens and closes");
            if (span.opens().equals(span.closes())) throw bad("An opening can't close when it opens");
            sorted.add(new Span(span.opens().truncatedTo(ChronoUnit.MINUTES), span.closes().truncatedTo(ChronoUnit.MINUTES)));
        }
        sorted.sort(Comparator.comparing(Span::opens));
        for (int i = 0; i < sorted.size(); i++) {
            Span span = sorted.get(i);
            if (span.pastMidnight() && i < sorted.size() - 1) throw bad("Only the last opening of a day can run past midnight");
            if (i > 0 && span.opens().isBefore(sorted.get(i - 1).closes())) throw bad("Openings in a day can't overlap");
        }
        return sorted;
    }

    private boolean isSet(UUID branchId) {
        Boolean set = jdbc.query("select opening_hours_set from " + schema + "branches where id = :b", Map.of("b", branchId),
                (row, i) -> row.getBoolean(1)).stream().findFirst().orElse(false);
        return Boolean.TRUE.equals(set);
    }

    private ZoneId zoneOf(UUID branchId) {
        String zone = jdbc.query("select r.timezone from " + schema + "branches b join " + schema + "restaurants r on r.id = b.restaurant_id"
                + " where b.id = :b", Map.of("b", branchId), (row, i) -> row.getString(1)).stream().findFirst().orElse(null);
        try {
            return zone == null ? ZoneOffset.UTC : ZoneId.of(zone);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private List<Day> week(UUID branchId) {
        Map<Integer, List<Span>> map = weekMap(branchId);
        List<Day> week = new ArrayList<>();
        for (int day = 1; day <= 7; day++) week.add(new Day(day, map.getOrDefault(day, List.of())));
        return week;
    }

    private Map<Integer, List<Span>> weekMap(UUID branchId) {
        Map<Integer, List<Span>> map = new LinkedHashMap<>();
        jdbc.query("select day_of_week, opens, closes from " + schema + "branch_opening_hours where branch_id = :b order by day_of_week, opens",
                Map.of("b", branchId), (java.sql.ResultSet row) -> {
                    map.computeIfAbsent(row.getInt("day_of_week"), d -> new ArrayList<>())
                            .add(new Span(row.getTime("opens").toLocalTime(), row.getTime("closes").toLocalTime()));
                });
        return map;
    }

    private List<SpecialDay> specialDays(UUID branchId, LocalDate from, LocalDate to) {
        Map<UUID, SpecialDay> days = new LinkedHashMap<>();
        jdbc.query("select id, starts_on, ends_on, closed, note from " + schema + "branch_special_days where branch_id = :b"
                        + " and ends_on >= :f and starts_on <= :t order by starts_on",
                Map.of("b", branchId, "f", from, "t", to), (java.sql.ResultSet row) -> {
                    UUID id = row.getObject("id", UUID.class);
                    days.put(id, new SpecialDay(id, row.getObject("starts_on", LocalDate.class), row.getObject("ends_on", LocalDate.class),
                            row.getBoolean("closed"), new ArrayList<>(), row.getString("note")));
                });
        if (days.isEmpty()) return List.of();
        jdbc.query("select special_day_id, opens, closes from " + schema + "branch_special_day_hours where special_day_id in (:ids) order by opens",
                Map.of("ids", days.keySet()), (java.sql.ResultSet row) -> {
                    days.get(row.getObject("special_day_id", UUID.class)).spans()
                            .add(new Span(row.getTime("opens").toLocalTime(), row.getTime("closes").toLocalTime()));
                });
        return List.copyOf(days.values());
    }

    private static SpecialDay specialOn(List<SpecialDay> specials, LocalDate date) {
        for (SpecialDay day : specials) if (!date.isBefore(day.startsOn()) && !date.isAfter(day.endsOn())) return day;
        return null;
    }

    private boolean specialCovers(UUID branchId, LocalDate date) {
        return specialOn(specialDays(branchId, date, date), date) != null;
    }

    private String specialNote(UUID branchId, LocalDate date) {
        SpecialDay day = specialOn(specialDays(branchId, date, date), date);
        return day == null ? null : day.note();
    }

    /** The openings of [date] as moments (a past-midnight one ends the next day), and whether a special day made them. */
    private List<Window> windowsOf(LocalDate date, Map<Integer, List<Span>> week, List<SpecialDay> specials, ZoneId zone) {
        SpecialDay special = specialOn(specials, date);
        List<Span> spans = special != null ? (special.closed() ? List.of() : special.spans()) : week.getOrDefault(date.getDayOfWeek().getValue(), List.of());
        List<Window> out = new ArrayList<>();
        for (Span span : spans) {
            LocalDateTime start = date.atTime(span.opens());
            LocalDateTime end = span.pastMidnight() ? date.plusDays(1).atTime(span.closes()) : date.atTime(span.closes());
            out.add(new Window(start.atZone(zone).toOffsetDateTime(), end.atZone(zone).toOffsetDateTime(), special != null,
                    special == null ? null : special.note()));
        }
        return out;
    }

    private Window windowAt(UUID branchId, ZoneId zone, OffsetDateTime at) {
        LocalDate date = at.atZoneSameInstant(zone).toLocalDate();
        Map<Integer, List<Span>> week = weekMap(branchId);
        List<SpecialDay> specials = specialDays(branchId, date.minusDays(1), date);
        for (LocalDate d : List.of(date.minusDays(1), date)) {
            for (Window w : windowsOf(d, week, specials, zone)) {
                if (!at.isBefore(w.start) && at.isBefore(w.end)) return w;
            }
        }
        return null;
    }

    private OffsetDateTime nextOpening(UUID branchId, ZoneId zone, OffsetDateTime at) {
        LocalDate date = at.atZoneSameInstant(zone).toLocalDate();
        Map<Integer, List<Span>> week = weekMap(branchId);
        List<SpecialDay> specials = specialDays(branchId, date, date.plusDays(30));
        for (int i = 0; i <= 30; i++) {
            for (Window w : windowsOf(date.plusDays(i), week, specials, zone)) {
                if (w.start.isAfter(at)) return w.start;
            }
        }
        return null;
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
