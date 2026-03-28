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
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.security.config.SecurityPaths;
import pos.pos.security.service.JwtService;
import pos.pos.role.entity.Role;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.role.repository.RoleRepository;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserSessionRepository userSessionRepository;

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            UserSessionRepository userSessionRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.userSessionRepository = userSessionRepository;
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

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

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

        User user = userRepository.findById(userId).orElse(null);

        if (user == null || !user.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        List<UUID> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .toList();

        List<SimpleGrantedAuthority> authorities = roleRepository.findByIdIn(roleIds)
                .stream()
                .filter(Role::isActive)
                .map(Role::getCode)
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        SecurityContextHolder.clearContext();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        authorities
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
