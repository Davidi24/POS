package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.SessionNotFoundException;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.UserSessionResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// checked
// tested
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserSessionMapper userSessionMapper;
    private final RoleHierarchyService roleHierarchyService;

    public List<UserSessionResponse> getMyActiveSessions(UUID userId, UUID currentTokenId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userSessionRepository.findActiveSessionsByUserId(userId, now)
                .stream()
                .map(s -> userSessionMapper.toSessionResponse(
                        s,
                        currentTokenId != null && currentTokenId.equals(s.getTokenId())
                ))
                .toList();
    }

    public UserSessionResponse getCurrentSession(UUID userId, UUID currentTokenId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId)
                .filter(s -> s.getUserId().equals(userId))
                .filter(s -> s.getExpiresAt().isAfter(now))
                .orElseThrow(SessionNotFoundException::new);

        return userSessionMapper.toSessionResponse(session, true);
    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession session = userSessionRepository.findByIdAndUserIdAndRevokedFalse(sessionId, userId)
                .filter(s -> s.getExpiresAt().isAfter(now))
                .orElseThrow(SessionNotFoundException::new);

        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(SessionRevocationReason.SESSION_REVOKED.name());
        userSessionRepository.save(session);
    }

    // it revokes all other session except the one taken as argument
    @Transactional
    public void revokeOtherSessions(UUID userId, UUID currentTokenId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession currentSession = userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId)
                .filter(s -> s.getUserId().equals(userId))
                .filter(s -> s.getExpiresAt().isAfter(now))
                .orElseThrow(SessionNotFoundException::new);

        userSessionRepository.revokeAllActiveSessionsByUserIdExcept(
                userId,
                currentSession.getId(),
                now,
                SessionRevocationReason.SESSION_REVOKED.name()
        );
    }

    public List<UserSessionResponse> getUserActiveSessions(Authentication authentication, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        return userSessionRepository.findActiveSessionsByUserId(userId, now)
                .stream()
                .map(s -> userSessionMapper.toSessionResponse(s, false))
                .toList();
    }

    @Transactional
    public void revokeAllUserSessions(Authentication authentication, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        userSessionRepository.revokeAllActiveSessionsByUserId(
                userId,
                now,
                SessionRevocationReason.SESSION_REVOKED.name()
        );
    }

    @Transactional
    public void revokeUserSession(Authentication authentication, UUID userId, UUID sessionId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        UserSession session = userSessionRepository.findByIdAndUserIdAndRevokedFalse(sessionId, userId)
                .filter(s -> s.getExpiresAt().isAfter(now))
                .orElseThrow(SessionNotFoundException::new);

        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(SessionRevocationReason.SESSION_REVOKED.name());
        userSessionRepository.save(session);
    }
}
