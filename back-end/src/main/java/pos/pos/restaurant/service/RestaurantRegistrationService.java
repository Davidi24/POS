package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.exception.restaurant.RestaurantRegistrationNotPendingException;
import pos.pos.restaurant.dto.RestaurantApplicationReceiptResponse;
import pos.pos.restaurant.dto.RestaurantApplicationResponse;
import pos.pos.restaurant.dto.RestaurantApplicationStatus;
import pos.pos.restaurant.dto.RestaurantApplicationStatusResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantCardRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.util.RestaurantCurrencyPrecision;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.security.service.PublicRequestRateLimiter;
import pos.pos.user.service.UserIdentityService;
import pos.pos.utils.NormalizationUtils;
import pos.pos.utils.PageableUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Public restaurant applications: anyone can apply to open a restaurant, a super admin approves or rejects it, and the
 * applicant follows it with the private code they got when applying. Approving creates the owner's account (with an
 * invitation to set a password) and the first branch.
 */
@Service
@RequiredArgsConstructor
public class RestaurantRegistrationService {

    private static final String TOO_MANY_APPLICATIONS = "Too many applications were sent from here. Try again later.";
    private static final String TOO_MANY_STATUS_CHECKS = "Too many checks from here. Wait a few minutes and try again.";

    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final RestaurantCardRepository restaurantCardRepository;
    private final RestaurantMapper restaurantMapper;
    private final RestaurantValidationService restaurantValidationService;
    private final RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;
    private final RestaurantScopeService restaurantScopeService;
    private final ActorScopeService actorScopeService;
    private final UserIdentityService userIdentityService;
    private final PublicRequestRateLimiter rateLimiter;
    private final RestaurantApplicationCodes applicationCodes;
    private final RestaurantApplicationMailService applicationMail;

    // On: anyone can apply, and a super admin reviews every application before the restaurant exists.
    @Value("${app.restaurants.public-registration-enabled:true}")
    private boolean publicRegistrationEnabled = true;

    @Value("${app.restaurants.applications.max-per-ip:5}")
    private int maxApplicationsPerIp = 5;

    @Value("${app.restaurants.applications.ip-window-minutes:60}")
    private long ipWindowMinutes = 60;

    @Value("${app.restaurants.applications.max-per-email:3}")
    private int maxApplicationsPerEmail = 3;

    @Value("${app.restaurants.applications.email-window-hours:24}")
    private long emailWindowHours = 24;

    @Value("${app.restaurants.applications.status-checks-per-ip:30}")
    private int statusChecksPerIp = 30;

    @Value("${app.restaurants.applications.status-window-minutes:10}")
    private long statusWindowMinutes = 10;

    /**
     * Sends an application (no sign-in). It waits as a PENDING restaurant until a super admin reviews it; nobody's
     * account exists yet. The answer carries the private code for checking on it, which is also emailed.
     */
    @Transactional
    public RestaurantApplicationReceiptResponse registerRestaurant(RestaurantRegistrationRequest request, String clientIp) {
        assertPublicRegistrationEnabled();
        String ownerEmail = NormalizationUtils.normalizeLower(request.getOwner().getEmail());
        rateLimiter.check("APPLY_IP", clientIp, maxApplicationsPerIp, Duration.ofMinutes(ipWindowMinutes), TOO_MANY_APPLICATIONS);
        rateLimiter.check("APPLY_EMAIL", ownerEmail, maxApplicationsPerEmail, Duration.ofHours(emailWindowHours), TOO_MANY_APPLICATIONS);

        requireKnownCurrency(request.getCurrency());
        UserIdentityService.NormalizedUserIdentity identity = userIdentityService.normalizeAndAssertUnique(
                request.getOwner().getEmail(),
                request.getOwner().getUsername(),
                request.getOwner().getPhone()
        );
        if (restaurantRepository.existsByStatusAndPendingOwnerEmailAndDeletedAtIsNull(RestaurantStatus.PENDING, identity.email())) {
            throw new AuthException("An application with this email is already waiting for review. Check it with your application code.",
                    HttpStatus.CONFLICT);
        }
        if (restaurantRepository.existsByStatusAndPendingOwnerUsernameAndDeletedAtIsNull(RestaurantStatus.PENDING, identity.username())) {
            throw new AuthException("An application with this username is already waiting for review. Choose another username.",
                    HttpStatus.CONFLICT);
        }

        RestaurantValidationService.NormalizedRestaurantFields normalizedFields =
                restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields(
                        request.getName(),
                        request.getTimezone()
                );

        Restaurant restaurant = restaurantMapper.toPendingRegistrationEntity(
                request,
                normalizedFields.code(),
                normalizedFields.slug()
        );
        String code = applicationCodes.newCode();
        restaurant.setApplicationCodeHash(applicationCodes.hash(code));
        restaurant.setApplicationSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Restaurant saved = restaurantRepository.save(restaurant);
        Restaurant application = saved != null ? saved : restaurant;
        applicationMail.received(application, code);
        return new RestaurantApplicationReceiptResponse(
                application.getId(),
                application.getName(),
                RestaurantApplicationStatus.PENDING,
                code,
                application.getPendingOwnerEmail(),
                application.getApplicationSubmittedAt()
        );
    }

