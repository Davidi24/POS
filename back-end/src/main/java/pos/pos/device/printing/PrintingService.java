package pos.pos.device.printing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePrinterProfile;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Printing (user, 2026-11-27): each printer says what it prints, the tills whose receipts and the kitchen stations whose tickets
 * ([jobs], [setJobs]); a till or a station prints on one printer, so choosing it on a printer takes it off the one it was on.
 * The app on a till or tablet reads [setup] and sends to the printers itself (the server can't reach printers inside the
 * restaurant); a printer plugged into one device (USB, Bluetooth) prints for the others through [queue], which that device
 * empties ([hostJobs]).
 */
@Service
@RequiredArgsConstructor
public class PrintingService {

    private static final String RECEIPTS = "RECEIPTS";
    private static final String KITCHEN = "KITCHEN";
    private static final int QUEUE_MINUTES = 2;

    private final DeviceRepository devices;
    private final SettingsRepository settingsRepository;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;
    private final OpaqueTokenService opaqueTokens;
    private final DevicePairingProperties pairingProperties;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- What a printer prints ----

    @Transactional(readOnly = true)
    public PrintingDtos.PrinterJobs jobs(Authentication authentication, UUID restaurantId, UUID printerId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        devices.findPrinterByIdAndRestaurantId(printerId, restaurantId).orElseThrow(DeviceNotFoundException::new);
        return jobsOf(restaurantId, printerId);
    }

    @Transactional
    public PrintingDtos.PrinterJobs setJobs(Authentication authentication, UUID restaurantId, UUID printerId, PrintingDtos.SetPrinterJobsRequest request) {
        Restaurant restaurant = settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        Device printer = devices.findPrinterByIdAndRestaurantId(printerId, restaurantId).orElseThrow(DeviceNotFoundException::new);
        Set<UUID> tills = new java.util.LinkedHashSet<>(request.receiptTillIds());
        Set<UUID> stations = new java.util.LinkedHashSet<>(request.kitchenStationIds());
        boolean document = printer.getPrinterProfile() != null && "DOCUMENT".equals(printer.getPrinterProfile().getPrinterRole());
        if (document && (!tills.isEmpty() || !stations.isEmpty())) {
            throw new AuthException("A document printer prints documents only. Choose it when you print a report.", HttpStatus.BAD_REQUEST);
        }
        Set<UUID> knownTills = tillsOf(restaurantId).stream().map(row -> (UUID) row[0]).collect(Collectors.toSet());
        Set<UUID> knownStations = stationsOf(restaurantId).stream().map(row -> (UUID) row[0]).collect(Collectors.toSet());
        if (!knownTills.containsAll(tills)) throw new AuthException("A till you chose isn't one of this restaurant's.", HttpStatus.BAD_REQUEST);
        if (!knownStations.containsAll(stations)) throw new AuthException("A kitchen station you chose isn't one of this restaurant's.", HttpStatus.BAD_REQUEST);
        UUID actor = settingsDomainSupport.currentActorId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entityManager.createNativeQuery("delete from {h-schema}printer_jobs where printer_id = :printer").setParameter("printer", printerId).executeUpdate();
        for (UUID till : tills) {
            // One printer per till: it leaves the printer it was on.
            entityManager.createNativeQuery("delete from {h-schema}printer_jobs where kind = 'RECEIPTS' and till_id = :till").setParameter("till", till).executeUpdate();
            insert(restaurantId, printerId, RECEIPTS, till, null, now, actor);
        }
        for (UUID station : stations) {
            entityManager.createNativeQuery("delete from {h-schema}printer_jobs where kind = 'KITCHEN' and station_id = :station").setParameter("station", station).executeUpdate();
            insert(restaurantId, printerId, KITCHEN, null, station, now, actor);
        }
        settingsAuditService.log(restaurant, null, "PRINTER_JOBS", printerId, "UPDATE",
                printer.getName() + " prints receipts for " + tills.size() + " till(s) and tickets for " + stations.size() + " kitchen station(s)", actor);
        return jobsOf(restaurantId, printerId);
    }

    // ---- What a device prints ----

