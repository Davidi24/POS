package pos.pos.device.connect;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.auth.service.AccountAudit;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.dto.DevicePairingRedeemResponse;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePairingToken;
import pos.pos.device.network.DeviceNetworkService;
import pos.pos.device.repository.DevicePairingTokenRepository;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.device.service.DevicePairingService;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.user.repository.UserRepository;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Enter a code on a new device waits for the person who made the code to agree (user, 2026-11-25: "when someone puts the code,
 * it goes to the person who generated it; I agree, and in that moment the old one is removed"). The new device asks ([ask]),
 * the maker hears it (a notification, and the app asks them) and agrees or refuses within a few minutes ([agree], [refuse]),
 * then the new device takes the connection with the claim it got when asking ([claim]); the device that was the till until
 * then stops working, since the connection's secret changes.
 */
@Service
@RequiredArgsConstructor
public class DeviceConnectRequestService {

    public static final String EVENT = "DEVICE_CONNECT_REQUEST";

    private final DeviceConnectRequestRepository requests;
    private final DevicePairingTokenRepository pairingTokens;
    private final DeviceRepository devices;
    private final DevicePairingService pairing;
    private final DevicePairingProperties pairingProperties;
    private final OpaqueTokenService opaqueTokens;
    private final UserRepository users;
    private final AccountAudit accountAudit;
    private final NotificationOperationalPublisher notifications;

    /** How long the maker has to answer (the app keeps the screens awake as long). */
    @Value("${app.devices.connect.wait-minutes:3}")
    private int waitMinutes = 3;

    /** How long the new device has to take the connection once agreed (it asks every few seconds). */
    @Value("${app.devices.connect.claim-minutes:2}")
    private int claimMinutes = 2;

    @Transactional
    public DeviceConnectDtos.Asked ask(String rawCode, UUID restaurantId, UUID askedBy, String askingDevice, String askingSystem, String address) {
        DevicePairingToken token = pairing.usableToken(rawCode, restaurantId);
        Device device = token.getDevice();
        if (!DeviceNetworkService.RULED.contains(device.getDeviceType())) {
            throw new AuthException("This code is for a card terminal: type it on the terminal itself.", HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.hasText(device.getAuthSecretHash())) {
            throw new AuthException("That device is in use. Disconnect it first, then make a new code.", HttpStatus.CONFLICT);
        }
        OffsetDateTime now = now();
        requests.forgetBefore(now.minusDays(7));
        for (DeviceConnectRequest open : requests.findByPairingTokenIdAndStatusIn(token.getId(),
                List.of(DeviceConnectRequest.WAITING, DeviceConnectRequest.AGREED))) {
            if (!open.getExpiresAt().isAfter(now)) continue;
            if (askedBy.equals(open.getRequestedBy())) {
                open.setStatus(DeviceConnectRequest.CANCELLED); // asked again (the app was closed meanwhile): the new one counts
                continue;
            }
            throw new AuthException("Someone else is waiting with this code. Try again in a few minutes.", HttpStatus.CONFLICT);
        }

        OpaqueTokenService.IssuedToken claim = opaqueTokens.issue(pairingProperties.getTokenPepper());
        DeviceConnectRequest request = new DeviceConnectRequest();
        request.setId(UUID.randomUUID());
        request.setRestaurantId(restaurantId);
        request.setDeviceId(device.getId());
        request.setPairingTokenId(token.getId());
        request.setRequestedBy(askedBy);
        request.setCodeMadeBy(token.getCreatedBy());
        request.setAskingDeviceName(trimmed(askingDevice, 150));
        request.setAskingDeviceSystem(trimmed(askingSystem, 80));
        request.setAskingAddress(trimmed(address, 64));
        request.setClaimHash(claim.tokenHash());
        request.setStatus(DeviceConnectRequest.WAITING);
        request.setCreatedAt(now);
        request.setExpiresAt(now.plus(Duration.ofMinutes(waitMinutes)));
        requests.save(request);

        String asker = nameOf(askedBy);
        String where = StringUtils.hasText(request.getAskingDeviceName()) ? request.getAskingDeviceName() : "another device";
        if (token.getCreatedBy() != null) {
            notifications.publishCustom(NotificationTopic.DEVICE, NotificationMutationType.STATE_CHANGE, NotificationChannel.IN_APP,
                    NotificationPriority.HIGH, EVENT, restaurantId, null, token.getCreatedBy(), EVENT, request.getId(), askedBy,
                    where + " wants to become " + device.getName(),
                    asker + " typed your code for " + device.getName() + " on " + where + ". Agree or refuse within "
                            + waitMinutes + " minutes.");
        }
        accountAudit.inRestaurant(restaurantId, device.getId(), askedBy, "DEVICE_CONNECT_ASKED",
                asker + " typed the code for " + device.getName() + " on " + where
                        + (StringUtils.hasText(address) ? " (" + address + ")" : "") + ", waiting for "
                        + nameOf(token.getCreatedBy()) + " to agree");
        return new DeviceConnectDtos.Asked(view(request, device, now), claim.rawToken());
    }

    /** The request, for the device that asked or the person who made the code. */
    @Transactional(readOnly = true)
    public DeviceConnectDtos.RequestView get(UUID id, UUID restaurantId, UUID actor) {
        DeviceConnectRequest request = find(id, restaurantId);
        if (!actor.equals(request.getRequestedBy()) && !actor.equals(request.getCodeMadeBy())) throw notFound();
        return view(request, deviceOf(request), now());
    }

    /** The requests waiting for [actor] to answer: codes they made, typed on another device. */
    @Transactional(readOnly = true)
    public List<DeviceConnectDtos.RequestView> waitingFor(UUID restaurantId, UUID actor) {
        OffsetDateTime now = now();
        return requests.findByRestaurantIdAndCodeMadeByAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
                        restaurantId, actor, DeviceConnectRequest.WAITING, now).stream()
                .map(request -> view(request, deviceOf(request), now))
                .toList();
    }

    @Transactional
    public DeviceConnectDtos.RequestView agree(UUID id, UUID restaurantId, UUID actor) {
        DeviceConnectRequest request = waitingForAnswer(id, restaurantId, actor);
        OffsetDateTime now = now();
        request.setStatus(DeviceConnectRequest.AGREED);
        request.setDecidedAt(now);
        request.setExpiresAt(now.plus(Duration.ofMinutes(claimMinutes)));
        Device device = deviceOf(request);
        accountAudit.inRestaurant(restaurantId, device.getId(), actor, "DEVICE_CONNECT_AGREED",
                nameOf(actor) + " agreed that " + askingName(request) + " becomes " + device.getName());
        return view(request, device, now);
    }

    /** Refused: the code ends too, so whoever typed it can't try again with it. */
    @Transactional
    public DeviceConnectDtos.RequestView refuse(UUID id, UUID restaurantId, UUID actor) {
        DeviceConnectRequest request = waitingForAnswer(id, restaurantId, actor);
        OffsetDateTime now = now();
        request.setStatus(DeviceConnectRequest.REFUSED);
        request.setDecidedAt(now);
        pairingTokens.findById(request.getPairingTokenId()).ifPresent(token -> {
            if (token.getUsedAt() == null && token.getRevokedAt() == null) {
                token.setRevokedAt(now);
                pairingTokens.save(token);
            }
        });
        Device device = deviceOf(request);
        accountAudit.inRestaurant(restaurantId, device.getId(), actor, "DEVICE_CONNECT_REFUSED",
                nameOf(actor) + " refused " + askingName(request) + " as " + device.getName() + " (the code was ended)");
        return view(request, device, now);
    }

    /** The device that asked stops waiting. */
    @Transactional
    public DeviceConnectDtos.RequestView cancel(UUID id, UUID restaurantId, UUID actor) {
        DeviceConnectRequest request = find(id, restaurantId);
        if (!actor.equals(request.getRequestedBy())) throw notFound();
        OffsetDateTime now = now();
        String state = request.stateAt(now);
        if (DeviceConnectRequest.WAITING.equals(state) || DeviceConnectRequest.AGREED.equals(state)) {
            request.setStatus(DeviceConnectRequest.CANCELLED);
        }
        return view(request, deviceOf(request), now);
    }

    /** Agreed: the device that asked takes the connection (its claim proves it's that device, not just the same person). */
    @Transactional(noRollbackFor = pos.pos.device.network.OutsideNetworkException.class)
    public DevicePairingRedeemResponse claim(UUID id, UUID restaurantId, UUID actor, String rawClaim, String address) {
        DeviceConnectRequest request = find(id, restaurantId);
        if (!actor.equals(request.getRequestedBy()) || !sameHash(request.getClaimHash(), rawClaim)) throw notFound();
        OffsetDateTime now = now();
        String state = request.stateAt(now);
        if (DeviceConnectRequest.WAITING.equals(state)) throw new AuthException("Not agreed yet.", HttpStatus.CONFLICT);
        if (DeviceConnectRequest.DONE.equals(state)) throw new AuthException("This device is already connected.", HttpStatus.CONFLICT);
        if (DeviceConnectRequest.REFUSED.equals(state)) throw new AuthException("The person who made the code refused it.", HttpStatus.CONFLICT);
        if (!DeviceConnectRequest.AGREED.equals(state)) throw new AuthException("This request has ended. Ask for a new code.", HttpStatus.CONFLICT);
        DevicePairingRedeemResponse connected = pairing.redeemAgreed(request.getPairingTokenId(), address, actor, restaurantId,
                request.getAskingDeviceName(), request.getAskingDeviceSystem());
        request.setStatus(DeviceConnectRequest.DONE);
        request.setDoneAt(now);
        return connected;
    }

    private DeviceConnectRequest waitingForAnswer(UUID id, UUID restaurantId, UUID actor) {
        DeviceConnectRequest request = find(id, restaurantId);
        if (!actor.equals(request.getCodeMadeBy())) {
            throw new AuthException("Only the person who made the code answers this.", HttpStatus.FORBIDDEN);
        }
        if (!DeviceConnectRequest.WAITING.equals(request.stateAt(now()))) {
            throw new AuthException("This request isn't waiting any more.", HttpStatus.CONFLICT);
        }
        return request;
    }

    private DeviceConnectRequest find(UUID id, UUID restaurantId) {
        return requests.findByIdAndRestaurantId(id, restaurantId).orElseThrow(this::notFound);
    }

    private Device deviceOf(DeviceConnectRequest request) {
        return devices.findById(request.getDeviceId()).orElseThrow(this::notFound);
    }

    private DeviceConnectDtos.RequestView view(DeviceConnectRequest request, Device device, OffsetDateTime now) {
        long left = Math.max(0, Duration.between(now, request.getExpiresAt()).getSeconds());
        String state = request.stateAt(now);
        return new DeviceConnectDtos.RequestView(
                request.getId(),
                state,
                device.getId(),
                device.getName(),
                device.getDeviceType().name(),
                nameOf(request.getRequestedBy()),
                request.getAskingDeviceName(),
                request.getAskingDeviceSystem(),
                request.getCodeMadeBy(),
                nameOf(request.getCodeMadeBy()),
                StringUtils.hasText(device.getAuthSecretHash()),
                request.getCreatedAt(),
                request.getExpiresAt(),
                DeviceConnectRequest.WAITING.equals(state) || DeviceConnectRequest.AGREED.equals(state) ? left : 0
        );
    }

    private boolean sameHash(String stored, String rawClaim) {
        if (!StringUtils.hasText(rawClaim)) return false;
        String hashed = opaqueTokens.hash(rawClaim, pairingProperties.getTokenPepper());
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), hashed.getBytes(StandardCharsets.UTF_8));
    }

    private String askingName(DeviceConnectRequest request) {
        return StringUtils.hasText(request.getAskingDeviceName()) ? request.getAskingDeviceName() : "another device";
    }

    private String nameOf(UUID userId) {
        if (userId == null) return "someone";
        return users.findById(userId).map(AccountAudit::nameOf).orElse("someone");
    }

    private static String trimmed(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private AuthException notFound() {
        return new AuthException("This request isn't there any more.", HttpStatus.NOT_FOUND);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
