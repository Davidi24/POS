package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.auth.dto.ChangeMyEmailRequest;
import pos.pos.auth.dto.MyProfileResponse;
import pos.pos.auth.dto.UpdateMyDetailsRequest;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.config.properties.EmailVerificationProperties;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Profile (user, 2026-11-18: "add all things I need in profile"): a person changes their own name, phone and photo, and
 * their email through a link sent to the new address (with their password first). Username, role and restaurant stay
 * with their manager.
 */
@Service
@RequiredArgsConstructor
public class MyProfileService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuthEmailVerificationTokenRepository emailTokens;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;
    private final pos.pos.user.repository.UsernameChangeRepository usernameChanges;
    private final AuthMailService authMailService;
    private final AccountAudit accountAudit;

    /** Usernames nobody may take, so no one can pass for someone important. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of("admin", "administrator", "root", "system", "support", "help",
            "security", "superadmin", "super.admin", "super-admin", "owner", "co-owner", "manager", "staff", "pos", "info", "noreply", "no-reply");
    private static final int USERNAME_CHANGES_PER_30_DAYS = 3;

    @Transactional(readOnly = true)
    public MyProfileResponse profile(UUID userId) {
        return view(requireUser(userId));
    }

    @Transactional
    public MyProfileResponse updateDetails(UUID userId, UpdateMyDetailsRequest request) {
        User user = requireUser(userId);
        String phone = request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim();
        String normalizedPhone = NormalizationUtils.normalizePhone(phone);
        if (normalizedPhone != null && !normalizedPhone.equals(user.getNormalizedPhone())
                && userRepository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull(normalizedPhone, userId)) {
            throw new AuthException("That phone number is used by another account.", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(normalizedPhone, user.getNormalizedPhone())) {
            user.setPhoneVerified(false);
        }
        String before = AccountAudit.nameOf(user);
        java.util.List<String> changes = new java.util.ArrayList<>();
        if (!Objects.equals(user.getFirstName(), request.getFirstName().trim()) || !Objects.equals(user.getLastName(), request.getLastName().trim())) {
            changes.add("changed their name from " + before + " to " + (request.getFirstName().trim() + " " + request.getLastName().trim()).trim());
        }
        if (!Objects.equals(user.getPhone(), phone)) {
            changes.add(phone == null ? "removed their phone" : "changed their phone to " + phone);
        }
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(phone);
        user.setNormalizedPhone(normalizedPhone);
        User saved = userRepository.save(user);
        if (!changes.isEmpty()) {
            accountAudit.about(saved, userId, "PROFILE_CHANGED", before + " " + String.join(" and ", changes));
        }
        return view(saved);
    }

    @Transactional
    public MyProfileResponse requestEmailChange(UUID userId, ChangeMyEmailRequest request) {
        User user = requireUser(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // 400, not 401: a 401 would make the app try to refresh its sign-in.
        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthException("Your current password isn't right.", HttpStatus.BAD_REQUEST);
        }
        String newEmail = NormalizationUtils.normalizeLower(request.getNewEmail());
        if (newEmail.equals(user.getEmail())) {
            throw new AuthException("That's already your email.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(newEmail)) {
            throw new AuthException("That email is used by another account.", HttpStatus.CONFLICT);
        }
        if (emailTokens.existsByUserIdAndCreatedAtAfter(userId, now.minus(emailVerificationProperties.getRequestCooldown()))) {
            throw new TooManyRequestsException("An email was just sent. Wait a few minutes before asking again.");
        }
        emailVerificationService.requestEmailChange(user, newEmail, now);
        return view(user);
    }

    /**
     * Profile → change username (user, 2026-11-18): the current password first; 3–30 letters, numbers, ".", "-" or "_";
     * not a reserved name, not anyone's (removed accounts too), not one someone let go of in the last 30 days; at most 3
     * changes in 30 days. The person gets an email, the restaurant's audit log a line, and Users shows "was …" for 30 days.
     */
    @Transactional
    public MyProfileResponse changeUsername(UUID userId, pos.pos.auth.dto.ChangeMyUsernameRequest request) {
        User user = requireUser(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthException("Your current password isn't right.", HttpStatus.BAD_REQUEST);
        }
        String username = NormalizationUtils.normalizeLower(request.getUsername());
        if (username == null || !username.matches("[a-z0-9._-]{3,30}") || !username.matches(".*[a-z].*")) {
            throw new AuthException("Use 3–30 letters, numbers, dots, dashes or underscores, with at least one letter.", HttpStatus.BAD_REQUEST);
        }
        if (username.equals(user.getUsername())) {
            throw new AuthException("That's already your username.", HttpStatus.BAD_REQUEST);
        }
        if (RESERVED.contains(username)) {
            throw new AuthException("That username can't be used. Choose another one.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByUsername(username) || usernameChanges.existsByOldUsernameAndChangedAtAfterAndUserIdNot(username, now.minusDays(30), userId)) {
            throw new AuthException("That username is taken.", HttpStatus.CONFLICT);
        }
        java.util.List<pos.pos.user.entity.UsernameChange> recent = usernameChanges.findByUserIdAndChangedAtAfterOrderByChangedAtAsc(userId, now.minusDays(30));
        if (recent.size() >= USERNAME_CHANGES_PER_30_DAYS) {
            OffsetDateTime next = recent.get(recent.size() - USERNAME_CHANGES_PER_30_DAYS).getChangedAt().plusDays(30);
            throw new TooManyRequestsException("You can change your username 3 times in 30 days. Next on "
                    + next.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) + ".");
        }
        String old = user.getUsername();
        user.setUsername(username);
        userRepository.saveAndFlush(user);
        usernameChanges.save(pos.pos.user.entity.UsernameChange.builder().userId(userId).oldUsername(old == null ? "" : old)
                .newUsername(username).changedAt(now).build());
        // In the restaurant's audit log (Admin Hub → Audit log), with the other account lines.
        accountAudit.about(user, userId, "USERNAME_CHANGED", AccountAudit.nameOf(user) + " changed their username from @" + old + " to @" + username);
        authMailService.sendUsernameChangedNotice(user, old);
        return view(user);
    }

    /** The app's language this person uses (en, it, es, sq), picked in Profile; the app switches to it at sign-in. */
    @Transactional
    public void changeLanguage(UUID userId, String language) {
        String code = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("en", "it", "es", "sq").contains(code)) {
            throw new AuthException("Choose English, Italiano, Español or Shqip.", HttpStatus.BAD_REQUEST);
        }
        User user = requireUser(userId);
        if (code.equals(user.getLanguage())) return;
        user.setLanguage(code);
        userRepository.save(user);
        String name = switch (code) { case "it" -> "Italiano"; case "es" -> "Español"; case "sq" -> "Shqip"; default -> "English"; };
        accountAudit.about(user, userId, "PROFILE_CHANGED", AccountAudit.nameOf(user) + " changed their language to " + name);
    }

    @Transactional
    public MyProfileResponse cancelEmailChange(UUID userId) {
        User user = requireUser(userId);
        emailTokens.deleteByUserId(userId);
        accountAudit.about(user, userId, "EMAIL_CHANGE_CANCELLED", AccountAudit.nameOf(user) + " cancelled their email change");
        return view(user);
    }

    @Transactional
    public MyProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        User user = requireUser(userId);
        MenuImageUploads.CheckedPhoto photo = MenuImageUploads.check(file);
        // Kept under the person's restaurant, or under their own id for people who belong to none (super admins).
        UUID folder = user.getRestaurantId() != null ? user.getRestaurantId() : user.getId();
        String key = imageStorage.store(folder, photo.content(), photo.contentType(), photo.extension());
        imageReleaser.discardOnRollback(key);
        return replacePhoto(user, imageStorage.urlFor(key));
    }

    @Transactional
    public MyProfileResponse removePhoto(UUID userId) {
        return replacePhoto(requireUser(userId), null);
    }

    private MyProfileResponse replacePhoto(User user, String url) {
        String previous = user.getAvatarUrl();
        user.setAvatarUrl(url);
        User saved = userRepository.saveAndFlush(user);
        if (previous != null && !previous.equals(url)) {
            imageReleaser.releaseAfterCommit(previous);
        }
        if (url == null && previous != null) {
            accountAudit.about(saved, saved.getId(), "PHOTO_REMOVED", AccountAudit.nameOf(saved) + " removed their photo");
        } else if (url != null) {
            accountAudit.about(saved, saved.getId(), previous == null ? "PHOTO_ADDED" : "PHOTO_CHANGED",
                    AccountAudit.nameOf(saved) + (previous == null ? " added a photo" : " changed their photo"));
        }
        return view(saved);
    }

    private MyProfileResponse view(User user) {
        String pending = emailTokens.findTopByUserIdAndNewEmailIsNotNullAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                user.getId(), OffsetDateTime.now(ZoneOffset.UTC)).map(t -> t.getNewEmail()).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        java.util.List<pos.pos.user.entity.UsernameChange> recent = usernameChanges.findByUserIdAndChangedAtAfterOrderByChangedAtAsc(user.getId(), now.minusDays(30));
        int left = Math.max(0, USERNAME_CHANGES_PER_30_DAYS - recent.size());
        OffsetDateTime next = left > 0 ? null : recent.get(recent.size() - USERNAME_CHANGES_PER_30_DAYS).getChangedAt().plusDays(30);
        return new MyProfileResponse(user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail(), user.isEmailVerified(),
                pending, user.getPhone(), user.isPhoneVerified(), user.getAvatarUrl(), left, next);
    }

    private User requireUser(UUID userId) {
        return userRepository.findActiveById(userId).orElseThrow(UserNotFoundException::new);
    }
}
