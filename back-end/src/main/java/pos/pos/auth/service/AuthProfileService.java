package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthProfileService {

    private final CurrentUserMapper currentUserMapper;

    public CurrentUserResponse getMe(Authentication authentication) {
        AuthenticatedUser user = currentUser(authentication);

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .distinct()
                .toList();

        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .distinct()
                .toList();

        return currentUserMapper.toCurrentUserResponse(user, roles, permissions);
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
