package pos.pos.security.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.ActorScopeNotAvailableException;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActorScopeService {

    private final UserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;

    public ActorScope resolve(Authentication authentication) {
        UUID userId = roleHierarchyService.currentUserId(authentication);
        User actor = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(ActorScopeNotAvailableException::new);

        return new ActorScope(
                userId,
                actor,
                roleHierarchyService.isSuperAdmin(authentication),
                extractRoleCodes(authentication),
                extractPermissionCodes(authentication)
        );
    }

    public UUID currentUserId(Authentication authentication) {
        return roleHierarchyService.currentUserId(authentication);
    }

    public User currentActor(Authentication authentication) {
        return resolve(authentication).actor();
    }

    private Set<String> extractRoleCodes(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> extractPermissionCodes(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
