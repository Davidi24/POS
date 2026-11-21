package pos.pos.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Support admins, restaurant admins and restaurants' requests to the Super Admin (user, 2026-11-19). */
@Tag(name = "Admins")
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/platform/admins")
    @Operation(summary = "Super Admin: every admin, support admins (with their restaurants) and restaurant admins")
    public List<AdminDtos.AdminCard> admins(Authentication auth) {
        return adminService.adminCards(auth);
    }

    @GetMapping("/co-super-admins/{userId}/restaurants")
    @Operation(summary = "Super Admin: the restaurants a support admin takes care of")
    public AdminDtos.SupportAdminAccess access(@PathVariable UUID userId, Authentication auth) {
        return adminService.access(auth, userId);
    }

    @PutMapping("/co-super-admins/{userId}/restaurants")
    @Operation(summary = "Super Admin: give a support admin restaurants (or all), and allow removing owners")
    public AdminDtos.SupportAdminAccess updateAccess(@PathVariable UUID userId, @Valid @RequestBody AdminDtos.UpdateSupportAdminAccess request,
                                                    Authentication auth) {
        return adminService.updateAccess(auth, userId, request);
    }

    @GetMapping("/restaurants/{restaurantId}/support-admins")
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "The support admins taking care of this restaurant, to write to for help")
    public List<AdminDtos.SupportContact> supportAdmins(@PathVariable UUID restaurantId, Authentication auth) {
        return adminService.supportAdmins(auth, restaurantId);
    }

    @PostMapping("/restaurants/{restaurantId}/requests")
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "Owners ask the Super Admin: remove another owner, or a support admin")
    public AdminDtos.PlatformRequest ask(@PathVariable UUID restaurantId, @Valid @RequestBody AdminDtos.CreateRequest request, Authentication auth) {
        return adminService.ask(auth, restaurantId, request);
    }

    @GetMapping("/restaurants/{restaurantId}/requests")
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "What this restaurant asked the Super Admin")
    public List<AdminDtos.PlatformRequest> restaurantRequests(@PathVariable UUID restaurantId, Authentication auth) {
        return adminService.restaurantRequests(auth, restaurantId);
    }

    @GetMapping("/platform/requests")
    @Operation(summary = "Super Admin: what restaurants asked (waiting ones first)")
    public List<AdminDtos.PlatformRequest> allRequests(@RequestParam(defaultValue = "false") boolean pendingOnly, Authentication auth) {
        return adminService.allRequests(auth, pendingOnly);
    }

    @PostMapping("/platform/requests/{requestId}/decide")
    @Operation(summary = "Super Admin: approve or decline a request (approving a removal removes that owner)")
    public AdminDtos.PlatformRequest decide(@PathVariable UUID requestId, @Valid @RequestBody AdminDtos.Decision decision, Authentication auth) {
        return adminService.decide(auth, requestId, decision);
    }

    @PostMapping("/platform/requests/{requestId}/assign")
    @Operation(summary = "Super Admin: give a waiting request to a support admin (adminId null takes it back)")
    public AdminDtos.PlatformRequest assign(@PathVariable UUID requestId, @Valid @RequestBody AdminDtos.Assign assign, Authentication auth) {
        return adminService.assign(auth, requestId, assign);
    }

    @PostMapping("/platform/requests/{requestId}/start")
    @Operation(summary = "Start working on a waiting request (it shows In progress)")
    public AdminDtos.PlatformRequest start(@PathVariable UUID requestId, Authentication auth) {
        return adminService.start(auth, requestId);
    }

    @PostMapping("/platform/requests/{requestId}/solve")
    @Operation(summary = "Close a waiting request as solved (the Super Admin, or the support admin it was given to)")
    public AdminDtos.PlatformRequest solve(@PathVariable UUID requestId, @Valid @RequestBody AdminDtos.Solve solve, Authentication auth) {
        return adminService.solve(auth, requestId, solve);
    }
}
