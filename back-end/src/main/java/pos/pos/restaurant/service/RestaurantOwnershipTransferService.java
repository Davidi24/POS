package pos.pos.restaurant.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.mail.EmailKind;
import pos.pos.mail.EmailOutboxService;
import pos.pos.restaurant.dto.OwnerCandidateResponse;
import pos.pos.restaurant.dto.RestaurantOwnerTransferResponse;
import pos.pos.restaurant.dto.TransferRestaurantOwnerRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.utils.NormalizationUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The Super Admin gives a restaurant to a new owner: someone already in the POS, or a new person who is invited. The
 * former owner loses the restaurant: their owner role goes, their account is switched off (kept for the history),
 * they are signed out everywhere and their unused account links stop working. The restaurant, its staff, sales and
 * every other record stay as they are; the change itself is recorded.
 */
@Service
public class RestaurantOwnershipTransferService {

    private static final Set<String> PLATFORM_ROLES = Set.of(AppRole.SUPER_ADMIN.name(), AppRole.CO_SUPER_ADMIN.name());
    private static final int MAX_CANDIDATES = 50;

    private final JdbcTemplate jdbc;
    private final String schema;
    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final RestaurantOwnerProvisioningService provisioningService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RoleHierarchyService roleHierarchyService;
    private final ActorScopeService actorScopeService;
    private final UserSessionRepository sessionRepository;
    private final AuthPasswordResetTokenRepository passwordLinks;
    private final EmailOutboxService outbox;
    private final SettingsAuditService auditService;