    @Transactional(readOnly = true)
    public PrintingDtos.Setup setup(Authentication authentication, UUID restaurantId, UUID deviceId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Map<UUID, PrintingDtos.Printer> printers = printersOf(restaurantId);
        List<Object[]> jobs = jobsOf(restaurantId);
        List<PrintingDtos.Printer> receipt = new ArrayList<>();
        if (deviceId != null) {
            jobs.stream().filter(job -> RECEIPTS.equals(job[0]) && deviceId.equals(job[1]))
                    .map(job -> printers.get((UUID) job[3])).filter(Objects::nonNull).forEach(receipt::add);
        }
        List<PrintingDtos.StationPrinters> kitchen = new ArrayList<>();
        for (Object[] station : stationsOf(restaurantId)) {
            List<PrintingDtos.Printer> list = jobs.stream().filter(job -> KITCHEN.equals(job[0]) && station[0].equals(job[2]))
                    .map(job -> printers.get((UUID) job[3])).filter(Objects::nonNull).toList();
            if (!list.isEmpty()) kitchen.add(new PrintingDtos.StationPrinters((UUID) station[0], (String) station[1], list,
                    station.length > 3 && station[3] != null ? String.valueOf(station[3]) : "NORMAL", station.length > 4 && Boolean.TRUE.equals(station[4])));
        }
        Settings settings = settingsRepository.findByRestaurant_Id(restaurantId).orElseGet(Settings::new);
        return new PrintingDtos.Setup(deviceId, receipt, kitchen, List.copyOf(printers.values()),
                settings.isPrintKitchenOnSend(), settings.isPrintBillOnRequest(), settings.getPrintReceiptAfterPayment());
    }

    // ---- A printer plugged into one device prints for the others ----

