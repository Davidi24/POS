package pos.pos.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.support.AuthTestDataFactory;
import pos.pos.auth.entity.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSessionRepositoryTest {

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Test
    void findByTokenIdAndRevokedFalse_shouldReturnActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UserSession session = AuthTestDataFactory.session(userId, tokenId);
        userSessionRepository.save(session);

        Optional<UserSession> result = userSessionRepository.findByTokenIdAndRevokedFalse(tokenId);

        assertTrue(result.isPresent());
        assertEquals(tokenId, result.get().getTokenId());
    }

    @Test
    void findByUserId_shouldReturnOnlyThatUsersSessions() {
        UUID userId = UUID.randomUUID();
        userSessionRepository.save(AuthTestDataFactory.session(userId, UUID.randomUUID()));
        userSessionRepository.save(AuthTestDataFactory.session(userId, UUID.randomUUID()));
        userSessionRepository.save(AuthTestDataFactory.session(UUID.randomUUID(), UUID.randomUUID()));

        List<UserSession> result = userSessionRepository.findByUserId(userId);

        assertEquals(2, result.size());
    }
}
