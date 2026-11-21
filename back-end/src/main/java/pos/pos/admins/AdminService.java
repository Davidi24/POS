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
    private final pos.pos.restaurant.repository.RestaurantRepository restaurantRepository;

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

    /** How many unsolved problems one person may have at a time. */
    private static final int MAX_OPEN_PROBLEMS = 5;

    @Transactional
    public AdminDtos.PlatformRequest ask(Authentication auth, UUID restaurantId, AdminDtos.CreateRequest request) {
        requireRestaurant(auth, restaurantId);
        UUID actorId = roleHierarchyService.currentUserId(auth);
        // A problem: anyone working in the restaurant writes what's wrong (user, 2026-11-21); a few open ones at a time each.
        if ("PROBLEM".equals(request.kind())) {
            String message = request.note() == null ? "" : request.note().trim();
            if (message.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write what the problem is.");
            if (admins.openProblems(actorId) >= MAX_OPEN_PROBLEMS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have 5 problems waiting for an answer. Wait for one before you send more.");
            }
            UUID id = UUID.randomUUID();
            OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
            admins.insertRequest(id, restaurantId, "PROBLEM", null, message, actorId, at);
            admins.event(id, "CREATED", actorId, null, null, at);
            return admins.request(id);
        }
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
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        admins.insertRequest(id, restaurantId, request.kind(), target, note, actorId, at);
        admins.event(id, "CREATED", actorId, target, null, at);
        return admins.request(id);
    }

    /**
     * A ticket opened from the Requests board itself (user, 2026-11-21: "make here that I can open a ticket myself"): a
     * problem for a restaurant, written by the Super Admin or a support admin who gives requests out; it starts Open.
     */
    @Transactional
    public AdminDtos.PlatformRequest openTicket(Authentication auth, AdminDtos.OpenTicket ticket) {
        if (!canAssign(auth)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin does this.");
        String message = ticket.note() == null ? "" : ticket.note().trim();
        if (message.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write what the problem is.");
        var restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(ticket.restaurantId())
                .filter(r -> r.getStatus() != pos.pos.restaurant.enums.RestaurantStatus.PENDING
                        && r.getStatus() != pos.pos.restaurant.enums.RestaurantStatus.REJECTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a restaurant."));
        UUID actorId = roleHierarchyService.currentUserId(auth);
        UUID id = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        admins.insertRequest(id, restaurant.getId(), "PROBLEM", null, message, actorId, at);
        admins.event(id, "CREATED", actorId, null, null, at);
        return admins.request(id);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.PlatformRequest> restaurantRequests(Authentication auth, UUID restaurantId) {
        requireRestaurant(auth, restaurantId);
        return admins.requests(restaurantId, false);
    }

    /** The Super Admin sees every request; a support admin only the ones given to them (user, 2026-11-21). */
    @Transactional(readOnly = true)
    public List<AdminDtos.PlatformRequest> allRequests(Authentication auth, boolean pendingOnly) {
        // Someone who gives requests out sees them all.
        if (roleHierarchyService.isSuperAdmin(auth) || canAssign(auth)) return admins.requests(null, pendingOnly);
        if (roleHierarchyService.isCoSuperAdmin(auth)) return admins.requests(null, pendingOnly, roleHierarchyService.currentUserId(auth));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin does this.");
    }

    /** The Super Admin gives a waiting request to a support admin, or takes it back. */
    @Transactional
    public AdminDtos.PlatformRequest assign(Authentication auth, UUID requestId, AdminDtos.Assign assign) {
        if (!roleHierarchyService.isSuperAdmin(auth) && !canAssign(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may not give requests to others. Ask the Super Admin.");
        }
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        UUID actorId = roleHierarchyService.currentUserId(auth);
        // Yourself, or a support admin who's switched on.
        boolean self = actorId.equals(assign.adminId());
        if (assign.adminId() != null && !self) {
            User admin = supportAdmin(assign.adminId());
            if (!admin.isActive()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This support admin is switched off.");
        }
        // Giving a restaurant to a support admin stays the Super Admin's.
        if (assign.giveRestaurant() && !self && assign.adminId() != null && !roleHierarchyService.isSuperAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin gives restaurants to support admins.");
        }
        String note = assign.note() == null || assign.note().isBlank() ? null : assign.note().trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (admins.assign(requestId, assign.adminId(), actorId, note, now) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already answered.");
        }
        if (assign.adminId() == null) admins.event(requestId, "TAKEN_BACK", actorId, request.assignedToId(), null, now);
        else admins.event(requestId, self ? "TAKEN" : "ASSIGNED", actorId, assign.adminId(), note, now);
        if (assign.adminId() != null && !self && assign.giveRestaurant()) {
            User admin = supportAdmin(assign.adminId());
            if (!admin.isAdminAllRestaurants()) {
                admins.addRestaurant(admin.getId(), request.restaurantId(), actorId);
                admins.event(requestId, "RESTAURANT_GIVEN", actorId, admin.getId(), null, now);
            }
            // They asked for a support admin and now have one.
            if ("NEED_ADMIN".equals(request.kind())) {
                String name = (admin.getFirstName() + " " + admin.getLastName()).trim();
                String answer = pos.pos.i18n.Words.en("{} takes care of you now.", name);
                admins.decide(requestId, true, actorId, answer, now);
                admins.event(requestId, "SOLVED", actorId, null, answer, now);
            }
        }
        return admins.request(requestId);
    }

    /** Someone starts working on it (it shows "In progress"): the Super Admin, or the support admin it was given to. */
    @Transactional
    public AdminDtos.PlatformRequest start(Authentication auth, UUID requestId) {
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        // The Super Admin, the one it was given to, or (when nobody has it) someone who gives requests out, who then has it.
        if (!(request.assignedToId() == null && canAssign(auth))) requireSuperAdminOrAssignee(auth, request);
        UUID actorId = roleHierarchyService.currentUserId(auth);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (admins.start(requestId, actorId, now) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already solved.");
        }
        if (request.assignedToId() == null) admins.event(requestId, "TAKEN", actorId, actorId, null, now);
        admins.event(requestId, "STARTED", actorId, null, null, now);
        return admins.request(requestId);
    }

    /** Closed as solved (taken care of some other way): the Super Admin, or the support admin it was given to. */
    @Transactional
    public AdminDtos.PlatformRequest solve(Authentication auth, UUID requestId, AdminDtos.Solve solve) {
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        requireSuperAdminOrAssignee(auth, request);
        String note = solve.note() == null || solve.note().isBlank() ? null : solve.note().trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (admins.solve(requestId, roleHierarchyService.currentUserId(auth), note, now) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already answered.");
        }
        admins.event(requestId, "SOLVED", roleHierarchyService.currentUserId(auth), null, note, now);
        return admins.request(requestId);
    }

    /** The Super Admin answers; approving a removal removes that owner (after checking with them, user 2026-11-19). */
    @Transactional
    public AdminDtos.PlatformRequest decide(Authentication auth, UUID requestId, AdminDtos.Decision decision) {
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        requireSuperAdminOrAssignee(auth, request);
        // A support admin it was given to may decline it, and remove an owner only when allowed to and in that restaurant;
        // giving support admins stays the Super Admin's.
        if (decision.approve() && !roleHierarchyService.isSuperAdmin(auth)) {
            UUID me = roleHierarchyService.currentUserId(auth);
            User self = userRepository.findByIdAndDeletedAtIsNull(me).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed."));
            if (!"REMOVE_OWNER".equals(request.kind())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin gives support admins. Mark it solved or ask the Super Admin.");
            }
            if (!self.isAdminMayRemoveOwners() || !restaurantRepository.supportAdminMayEnter(me, request.restaurantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may not remove owners here. Ask the Super Admin.");
            }
        }
        UUID actorId = roleHierarchyService.currentUserId(auth);
        String note = decision.note() == null || decision.note().isBlank() ? null : decision.note().trim();
        // Checked before answering, so a wrong person leaves the request waiting.
        User helper = decision.approve() && "NEED_ADMIN".equals(request.kind()) && decision.supportAdminId() != null
                ? supportAdmin(decision.supportAdminId()) : null;
        OffsetDateTime decidedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (admins.decide(requestId, decision.approve(), actorId, note, decidedAt) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already answered.");
        }
        boolean removal = decision.approve() && "REMOVE_OWNER".equals(request.kind());
        admins.event(requestId, removal ? "OWNER_REMOVED" : decision.approve() ? "APPROVED" : "DECLINED", actorId,
                removal ? request.targetUserId() : null, note, decidedAt);
        if (helper != null && !helper.isAdminAllRestaurants()) admins.event(requestId, "RESTAURANT_GIVEN", actorId, helper.getId(), null, decidedAt);
        if (decision.approve() && "REMOVE_OWNER".equals(request.kind()) && request.targetUserId() != null
                && userRepository.findByIdAndDeletedAtIsNull(request.targetUserId()).isPresent()) {
            userAdminService.removeApprovedBySuperAdmin(request.targetUserId(), actorId);
        }
        if (helper != null && !helper.isAdminAllRestaurants()) {
            admins.addRestaurant(helper.getId(), request.restaurantId(), actorId);
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

    private void requireSuperAdminOrAssignee(Authentication auth, AdminDtos.PlatformRequest request) {
        if (roleHierarchyService.isSuperAdmin(auth)) return;
        if (roleHierarchyService.isCoSuperAdmin(auth) && roleHierarchyService.currentUserId(auth).equals(request.assignedToId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin, or the support admin it was given to, answers this.");
    }

    /** What happened to a request, oldest first: for whoever may see it. */
    @Transactional(readOnly = true)
    public List<AdminDtos.RequestEvent> history(Authentication auth, UUID requestId) {
        AdminDtos.PlatformRequest request = admins.request(requestId);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This request doesn't exist any more.");
        if (!canAssign(auth)) requireSuperAdminOrAssignee(auth, request);
        return admins.events(requestId);
    }

    /** A support admin the Super Admin lets give requests out ("Assign requests"); the Super Admin always may. */
    private boolean canAssign(Authentication auth) {
        if (roleHierarchyService.isSuperAdmin(auth)) return true;
        return roleHierarchyService.isCoSuperAdmin(auth) && auth.getAuthorities().stream()
                .anyMatch(authority -> pos.pos.security.rbac.AppPermission.REQUESTS_ASSIGN.name().equals(authority.getAuthority()));
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