    @Transactional
    public PrintingDtos.QueuedPrint queue(Authentication authentication, UUID restaurantId, UUID printerId, PrintingDtos.QueueRequest request) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Device printer = devices.findPrinterByIdAndRestaurantId(printerId, restaurantId).orElseThrow(DeviceNotFoundException::new);
        UUID host = printer.getPrinterProfile() == null ? null : printer.getPrinterProfile().getHostDeviceId();
        if (host == null) throw new AuthException(printer.getName() + " isn't plugged into a till or tablet.", HttpStatus.BAD_REQUEST);
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(request.data());
        } catch (IllegalArgumentException e) {
            throw new AuthException("That isn't something to print.", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entityManager.createNativeQuery("delete from {h-schema}print_queue where created_at < :before")
                .setParameter("before", now.minusDays(1)).executeUpdate();
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("insert into {h-schema}print_queue (id, restaurant_id, printer_id, host_device_id, payload, status, created_by, created_at)"
                        + " values (:id, :restaurant, :printer, :host, :payload, 'WAITING', :actor, :now)")
                .setParameter("id", id).setParameter("restaurant", restaurantId).setParameter("printer", printerId).setParameter("host", host)
                .setParameter("payload", payload).setParameter("actor", settingsDomainSupport.currentActorId(authentication))
                .setParameter("now", now).executeUpdate();
        return new PrintingDtos.QueuedPrint(id, printerId, "WAITING", null, now);
    }

    @Transactional(readOnly = true)
    public PrintingDtos.QueuedPrint queued(Authentication authentication, UUID restaurantId, UUID id) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("select cast(printer_id as varchar), status, problem, created_at from {h-schema}print_queue"
                + " where id = :id and restaurant_id = :restaurant").setParameter("id", id).setParameter("restaurant", restaurantId).getResultList();
        if (rows.isEmpty()) throw new AuthException("That print isn't there any more.", HttpStatus.NOT_FOUND);
        Object[] row = rows.get(0);
        String status = String.valueOf(row[1]);
        OffsetDateTime created = toTime(row[3]);
        // Nobody printed it in time: the device it waits for is off or the app is closed there.
        if (("WAITING".equals(status) || "PRINTING".equals(status)) && created != null
                && created.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(QUEUE_MINUTES))) {
            return new PrintingDtos.QueuedPrint(id, UUID.fromString(String.valueOf(row[0])), "FAILED",
                    "The device it's plugged into didn't print it. Is the app open there?", created);
        }
        return new PrintingDtos.QueuedPrint(id, UUID.fromString(String.valueOf(row[0])), status, (String) row[2], created);
    }

    /** The prints waiting for the device [deviceId] (proved by its secret), taken as printing now. */
    @Transactional
    public List<PrintingDtos.HostJob> hostJobs(UUID deviceId, String secret) {
        verifyDevice(deviceId, secret);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("select cast(q.id as varchar), cast(q.printer_id as varchar), q.payload, p.connection_type, p.system_printer_name"
                        + " from {h-schema}print_queue q left join {h-schema}\"device-printer-profiles\" p on p.device_id = q.printer_id"
                        + " where q.host_device_id = :device and q.status = 'WAITING' and q.created_at >= :since order by q.created_at limit 20 for update of q skip locked")
                .setParameter("device", deviceId).setParameter("since", now.minusMinutes(QUEUE_MINUTES)).getResultList();
        List<PrintingDtos.HostJob> jobs = new ArrayList<>();
        for (Object[] row : rows) {
            UUID id = UUID.fromString(String.valueOf(row[0]));
            entityManager.createNativeQuery("update {h-schema}print_queue set status = 'PRINTING', picked_at = :now where id = :id")
                    .setParameter("now", now).setParameter("id", id).executeUpdate();
            jobs.add(new PrintingDtos.HostJob(id, UUID.fromString(String.valueOf(row[1])), (String) row[3], (String) row[4],
                    Base64.getEncoder().encodeToString((byte[]) row[2])));
        }
        return jobs;
    }

    @Transactional
    public void hostResult(UUID deviceId, String secret, UUID jobId, PrintingDtos.HostResult result) {
        verifyDevice(deviceId, secret);
        int changed = entityManager.createNativeQuery("update {h-schema}print_queue set status = :status, problem = :problem, done_at = :now, payload = decode('', 'hex')"
                        + " where id = :id and host_device_id = :device")
                .setParameter("status", result.ok() ? "DONE" : "FAILED")
                .setParameter("problem", result.ok() ? null : (StringUtils.hasText(result.problem()) ? result.problem() : "It didn't print."))
                .setParameter("now", OffsetDateTime.now(ZoneOffset.UTC)).setParameter("id", jobId).setParameter("device", deviceId).executeUpdate();
        if (changed == 0) throw new AuthException("That print isn't there any more.", HttpStatus.NOT_FOUND);
    }

    // ---- Whether printers answer ----

    /**
     * The printers [deviceId] checks once a minute (user, 2026-11-27): the restaurant's network printers in use, and the ones
     * plugged into or paired with this device.
     */
    @Transactional(readOnly = true)
    public List<PrintingDtos.PrinterToCheck> printersToCheck(UUID deviceId, String secret) {
        Device till = verifyDevice(deviceId, secret);
        return checkable(till).stream().map(printer -> {
            DevicePrinterProfile profile = printer.getPrinterProfile();
            return new PrintingDtos.PrinterToCheck(printer.getId(), printer.getName(), profile.getPrinterRole(), profile.getConnectionType().name(),
                    profile.getPrinterIp(), profile.getPrinterPort(), profile.getSystemPrinterName());
        }).toList();
    }

    /** What [deviceId] found: a printer it reached is online for a few minutes; one it didn't keeps why. */
    @Transactional
    public void printerChecks(UUID deviceId, String secret, PrintingDtos.PrinterChecksRequest request) {
        Device till = verifyDevice(deviceId, secret);
        Set<UUID> allowed = checkable(till).stream().map(Device::getId).collect(Collectors.toSet());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (PrintingDtos.PrinterCheck check : request.checks()) {
            if (!allowed.contains(check.printerId())) continue;
            if (check.reachable()) {
                entityManager.createNativeQuery("update {h-schema}\"device-printer-profiles\" set reached_at = :now, checked_at = :now, checked_by = :till,"
                                + " check_problem = null where device_id = :printer")
                        .setParameter("now", now).setParameter("till", deviceId).setParameter("printer", check.printerId()).executeUpdate();
            } else {
                // Another till that reached it lately still counts: only the check and why it failed here are kept.
                entityManager.createNativeQuery("update {h-schema}\"device-printer-profiles\" set checked_at = :now, checked_by = :till,"
                                + " check_problem = :problem where device_id = :printer")
                        .setParameter("now", now).setParameter("till", deviceId)
                        .setParameter("problem", StringUtils.hasText(check.problem()) ? check.problem() : "It didn't answer.")
                        .setParameter("printer", check.printerId()).executeUpdate();
            }
        }
    }

    private List<Device> checkable(Device till) {
        if (till.getRestaurant() == null) return List.of();
        return devices.findRestaurantPrinters(till.getRestaurant().getId()).stream()
                .filter(printer -> printer.isActive() && (printer.getStatus() == DeviceStatus.ACTIVE || printer.getStatus() == DeviceStatus.PROVISIONING))
                .filter(printer -> {
                    DevicePrinterProfile profile = printer.getPrinterProfile();
                    if (profile == null || profile.getConnectionType() == null) return false;
                    boolean network = profile.getConnectionType() == pos.pos.device.enums.PrinterConnectionType.NETWORK && StringUtils.hasText(profile.getPrinterIp());
                    return network || till.getId().equals(profile.getHostDeviceId());
                })
                .toList();
    }

    // ---- helpers ----

    private PrintingDtos.PrinterJobs jobsOf(UUID restaurantId, UUID printerId) {
        Map<UUID, PrintingDtos.Printer> printers = printersOf(restaurantId);
        Map<UUID, UUID> tillPrinter = new HashMap<>();
        Map<UUID, UUID> stationPrinter = new HashMap<>();
        for (Object[] job : jobsOf(restaurantId)) {
            if (RECEIPTS.equals(job[0])) tillPrinter.put((UUID) job[1], (UUID) job[3]);
            else stationPrinter.put((UUID) job[2], (UUID) job[3]);
        }
        List<PrintingDtos.Target> tills = tillsOf(restaurantId).stream().map(row -> target(row, tillPrinter, printers)).toList();
        List<PrintingDtos.Target> stations = stationsOf(restaurantId).stream().map(row -> target(row, stationPrinter, printers)).toList();
        return new PrintingDtos.PrinterJobs(
                tills.stream().filter(t -> printerId.equals(t.printerId())).map(PrintingDtos.Target::id).toList(),
                stations.stream().filter(t -> printerId.equals(t.printerId())).map(PrintingDtos.Target::id).toList(),
                tills, stations);
    }

    private static PrintingDtos.Target target(Object[] row, Map<UUID, UUID> printerOf, Map<UUID, PrintingDtos.Printer> printers) {
        UUID printer = printerOf.get((UUID) row[0]);
        PrintingDtos.Printer on = printer == null ? null : printers.get(printer);
        return new PrintingDtos.Target((UUID) row[0], (String) row[1], (String) row[2], on == null ? null : printer, on == null ? null : on.name());
    }

    /** [kind, till, station, printer] for every job of the restaurant. */
    private List<Object[]> jobsOf(UUID restaurantId) {
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("select kind, cast(till_id as varchar), cast(station_id as varchar), cast(printer_id as varchar)"
                + " from {h-schema}printer_jobs where restaurant_id = :restaurant").setParameter("restaurant", restaurantId).getResultList();
        List<Object[]> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new Object[]{row[0], uuid(row[1]), uuid(row[2]), uuid(row[3])});
        }
        return out;
    }

    private Map<UUID, PrintingDtos.Printer> printersOf(UUID restaurantId) {
        Map<UUID, String> names = new HashMap<>();
        tillsOf(restaurantId).forEach(row -> names.put((UUID) row[0], (String) row[1]));
        // A printer with a cash drawer plugged into it opens it (the drawer is a device of its own since 2026-11-27).
        Set<UUID> withDrawer = devices.findByRestaurant_IdAndDeviceType(restaurantId, pos.pos.device.enums.DeviceType.CASH_DRAWER).stream()
                .filter(drawer -> drawer.isActive() && drawer.getStatus() != DeviceStatus.RETIRED && drawer.getLinkedDeviceId() != null)
                .map(Device::getLinkedDeviceId).collect(Collectors.toSet());
        return devices.findRestaurantPrinters(restaurantId).stream()
                .collect(Collectors.toMap(Device::getId, device -> printerOf(device, names, withDrawer.contains(device.getId())), (a, b) -> a, LinkedHashMap::new));
    }

    /** Tills and tablets of the restaurant that aren't retired: [id, name, branch name]. */
    private List<Object[]> tillsOf(UUID restaurantId) {
        return idRows(entityManager.createNativeQuery("select cast(d.id as varchar), d.name, b.name from {h-schema}devices d"
                + " left join {h-schema}branches b on b.id = d.branch_id"
                + " where d.restaurant_id = :restaurant and d.device_type in ('POS_TERMINAL', 'TABLET') and d.status <> 'RETIRED'"
                + " order by b.name, d.name").setParameter("restaurant", restaurantId).getResultList());
    }

    /** The restaurant's active kitchen stations: [id, name, branch name, ticket letter size, notes in red]. */
    private List<Object[]> stationsOf(UUID restaurantId) {
        return idRows(entityManager.createNativeQuery("select cast(s.id as varchar), s.name, b.name, s.ticket_text_size, s.ticket_notes_red from {h-schema}kds_stations s"
                + " left join {h-schema}branches b on b.id = s.branch_id"
                + " where s.restaurant_id = :restaurant and s.is_active order by b.name, s.display_order, s.name")
                .setParameter("restaurant", restaurantId).getResultList());
    }

    private static List<Object[]> idRows(List<?> raw) {
        List<Object[]> out = new ArrayList<>();
        for (Object row : raw) {
            Object[] cells = ((Object[]) row).clone();
            cells[0] = uuid(cells[0]);
            out.add(cells);
        }
        return out;
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(String.valueOf(value));
    }

    private static OffsetDateTime toTime(Object value) {
        if (value instanceof OffsetDateTime time) return time;
        if (value instanceof java.time.Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        return null;
    }

    private void insert(UUID restaurantId, UUID printerId, String kind, UUID till, UUID station, OffsetDateTime now, UUID actor) {
        entityManager.createNativeQuery("insert into {h-schema}printer_jobs (id, restaurant_id, printer_id, kind, till_id, station_id, created_at, created_by)"
                        + " values (:id, :restaurant, :printer, :kind, :till, :station, :now, :actor)")
                .setParameter("id", UUID.randomUUID()).setParameter("restaurant", restaurantId).setParameter("printer", printerId)
                .setParameter("kind", kind).setParameter("till", till).setParameter("station", station)
                .setParameter("now", now).setParameter("actor", actor).executeUpdate();
    }

    private Device verifyDevice(UUID deviceId, String secret) {
        if (!StringUtils.hasText(secret)) throw unauthorized();
        Device device = devices.findById(deviceId).orElseThrow(this::unauthorized);
        String stored = device.getAuthSecretHash();
        String supplied = opaqueTokens.hash(secret, pairingProperties.getTokenPepper());
        if (!device.isActive() || device.getStatus() != DeviceStatus.ACTIVE || !StringUtils.hasText(stored)
                || !MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized();
        }
        return device;
    }

    private AuthException unauthorized() {
        return new AuthException("Device authentication failed", HttpStatus.UNAUTHORIZED);
    }

    private static PrintingDtos.Printer printerOf(Device device, Map<UUID, String> tillNames, boolean drawer) {
        DevicePrinterProfile profile = device.getPrinterProfile();
        boolean usable = device.isActive() && (device.getStatus() == DeviceStatus.ACTIVE || device.getStatus() == DeviceStatus.PROVISIONING);
        UUID host = profile == null ? null : profile.getHostDeviceId();
        return new PrintingDtos.Printer(device.getId(), device.getName(), profile == null ? "OTHER" : profile.getPrinterRole(),
                profile == null || profile.getConnectionType() == null ? null : profile.getConnectionType().name(),
                profile == null ? null : profile.getPrinterIp(), profile == null ? null : profile.getPrinterPort(),
                profile == null ? null : profile.getSystemPrinterName(), host, host == null ? null : tillNames.get(host),
                profile == null ? 80 : profile.getPaperWidthMm(), profile != null && profile.isAutoCut(),
                drawer || (profile != null && profile.isCashDrawerKickEnabled()), usable);
    }
}
