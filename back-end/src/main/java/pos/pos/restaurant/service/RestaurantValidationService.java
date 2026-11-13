package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantCodeAlreadyExistsException;
import pos.pos.exception.restaurant.RestaurantOwnerNotFoundException;
import pos.pos.exception.restaurant.RestaurantSlugAlreadyExistsException;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.util.RestaurantFieldNormalizer;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Objects;
import java.util.UUID;

//checked
@Service
@RequiredArgsConstructor
public class RestaurantValidationService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    // Validates timezone, normalizes code/slug (or generates them from name), then asserts both are unique.
    // Pass restaurantId=null when creating — it checks that no other restaurant already uses this code/slug.
    // Pass the existing restaurantId when updating — same check, but skips comparing the restaurant against itself.
    public NormalizedRestaurantFields normalizeAndValidateFields(
            String code,
            String slug,
            String fallbackName,
            String timezone,
            UUID restaurantId
    ) {
        validateTimezone(timezone);

        String normalizedCode = RestaurantFieldNormalizer.normalizeCodeOrFallback(code, fallbackName);
        if (normalizedCode == null) {
            throw new AuthException("code is required", HttpStatus.BAD_REQUEST);
        }

        String normalizedSlug = RestaurantFieldNormalizer.normalizeSlugOrFallback(slug, fallbackName);
        if (normalizedSlug == null) {
            throw new AuthException("slug is required", HttpStatus.BAD_REQUEST);
        }

        assertUniqueCode(normalizedCode, restaurantId);
        assertUniqueSlug(normalizedSlug, restaurantId);

        return new NormalizedRestaurantFields(normalizedCode, normalizedSlug);
    }

    // Used for public registrations where no code/slug is provided by the user.
    // Generates a unique code+slug pair by appending a numeric suffix (e.g. burger-house-1, burger-house-2)
    // until a combination that doesn't exist in the DB is found. Fails after 9999 attempts.
    public NormalizedRestaurantFields normalizeAndGenerateUniqueRegistrationFields(
            String fallbackName,
            String timezone
    ) {
        validateTimezone(timezone);

        String baseCode = RestaurantFieldNormalizer.normalizeCodeOrFallback(null, fallbackName);
        if (baseCode == null) {
            throw new AuthException("code is required", HttpStatus.BAD_REQUEST);
        }

        String baseSlug = RestaurantFieldNormalizer.normalizeSlugOrFallback(null, fallbackName);
        if (baseSlug == null) {
            throw new AuthException("slug is required", HttpStatus.BAD_REQUEST);
        }

        for (int sequence = 1; sequence <= 9_999; sequence++) {
            String candidateCode = RestaurantFieldNormalizer.withCodeSequence(baseCode, sequence);
            String candidateSlug = RestaurantFieldNormalizer.withSlugSequence(baseSlug, sequence);

            // Archived restaurants keep their code and web address, so they are skipped too.
            if (!restaurantRepository.existsByCode(candidateCode)
                    && !restaurantRepository.existsBySlug(candidateSlug)) {
                return new NormalizedRestaurantFields(candidateCode, candidateSlug);
            }
        }

        throw new AuthException("Unable to generate a unique restaurant identifier", HttpStatus.CONFLICT);
    }

    // Ensures isActive and status don't contradict each other (e.g. isActive=true but status=SUSPENDED is invalid).
    public void validateStatusConsistency(Boolean isActive, RestaurantStatus status) {
        if (Boolean.TRUE.equals(isActive) && status != RestaurantStatus.ACTIVE) {
            throw new AuthException("Non-active restaurant statuses must have isActive=false", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.FALSE.equals(isActive) && status == RestaurantStatus.ACTIVE) {
            throw new AuthException("ACTIVE restaurants must have isActive=true", HttpStatus.BAD_REQUEST);
        }
    }

    // Blocks admin update/delete operations on restaurants that are still in the registration review flow (PENDING/REJECTED).
    public void validateManageableStatus(RestaurantStatus status) {
        if (status == RestaurantStatus.PENDING || status == RestaurantStatus.REJECTED) {
            throw new AuthException("PENDING and REJECTED statuses are reserved for registration review", HttpStatus.BAD_REQUEST);
        }
    }

    // Guards against invalid transitions: ARCHIVED is terminal and cannot be modified;
    // PENDING/REJECTED are reserved for the registration review flow only.
    public void validateStatusTransition(RestaurantStatus current, RestaurantStatus requested) {
        if (current == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
        if (requested == RestaurantStatus.PENDING || requested == RestaurantStatus.REJECTED) {
            throw new AuthException("PENDING and REJECTED statuses are reserved for registration review", HttpStatus.BAD_REQUEST);
        }
    }

    public UUID validateOwnerUser(UUID ownerUserId, UUID restaurantId, RestaurantStatus status) {
        // A restaurant may have no owner yet; it is added afterwards (POST /restaurants/{id}/owner).
        if (ownerUserId == null) {
            return null;
        }

        User owner = userRepository.findByIdAndDeletedAtIsNull(ownerUserId)
                .orElseThrow(RestaurantOwnerNotFoundException::new);

        if (!owner.isActive()) {
            throw new AuthException("Owner user must be active", HttpStatus.BAD_REQUEST);
        }

        if (owner.getRestaurantId() != null && !Objects.equals(owner.getRestaurantId(), restaurantId)) {
            throw new AuthException("Owner user is already assigned to another restaurant", HttpStatus.BAD_REQUEST);
        }

        return owner.getId();
    }

    // Rejects timezones that are not valid IANA identifiers (e.g. "America/New_York" is valid, "EST" is not).
    private void validateTimezone(String timezone) {
        if (!RestaurantFieldNormalizer.isValidTimezone(RestaurantFieldNormalizer.normalizeTimezone(timezone))) {
            throw new AuthException("timezone must be a valid IANA identifier", HttpStatus.BAD_REQUEST);
        }
    }

    // On create (restaurantId=null): checks no restaurant has this code, archived ones included (their codes stay
    // reserved). On update: the same, leaving out the restaurant being updated.
    private void assertUniqueCode(String code, UUID restaurantId) {
        boolean exists = restaurantId == null
                ? restaurantRepository.existsByCode(code)
                : restaurantRepository.existsByCodeAndIdNot(code, restaurantId);
        if (!exists) {
            return;
        }
        if (restaurantRepository.existsByCodeAndDeletedAtIsNotNull(code)) {
            throw new RestaurantCodeAlreadyExistsException(
                    "This code belonged to a removed restaurant and stays reserved. Choose another code.");
        }
        throw new RestaurantCodeAlreadyExistsException();
    }

    // Same as assertUniqueCode but for slug — the URL-friendly identifier (e.g. "burger-house").
    private void assertUniqueSlug(String slug, UUID restaurantId) {
        boolean exists = restaurantId == null
                ? restaurantRepository.existsBySlug(slug)
                : restaurantRepository.existsBySlugAndIdNot(slug, restaurantId);
        if (!exists) {
            return;
        }
        if (restaurantRepository.existsBySlugAndDeletedAtIsNotNull(slug)) {
            throw new RestaurantSlugAlreadyExistsException(
                    "This web address belonged to a removed restaurant and stays reserved. Choose another one.");
        }
        throw new RestaurantSlugAlreadyExistsException();
    }

    public record NormalizedRestaurantFields(String code, String slug) {
    }
}
