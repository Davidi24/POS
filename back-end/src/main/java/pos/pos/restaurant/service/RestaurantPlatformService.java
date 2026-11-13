package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.BranchCardPageResponse;
import pos.pos.restaurant.dto.BranchCardResponse;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantCardResponse;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantOwnerRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.RestaurantCardRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

/** What only the Super Admin and Co-Super-Admins see: every restaurant at a glance, and the owner's invitation. */
@Service
@RequiredArgsConstructor
public class RestaurantPlatformService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActorScopeService actorScopeService;
    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantCardRepository restaurantCardRepository;
    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final pos.pos.mail.EmailOutboxService emailOutboxService;

    /** [filter]: "open", "closed" or null for all. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantCardResponse> cards(Authentication authentication, String search, String filter, int page, int size) {
        if (!actorScopeService.resolve(authentication).superAdmin()) {
            throw new AuthException("Only the Super Admin sees every restaurant", HttpStatus.FORBIDDEN);
        }
        String normalizedFilter = NormalizationUtils.normalizeLower(filter);
        Boolean open = switch (normalizedFilter == null ? "all" : normalizedFilter) {
            case "all" -> null;
            case "open" -> true;
            case "closed" -> false;
            default -> throw new AuthException("filter must be open, closed or all", HttpStatus.BAD_REQUEST);
        };
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageNumber = Math.max(0, page);
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
        long offset = (long) pageNumber * pageSize;
        if (offset > Integer.MAX_VALUE) throw new AuthException("page is too large", HttpStatus.BAD_REQUEST);
        var items = restaurantCardRepository.cards(searchLike, open, pageSize, (int) offset);
        long total = restaurantCardRepository.count(searchLike, open);
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(pageNumber, pageSize), total));
    }

    /** The order of the restaurant cards: [restaurantIds] first, the rest after them. */
    @Transactional
    public void reorder(Authentication authentication, List<UUID> restaurantIds) {
        if (!actorScopeService.resolve(authentication).superAdmin()) {
            throw new AuthException("Only the Super Admin can change the order of the restaurants", HttpStatus.FORBIDDEN);
        }
        restaurantCardRepository.reorder(restaurantIds);
    }

    /** One page of the "Choose a branch" cards, open branches first; [search] matches the name, code, city or street. */
    @Transactional(readOnly = true)
    public BranchCardPageResponse branchCards(Authentication authentication, UUID restaurantId, String search, int page, int size) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageNumber = Math.max(0, page);
        long offset = (long) pageNumber * pageSize;
        if (offset > Integer.MAX_VALUE) throw new AuthException("page is too large", HttpStatus.BAD_REQUEST);
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
        List<BranchCardResponse> items = restaurantCardRepository.branchCards(restaurantId, searchLike, pageSize, (int) offset);
        long matching = restaurantCardRepository.countBranchCards(restaurantId, searchLike);
        long all = searchLike == null ? matching : restaurantCardRepository.countBranchCards(restaurantId, null);
        int totalPages = (int) ((matching + pageSize - 1) / pageSize);
        return new BranchCardPageResponse(items, pageNumber, pageSize, matching, totalPages, offset + items.size() < matching,
                all, restaurantCardRepository.countOpenBranches(restaurantId));
    }

    /** The second step of setting up a restaurant: its owner, who gets an email to set a password. */
    @Transactional
    public RestaurantResponse createOwner(Authentication authentication, UUID restaurantId, CreateRestaurantOwnerRequest request) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin can add the restaurant's owner");
        if (restaurant.getOwnerId() != null && userRepository.findByIdAndDeletedAtIsNull(restaurant.getOwnerId()).isPresent()) {
            throw new AuthException("This restaurant already has an owner", HttpStatus.CONFLICT);
        }
        User owner = restaurantOwnerProvisioningService.createAndInviteOwner(request, restaurantId,
                restaurantScopeService.currentUserId(authentication), restaurant.getName());
        restaurant.setOwnerId(owner.getId());
        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    /** Corrects the owner's name, phone or (while their invitation still waits) email; a new email gets a new invitation. */
    @Transactional
    public RestaurantResponse updateOwner(Authentication authentication, UUID restaurantId, UpdateRestaurantOwnerRequest request) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin can change the restaurant's owner");
        User owner = restaurant.getOwnerId() == null ? null : userRepository.findByIdAndDeletedAtIsNull(restaurant.getOwnerId()).orElse(null);
        if (owner == null) {
            throw new AuthException("This restaurant has no owner yet", HttpStatus.BAD_REQUEST);
        }
        String email = NormalizationUtils.normalizeLower(request.email());
        boolean emailChanged = !email.equals(owner.getEmail());
        if (emailChanged) {
            if (owner.isEmailVerified()) {
                throw new AuthException("The owner has already set up their account, so only they can change their email", HttpStatus.CONFLICT);
            }
            userRepository.findByEmailAndDeletedAtIsNull(email).filter(other -> !other.getId().equals(owner.getId())).ifPresent(other -> {
                throw new AuthException("Someone already uses this email", HttpStatus.CONFLICT);
            });
            owner.setEmail(email);
        }
        String phone = NormalizationUtils.normalize(request.phone());
        String normalizedPhone = NormalizationUtils.normalizePhone(phone);
        if (normalizedPhone != null && userRepository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull(normalizedPhone, owner.getId())) {
            throw new AuthException("Someone already uses this phone number", HttpStatus.CONFLICT);
        }
        owner.setFirstName(NormalizationUtils.normalize(request.firstName()));
        owner.setLastName(NormalizationUtils.normalize(request.lastName()));
        owner.setPhone(phone);
        owner.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        userRepository.save(owner);
        if (emailChanged) {
            passwordResetService.issueRestaurantOwnerInvite(owner, ClientLinkTarget.UNIVERSAL, restaurant.getName());
        }
        return restaurantMapper.toResponse(restaurant);
    }

    /** Sends the owner a new link to set their password, as long as they haven't set one yet. */
    @Transactional
    public void resendOwnerInvite(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin can send the owner's invitation");
        if (restaurant.getOwnerId() == null) {
            throw new AuthException("This restaurant has no owner yet", HttpStatus.BAD_REQUEST);
        }
        User owner = userRepository.findByIdAndDeletedAtIsNull(restaurant.getOwnerId())
                .orElseThrow(() -> new AuthException("The owner's account was removed", HttpStatus.CONFLICT));
        if (owner.isEmailVerified()) {
            throw new AuthException("The owner has already set their password. They can reset it from the sign-in screen.",
                    HttpStatus.CONFLICT);
        }
        passwordResetService.issueRestaurantOwnerInvite(owner, ClientLinkTarget.UNIVERSAL, restaurant.getName());
    }

    /** How the owner's invitation is doing: sent, waiting to be retried or failed, and whether its link still works. */
    @Transactional(readOnly = true)
    public pos.pos.restaurant.dto.OwnerInviteStatusResponse ownerInvite(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin sees the owner's invitation");
        User owner = restaurant.getOwnerId() == null ? null : userRepository.findByIdAndDeletedAtIsNull(restaurant.getOwnerId()).orElse(null);
        if (owner == null) {
            throw new AuthException("This restaurant has no owner yet", HttpStatus.NOT_FOUND);
        }
        var delivery = emailOutboxService.latest(owner.getId(), pos.pos.mail.EmailKind.OWNER_INVITE).orElse(null);
        var link = passwordResetService.latestInvite(owner).orElse(null);
        return new pos.pos.restaurant.dto.OwnerInviteStatusResponse(
                owner.getId(),
                owner.getEmail(),
                owner.isEmailVerified(),
                delivery == null ? null : delivery.status(),
                delivery == null ? 0 : delivery.attempts(),
                delivery == null ? null : delivery.queuedAt(),
                delivery == null ? null : delivery.sentAt(),
                delivery == null ? null : delivery.nextAttemptAt(),
                delivery == null ? null : delivery.lastError(),
                link == null ? null : link.status(),
                link == null ? null : link.expiresAt()
        );
    }
}
