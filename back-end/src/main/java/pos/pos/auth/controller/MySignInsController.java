package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in person's own sign-ins of the last 30 days (user, 2026-11-18: Profile → Security, "make it with pages"), so
 * they notice when someone else tried their password: a page of tries (when, worked or why not, which device, from where)
 * and the counts for the 30 days.
 */
@Tag(name = "Authentication / Profile")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MySignInsController {

    private final AuthLoginAttemptRepository attempts;

    /** One try: [result] is SIGNED_IN or the failure reason (INVALID_CREDENTIALS, ACCOUNT_LOCKED, DEVICE_RATE_LIMITED…). */
    public record SignInView(UUID id, OffsetDateTime at, String result, String deviceId, String deviceName, String ipAddress) {
    }

    /** A page of tries, newest first, with how many there are and the 30 days' counts by result. */
    public record SignInPage(List<SignInView> items, long total, int page, int size, Map<String, Long> counts) {
    }

    @GetMapping("/sign-ins")
    @Operation(summary = "My sign-ins of the last 30 days, a page at a time")
    @Transactional(readOnly = true)
    public SignInPage mySignIns(Authentication authentication,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "8") int size) {
        UUID userId = ((AuthenticatedUser) authentication.getPrincipal()).getId();
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        int safeSize = Math.max(1, Math.min(size, 50));
        Page<AuthLoginAttempt> found = attempts.findByUserIdAndAttemptedAtAfterOrderByAttemptedAtDesc(userId, after,
                PageRequest.of(Math.max(0, page), safeSize));
        Map<String, Long> counts = new LinkedHashMap<>();
        attempts.countResultsForUser(userId, after).forEach(row -> counts.merge(
                Boolean.TRUE.equals(row[0]) ? "SIGNED_IN" : row[1] != null ? row[1].toString() : "FAILED", ((Number) row[2]).longValue(), Long::sum));
        return new SignInPage(found.getContent().stream()
                .map(a -> new SignInView(a.getId(), a.getAttemptedAt(),
                        a.isSuccess() ? "SIGNED_IN" : a.getFailureReason() != null ? a.getFailureReason().name() : "FAILED",
                        a.getDeviceId(), a.getDeviceName(), a.getIpAddress()))
                .toList(), found.getTotalElements(), found.getNumber(), safeSize, counts);
    }
}