    /**
     * Where an application stands, for whoever has its code (no sign-in). Works even when new applications are turned
     * off, so earlier applicants can still follow theirs. An unknown code answers 404.
     */
    @Transactional(readOnly = true)
    public RestaurantApplicationStatusResponse getRegistrationStatus(String code, String clientIp) {
        rateLimiter.check("APPLY_STATUS_IP", clientIp, statusChecksPerIp, Duration.ofMinutes(statusWindowMinutes), TOO_MANY_STATUS_CHECKS);
        String hash = applicationCodes.hash(code);
        Restaurant restaurant = hash == null ? null : restaurantRepository.findByApplicationCodeHash(hash)
                .filter(found -> found.getDeletedAt() == null || found.getStatus() != RestaurantStatus.PENDING)
                .orElse(null);
        if (restaurant == null) {
            throw new AuthException("No application has this code. Check that you typed it exactly as in the email.", HttpStatus.NOT_FOUND);
        }
        RestaurantApplicationStatus status = RestaurantApplicationStatus.of(restaurant.getStatus());
        return new RestaurantApplicationStatusResponse(
                restaurant.getId(),
                restaurant.getName(),
                status,
                status == RestaurantApplicationStatus.REJECTED ? restaurant.getRejectionReason() : null,
                submittedAt(restaurant),
                restaurant.getReviewedAt(),
                switch (status) {
                    case PENDING -> "Your application is waiting for review. We will email you when it is decided.";
                    case APPROVED -> "Your restaurant was approved. Check your email for the invitation to set your password.";
                    case REJECTED -> "Your application was not approved. You can apply again once the reason is sorted out.";
                }
        );
    }