    public RestaurantOwnershipTransferService(
            JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema,
            RestaurantScopeService restaurantScopeService,
            RestaurantRepository restaurantRepository,
            BranchRepository branchRepository,
            RestaurantOwnerProvisioningService provisioningService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            RoleHierarchyService roleHierarchyService,
            ActorScopeService actorScopeService,
            UserSessionRepository sessionRepository,
            AuthPasswordResetTokenRepository passwordLinks,
            EmailOutboxService outbox,
            SettingsAuditService auditService
    ) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
        this.restaurantScopeService = restaurantScopeService;
        this.restaurantRepository = restaurantRepository;
        this.branchRepository = branchRepository;
        this.provisioningService = provisioningService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.roleHierarchyService = roleHierarchyService;
        this.actorScopeService = actorScopeService;
        this.sessionRepository = sessionRepository;
        this.passwordLinks = passwordLinks;
        this.outbox = outbox;
        this.auditService = auditService;
    }

    @Transactional
    public RestaurantOwnerTransferResponse transfer(Authentication authentication, UUID restaurantId, TransferRestaurantOwnerRequest request) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            // Others learn nothing about a restaurant they can't see.
            restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
            throw new AuthException("Only the Super Admin can transfer a restaurant's ownership", HttpStatus.FORBIDDEN);
        }
        if ((request.newOwnerUserId() == null) == (request.newOwner() == null)) {
            throw new AuthException("Choose either someone already in the POS or a new person to invite", HttpStatus.BAD_REQUEST);
        }
        // Locked until commit: a second transfer, a removal or an edit of the restaurant waits for this one.
        Restaurant restaurant = restaurantScopeService.requireExistingRestaurant(restaurantId);
        if (restaurant.getStatus() == RestaurantStatus.PENDING || restaurant.getStatus() == RestaurantStatus.REJECTED) {
            throw new AuthException("Review the application first; a restaurant that isn't approved has no owner to transfer", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(request.expectedVersion(), restaurant.getVersion())) {
            throw new AuthException("The restaurant changed since you opened it. Review the owner again before transferring.", HttpStatus.CONFLICT);
        }
        User previousOwner = restaurant.getOwnerId() == null ? null
                : userRepository.findByIdAndDeletedAtIsNull(restaurant.getOwnerId()).orElse(null);
        if (previousOwner == null) {
            throw new AuthException("This restaurant has no owner yet. Use Add owner instead.", HttpStatus.CONFLICT);
        }

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String reason = NormalizationUtils.normalize(request.reason());

        boolean invited = request.newOwner() != null;
        User newOwner = invited
                ? provisioningService.createAndInviteOwner(request.newOwner(), restaurantId, actorId, restaurant.getName())
                : promoteExistingUser(restaurant, request.newOwnerUserId(), previousOwner.getId(), actorId);

        // Only a person who belongs to this restaurant (and nowhere else) is switched off; a platform admin or the
        // owner of another restaurant only stops owning this one.
        boolean deactivate = Objects.equals(previousOwner.getRestaurantId(), restaurantId)
                && !hasPlatformRole(previousOwner.getId())
                && !restaurantRepository.existsByOwnerIdAndIdNotAndDeletedAtIsNull(previousOwner.getId(), restaurantId);
        int sessionsRevoked = 0;
        if (deactivate) {
            sessionsRevoked = removeAccess(restaurant, previousOwner, actorId, now);
        }

        restaurant.setOwnerId(newOwner.getId());
        restaurant.setUpdatedBy(actorId);
        restaurantRepository.saveAndFlush(restaurant);

        UUID transferId = UuidCreator.getTimeOrdered();
        jdbc.update("insert into " + schema + "restaurant_owner_transfers (id, restaurant_id, previous_owner_id, new_owner_id,"
                        + " new_owner_invited, previous_owner_deactivated, sessions_revoked, reason, transferred_by, transferred_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transferId, restaurantId, previousOwner.getId(), newOwner.getId(), invited, deactivate, sessionsRevoked, reason,
                actorId, Timestamp.from(now.toInstant()));
        auditService.log(restaurant, null, "RESTAURANT", restaurantId, "OWNER_TRANSFER",
                truncate("Ownership transferred from " + fullName(previousOwner) + " to " + fullName(newOwner)
                        + (invited ? " (invited)" : "") + "; former owner " + (deactivate ? "deactivated" : "kept") + ", sessions revoked="
                        + sessionsRevoked + (reason == null ? "" : "; reason: " + reason)), actorId);
        if (deactivate) {
            notifyPreviousOwner(previousOwner, restaurant);
        }
        return history(restaurantId, transferId).get(0);
    }

    /** Every change of the restaurant's owner, newest first (super admins). */
    @Transactional(readOnly = true)
    public List<RestaurantOwnerTransferResponse> transfers(Authentication authentication, UUID restaurantId) {
        requirePlatformRestaurant(authentication, restaurantId);
        return history(restaurantId, null);
    }

    /** Active staff of the restaurant who could become its owner, searched by name, email or username (at most 50). */
    @Transactional(readOnly = true)
    public List<OwnerCandidateResponse> candidates(Authentication authentication, UUID restaurantId, String search) {
        Restaurant restaurant = requirePlatformRestaurant(authentication, restaurantId);
        String like = NormalizationUtils.normalizeLowerLike(search);
        return jdbc.query("select u.id, u.first_name, u.last_name, u.email, u.username,"
                        + " (select string_agg(ro.code, ',' order by ro.rank desc) from " + schema + "user_roles ur join " + schema
                        + "roles ro on ro.id = ur.role_id where ur.user_id = u.id and ro.is_active and ro.deleted_at is null) roles"
                        + " from " + schema + "users u"
                        + " where u.restaurant_id = ? and u.deleted_at is null and u.is_active"
                        + " and u.id is distinct from ?"
                        + " and not exists (select 1 from " + schema + "user_roles ur join " + schema + "roles ro on ro.id = ur.role_id"
                        + "   where ur.user_id = u.id and ro.code in ('SUPER_ADMIN', 'CO_SUPER_ADMIN'))"
                        + " and (cast(? as text) is null or lower(u.first_name || ' ' || u.last_name) like ? or lower(u.email) like ?"
                        + "   or lower(u.username) like ?)"
                        + " order by lower(u.first_name), lower(u.last_name), u.id limit " + MAX_CANDIDATES,
                (row, i) -> new OwnerCandidateResponse(
                        row.getObject("id", UUID.class),
                        row.getString("first_name"),
                        row.getString("last_name"),
                        row.getString("email"),
                        row.getString("username"),
                        row.getString("roles") == null ? List.of() : Arrays.asList(row.getString("roles").split(","))
                ),
                restaurantId, restaurant.getOwnerId(), like, like, like, like);
    }

    private Restaurant requirePlatformRestaurant(Authentication authentication, UUID restaurantId) {
        return restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin and Co-Super-Admins see a restaurant's owners");
    }

    private User promoteExistingUser(Restaurant restaurant, UUID userId, UUID previousOwnerId, UUID actorId) {
        if (Objects.equals(userId, previousOwnerId)) {
            throw new AuthException("This person already owns the restaurant", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException("Choose an active person in the POS", HttpStatus.BAD_REQUEST));
        if (hasPlatformRole(user.getId())) {
            throw new AuthException("A Super Admin or Co-Super-Admin can't own a restaurant", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(user.getRestaurantId(), restaurant.getId())) {
            throw new AuthException("This person works at another restaurant. Choose someone from this restaurant or invite a new person.",
                    HttpStatus.CONFLICT);
        }
        if (restaurantRepository.existsByOwnerIdAndIdNotAndDeletedAtIsNull(user.getId(), restaurant.getId())) {
            throw new AuthException("This person already owns another restaurant", HttpStatus.CONFLICT);
        }
        Role ownerRole = ownerRole();
        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), ownerRole.getId())) {
            userRoleRepository.save(UserRole.builder()
                    .userId(user.getId())
                    .roleId(ownerRole.getId())
                    .assignedBy(actorId)
                    .build());
        }
        user.setUpdatedBy(actorId);
        userRepository.save(user);
        return user;
    }

    /** Takes the restaurant away from its former owner. Returns how many of their sessions were ended. */
    private int removeAccess(Restaurant restaurant, User previousOwner, UUID actorId, OffsetDateTime now) {
        UUID userId = previousOwner.getId();
        User locked = userRepository.findActiveByIdForUpdate(userId).orElse(previousOwner);
        Role ownerRole = ownerRole();
        userRoleRepository.findByUserId(userId).stream()
                .filter(assignment -> Objects.equals(assignment.getRoleId(), ownerRole.getId()))
                .forEach(userRoleRepository::delete);
        locked.setActive(false);
        locked.setStatus("INACTIVE");
        locked.setDefaultBranchId(null);
        locked.setUpdatedBy(actorId);
        userRepository.save(locked);
        branchRepository.clearManager(restaurant.getId(), userId, actorId, now);
        int sessions = sessionRepository.findActiveSessionsByUserId(userId, now).size();
        sessionRepository.revokeAllActiveSessionsByUserId(userId, now, SessionRevocationReason.OWNERSHIP_TRANSFERRED.name());
        passwordLinks.supersedeOpenLinks(userId, now);
        outbox.cancelAccountEmails(userId, "The restaurant was given to a new owner");
        return sessions;
    }

    private void notifyPreviousOwner(User previousOwner, Restaurant restaurant) {
        if (previousOwner.getEmail() == null) {
            return;
        }
        String first = NormalizationUtils.normalize(previousOwner.getFirstName());
        String body = """
                Hello %s,

                %s now has a new owner, so your access to it in the POS has ended and you were signed out.

                The restaurant's sales and other records are kept. If you think this is a mistake, contact support.
                """.formatted(first == null ? "there" : first, restaurant.getName());
        outbox.enqueue(new EmailOutboxService.OutgoingEmail(EmailKind.OWNERSHIP_TRANSFERRED, previousOwner.getEmail(),
                "Your access to " + restaurant.getName() + " has ended", body, previousOwner.getId(), restaurant.getId(), null));
    }

    private List<RestaurantOwnerTransferResponse> history(UUID restaurantId, UUID onlyId) {
        return jdbc.query("select t.*, "
                        + name("p", "previous_owner_id") + " previous_name, " + name("n", "new_owner_id") + " new_name, "
                        + name("a", "transferred_by") + " actor_name"
                        + " from " + schema + "restaurant_owner_transfers t"
                        + " where t.restaurant_id = ? and (cast(? as uuid) is null or t.id = ?)"
                        + " order by t.transferred_at desc, t.id desc",
                (row, i) -> new RestaurantOwnerTransferResponse(
                        row.getObject("id", UUID.class),
                        row.getObject("restaurant_id", UUID.class),
                        row.getObject("previous_owner_id", UUID.class),
                        row.getString("previous_name"),
                        row.getObject("new_owner_id", UUID.class),
                        row.getString("new_name"),
                        row.getBoolean("new_owner_invited"),
                        row.getBoolean("previous_owner_deactivated"),
                        row.getInt("sessions_revoked"),
                        row.getString("reason"),
                        row.getObject("transferred_by", UUID.class),
                        row.getString("actor_name"),
                        row.getObject("transferred_at", OffsetDateTime.class)
                ),
                restaurantId, onlyId, onlyId);
    }

    private String name(String alias, String column) {
        return "(select trim(coalesce(" + alias + ".first_name, '') || ' ' || coalesce(" + alias + ".last_name, '')) from "
                + schema + "users " + alias + " where " + alias + ".id = t." + column + ")";
    }

    private boolean hasPlatformRole(UUID userId) {
        return roleRepository.findActiveRoleCodesByUserId(userId).stream().anyMatch(PLATFORM_ROLES::contains);
    }

    private Role ownerRole() {
        return roleRepository.findByCode(AppRole.OWNER.name()).filter(Role::isActive).orElseThrow(RoleNotFoundException::new);
    }

    private static String fullName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? String.valueOf(user.getUsername()) : name;
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
