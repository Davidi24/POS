package pos.pos.admins;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.service.UserAdminService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admins and requests to the Super Admin (user, 2026-11-19):
 * - Support admins (role CO_SUPER_ADMIN, shown as "Admin") are made by the Super Admin and take care of the restaurants
 *   given to them, or all of them; restaurants see them in Users and can write to them.
 * - Restaurant admins (role ADMIN) are made by the restaurant's owner and work only there.
 * - A restaurant asks the Super Admin to remove one of its owners (owners never remove each other), or for a support admin.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository admins;
    private final RoleHierarchyService roleHierarchyService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserAdminService userAdminService;

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminCard> adminCards(Authentication auth) {
        requireSuperAdmin(auth);
        return admins.adminCards();
    }

    @Transactional(readOnly = true)
    public AdminDtos.SupportAdminAccess access(Authentication auth, UUID adminId) {
        requireSuperAdmin(auth);
        User admin = supportAdmin(adminId);
        return new AdminDtos.SupportAdminAccess(admin.isAdminAllRestaurants(), admin.isAdminMayRemoveOwners(), admins.restaurantsOf(adminId));
    }

    @Transactional
    public AdminDtos.SupportAdminAccess updateAccess(Authentication auth, UUID adminId, AdminDtos.UpdateSupportAdminAccess request) {
        requireSuperAdmin(auth);
        User admin = supportAdmin(adminId);
        admin.setAdminAllRestaurants(request.allRestaurants());
        admin.setAdminMayRemoveOwners(request.mayRemoveOwners());
        userRepository.save(admin);
        admins.replaceRestaurants(adminId, request.allRestaurants() || request.restaurantIds() == null ? List.of() : request.restaurantIds().stream().distinct().toList(),
                roleHierarchyService.currentUserId(auth));
        return new AdminDtos.SupportAdminAccess(admin.isAdminAllRestaurants(), admin.isAdminMayRemoveOwners(), admins.restaurantsOf(adminId));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.SupportContact> supportAdmins(Authentication auth, UUID restaurantId) {
        requireRestaurant(auth, restaurantId);
        return admins.supportAdminsOf(restaurantId);
    }

    @Transactional
    public AdminDtos.PlatformRequest ask(Authentication auth, UUID restaurantId, AdminDtos.CreateRequest request) {
        requireRestaurant(auth, restaurantId);
        UUID actorId = roleHierarchyService.currentUserId(auth);
        if (!roleHierarchyService.hasRole(auth, "OWNER") && !roleHierarchyService.hasRole(auth, "CO_OWNER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the restaurant's owners can ask the Super Admin.");
        }
        UUID target = null;
        if ("REMOVE_OWNER".equals(request.kind())) {
            if (!roleHierarchyService.hasRole(auth, "OWNER")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an owner can ask to remove another owner.");
            }
            target = request.targetUserId();
            User person = target == null ? null : userRepository.findByIdAndDeletedAtIsNull(target).orElse(null);
            if (person == null || !restaurantId.equals(person.getRestaurantId()) || actorId.equals(target) || !rolesOf(target).contains("OWNER")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another owner of this restaurant.");
            }
        }
        if (admins.pendingExists(restaurantId, request.kind(), target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already asked. The Super Admin will answer soon.");
        }
        UUID id = UUID.randomUUID();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        admins.insertRequest(id, restaurantId, request.kind(), target, note, actorId, OffsetDateTime.now(ZoneOffset.UTC));
        return admins.request(id);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.PlatformRequest> restaurantRequests(Authentication auth, UUID restaurantId) {
        requireRestaurant(auth, restaurantId);
        return admins.requests(restaurantId, false);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.PlatformRequest> allRequests(Authentication auth, boolean pendingOnly) {
        requireSuperAdmin(auth);
        return admins.requests(null, pendingOnly);
    }

    /** The Super Admin answers; approving a removal removes that owner (after checking with them, user 2026-11-19). */
    @Transactional
    public AdminDtos.PlatformRequest decide(Authentication auth, UUID requestId, AdminDtos.Decision decision) {
        requireSuperAdmin(auth);
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        UUID actorId = roleHierarchyService.currentUserId(auth);
        String note = decision.note() == null || decision.note().isBlank() ? null : decision.note().trim();
        if (admins.decide(requestId, decision.approve(), actorId, note, OffsetDateTime.now(ZoneOffset.UTC)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already answered.");
        }
        if (decision.approve() && "REMOVE_OWNER".equals(request.kind()) && request.targetUserId() != null
                && userRepository.findByIdAndDeletedAtIsNull(request.targetUserId()).isPresent()) {
            userAdminService.removeApprovedBySuperAdmin(request.targetUserId(), actorId);
        }
        return admins.request(requestId);
    }

    private User supportAdmin(UUID adminId) {
        User admin = userRepository.findByIdAndDeletedAtIsNull(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This admin doesn't exist any more."));
        if (!rolesOf(adminId).contains("CO_SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only support admins are given restaurants.");
        }
        return admin;
    }

    private Set<String> rolesOf(UUID userId) {
        return roleRepository.findActiveRolesByUserId(userId).stream().map(Role::getCode).collect(Collectors.toSet());
    }

    private void requireSuperAdmin(Authentication auth) {
        if (!roleHierarchyService.isSuperAdmin(auth)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin does this.");
    }

    /** People of [restaurantId], or super admins working in it. */
    private void requireRestaurant(Authentication auth, UUID restaurantId) {
        if (restaurantId == null || !restaurantId.equals(roleHierarchyService.actorRestaurantId(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't work in this restaurant.");
        }
    }
}
