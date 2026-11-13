package pos.pos.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.security.config.AuthCookieProperties;
import pos.pos.security.config.SecurityPaths;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.service.JwtService;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.role.repository.RoleRepository;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final pos.pos.restaurant.repository.RestaurantRepository restaurantRepository;
    private final pos.pos.restaurant.repository.BranchRepository branchRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuthCookieProperties authCookieProperties;

    private final AntPathMatcher matcher = new AntPathMatcher();

    public static final String ACTING_RESTAURANT_HEADER = "X-Restaurant-Id";
    public static final String ACTING_BRANCH_HEADER = "X-Branch-Id";
    private static final Set<String> PLATFORM_ROLES = Set.of("SUPER_ADMIN", "CO_SUPER_ADMIN");

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserSessionRepository userSessionRepository,
            AuthCookieProperties authCookieProperties,
            pos.pos.restaurant.repository.RestaurantRepository restaurantRepository,
            pos.pos.restaurant.repository.BranchRepository branchRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.branchRepository = branchRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userSessionRepository = userSessionRepository;
        this.authCookieProperties = authCookieProperties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        for (String publicPath : SecurityPaths.PUBLIC) {
            if (matcher.match(publicPath, path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String token = extractAccessToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.isValid(token) || !jwtService.isAccessToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        UUID tokenId;
        try {
            userId = jwtService.extractUserId(token);
            tokenId = jwtService.extractTokenId(token);
        } catch (RuntimeException ex) {
            filterChain.doFilter(request, response);
            return;
        }

        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalse(tokenId).orElse(null);

        if (session == null || session.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findActiveById(userId).orElse(null);

        // Staff of a restaurant that was suspended, closed or removed lose access at once (their refresh is refused too).
        if (user == null || userRepository.worksAtClosedRestaurant(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        List<UUID> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .toList();

        List<Role> activeRoles = roleIds.isEmpty()
                ? List.of()
                : roleRepository.findByIdIn(roleIds)
                .stream()
                .filter(Role::isActive)
                .toList();

        List<UUID> activeRoleIds = activeRoles.stream()
                .map(Role::getId)
                .toList();

        List<SimpleGrantedAuthority> roleAuthorities = activeRoles.stream()
                .map(Role::getCode)
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        // Their roles as their restaurant has them, less what's switched off for them (the Super Admin's ticks per
        // Co-Super-Admin, an owner's per-person changes), plus what's given to them alone.
        List<SimpleGrantedAuthority> permissionAuthorities = pos.pos.role.service.EffectivePermissions
                .forUser(permissionRepository, userId, user.getRestaurantId(), activeRoleIds)
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.addAll(roleAuthorities);
        authorities.addAll(permissionAuthorities);

        SecurityContextHolder.clearContext();

        // The Super Admin and Co-Super-Admins act in the restaurant (and branch) they entered in the app, sent with
        // every request. Everyone else always acts in their own restaurant; the headers mean nothing for them.
        boolean platformAdmin = activeRoles.stream()
                .map(Role::getCode)
                .anyMatch(code -> PLATFORM_ROLES.contains(code));
        UUID actingRestaurant = null;
        UUID actingBranch = null;
        if (platformAdmin) {
            try {
                actingRestaurant = headerId(request, ACTING_RESTAURANT_HEADER);
                actingBranch = headerId(request, ACTING_BRANCH_HEADER);
            } catch (IllegalArgumentException invalid) {
                contextError(response, 400, "Invalid restaurant or branch selection");
                return;
            }
            var selectedRestaurant = actingRestaurant == null ? null : restaurantRepository.findByIdAndDeletedAtIsNull(actingRestaurant).orElse(null);
            var selectedBranch = actingBranch == null || actingRestaurant == null ? null
                    : branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(actingBranch, actingRestaurant).orElse(null);
            boolean canSelect = permissionAuthorities.stream().anyMatch(a -> a.getAuthority().equals("RESTAURANTS_READ"));
            if ((actingRestaurant != null && (!canSelect || selectedRestaurant == null))
                    || (actingBranch != null && (selectedBranch == null || !selectedBranch.isActive()
                        || selectedBranch.getStatus() != pos.pos.restaurant.enums.BranchStatus.ACTIVE
                        || selectedRestaurant == null || !selectedRestaurant.isActive()
                        || selectedRestaurant.getStatus() != pos.pos.restaurant.enums.RestaurantStatus.ACTIVE))) {
                contextError(response, 403, "The selected restaurant or branch is no longer available. Choose another selection.");
                return;
            }
        }
        AuthenticatedUser principal = platformAdmin
                ? AuthenticatedUser.actingFor(user, actingRestaurant, actingBranch)
                : AuthenticatedUser.from(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractAccessToken(HttpServletRequest request) {
        String authorizationToken = extractBearerToken(request.getHeader("Authorization"));
        if (authorizationToken != null) {
            return authorizationToken;
        }

        var cookie = WebUtils.getCookie(request, authCookieProperties.getAccessTokenName());
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }

        return cookie.getValue().trim();
    }

    private String extractBearerToken(String header) {
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }

        String token = header.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private void contextError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("X-POS-Context-Invalid", "true");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private UUID headerId(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? UUID.fromString(value.trim()) : null;
    }
}