    /** The applications for super admins: waiting ones oldest first, or rejected ones newest first. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantApplicationResponse> getApplications(
            Authentication authentication,
            String status,
            String search,
            Integer page,
            Integer size
    ) {
        assertPlatformAdmin(authentication);
        String normalized = NormalizationUtils.normalizeUpper(status);
        List<RestaurantStatus> statuses = switch (normalized == null ? "PENDING" : normalized) {
            case "PENDING" -> List.of(RestaurantStatus.PENDING);
            case "REJECTED" -> List.of(RestaurantStatus.REJECTED);
            case "ALL" -> List.of(RestaurantStatus.PENDING, RestaurantStatus.REJECTED);
            default -> throw new AuthException("status must be PENDING, REJECTED or ALL", HttpStatus.BAD_REQUEST);
        };
        Sort.Direction direction = statuses.size() == 1 && statuses.get(0) == RestaurantStatus.PENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        var pageable = PageableUtils.create(page, size, direction.name(), "createdAt", 20);
        Page<Restaurant> found = restaurantRepository.searchApplications(statuses, NormalizationUtils.normalizeLowerLike(search), pageable);
        List<RestaurantApplicationResponse> items = found.getContent().stream().map(this::toApplication).toList();
        return PageResponse.from(new PageImpl<>(items, pageable, found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public RestaurantApplicationResponse getApplication(Authentication authentication, UUID restaurantId) {
        assertPlatformAdmin(authentication);
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .filter(found -> found.getStatus() == RestaurantStatus.PENDING || found.getStatus() == RestaurantStatus.REJECTED)
                .orElseThrow(RestaurantNotFoundException::new);
        return toApplication(restaurant);
    }

    /**
     * Approves or rejects a waiting application (super admins who may create restaurants). Rejecting needs a reason,
     * which the applicant sees. Approving opens the restaurant with a first branch, puts it last on the restaurant
     * list, creates the owner's account and emails them an invitation to set a password.
     */
    @Transactional
    public RestaurantApplicationResponse reviewRegistration(
            Authentication authentication,
            UUID restaurantId,
            ReviewRestaurantRegistrationRequest request
    ) {
        restaurantScopeService.assertCanCreateRestaurant(authentication);
        // Locked until commit, so two reviewers can't both decide.
        Restaurant restaurant = restaurantScopeService.requireExistingRestaurant(restaurantId);
        if (restaurant.getStatus() != RestaurantStatus.PENDING) {
            throw new RestaurantRegistrationNotPendingException();
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (request.getDecision() == RestaurantRegistrationDecision.REJECT) {
            String reason = NormalizationUtils.normalize(request.getRejectionReason());
            if (reason == null) {
                throw new AuthException("Give the applicant a reason for the rejection", HttpStatus.BAD_REQUEST);
            }
            restaurantMapper.markRegistrationRejected(restaurant, reason, actorId);
            restaurant.setReviewedAt(now);
            restaurant.setReviewedBy(actorId);
            restaurantRepository.save(restaurant);
            applicationMail.rejected(restaurant);
            return toApplication(restaurant);
        }

        requireKnownCurrency(restaurant.getCurrency());
        RestaurantApplicationResponse.Applicant applicant = applicant(restaurant);
        var owner = restaurantOwnerProvisioningService.createAndInvitePendingOwner(
                restaurant,
                actorId,
                restaurant.getName()
        );
        restaurantMapper.markRegistrationApproved(restaurant, owner.getId(), actorId);
        restaurant.setRejectionReason(null);
        restaurant.setReviewedAt(now);
        restaurant.setReviewedBy(actorId);
        restaurantCardRepository.lockDisplayOrder();
        restaurant.setDisplayOrder(restaurantCardRepository.nextDisplayOrder());
        restaurantRepository.save(restaurant);
        if (branchRepository.countByRestaurantIdAndDeletedAtIsNull(restaurant.getId()) == 0) {
            branchRepository.save(firstBranch(restaurant, actorId));
        }
        RestaurantApplicationResponse approved = toApplication(restaurant);
        // The applicant's details were the account's; keep showing them on the answer.
        return new RestaurantApplicationResponse(approved.id(), approved.name(), approved.legalName(), approved.description(),
                approved.email(), approved.phone(), approved.website(), approved.currency(), approved.timezone(), approved.status(),
                approved.rejectionReason(), approved.submittedAt(), approved.reviewedAt(), approved.reviewedBy(), applicant);
    }

    private Branch firstBranch(Restaurant restaurant, UUID actorId) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName("Main");
        branch.setCode("MAIN");
        branch.setActive(true);
        branch.setStatus(BranchStatus.ACTIVE);
        branch.setCreatedBy(actorId);
        branch.setUpdatedBy(actorId);
        return branch;
    }

    private RestaurantApplicationResponse toApplication(Restaurant restaurant) {
        return new RestaurantApplicationResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getLegalName(),
                restaurant.getDescription(),
                restaurant.getEmail(),
                restaurant.getPhone(),
                restaurant.getWebsite(),
                restaurant.getCurrency(),
                restaurant.getTimezone(),
                RestaurantApplicationStatus.of(restaurant.getStatus()),
                restaurant.getRejectionReason(),
                submittedAt(restaurant),
                restaurant.getReviewedAt(),
                restaurant.getReviewedBy(),
                applicant(restaurant)
        );
    }

    private static RestaurantApplicationResponse.Applicant applicant(Restaurant restaurant) {
        return new RestaurantApplicationResponse.Applicant(
                restaurant.getPendingOwnerFirstName(),
                restaurant.getPendingOwnerLastName(),
                restaurant.getPendingOwnerEmail(),
                restaurant.getPendingOwnerUsername(),
                restaurant.getPendingOwnerPhone()
        );
    }

    private static OffsetDateTime submittedAt(Restaurant restaurant) {
        return restaurant.getApplicationSubmittedAt() != null ? restaurant.getApplicationSubmittedAt() : restaurant.getCreatedAt();
    }

    private void assertPublicRegistrationEnabled() {
        if (!publicRegistrationEnabled) {
            throw new AuthException("Restaurant applications are closed. Ask us to set up your restaurant.", HttpStatus.FORBIDDEN);
        }
    }

    private void assertPlatformAdmin(Authentication authentication) {
        if (!actorScopeService.resolve(authentication).superAdmin()) {
            throw new AuthException("Only the Super Admin and Co-Super-Admins review restaurant applications", HttpStatus.FORBIDDEN);
        }
    }

    // ISO 4217 codes only (EUR, ALL, USD...), so prices and receipts always show a real currency.
    private static void requireKnownCurrency(String currency) {
        try {
            RestaurantCurrencyPrecision.scale(NormalizationUtils.normalizeUpper(currency));
        } catch (RuntimeException ex) {
            throw new AuthException("currency must be a 3-letter ISO code such as EUR, ALL or USD", HttpStatus.BAD_REQUEST);
        }
    }
}
