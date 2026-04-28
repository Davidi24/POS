package pos.pos.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.auth.PhoneAlreadyExistsException;
import pos.pos.exception.auth.UsernameAlreadyExistsException;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

@Service
@RequiredArgsConstructor
public class UserIdentityService {

    private final UserRepository userRepository;

    public NormalizedUserIdentity normalizeAndAssertUnique(String email, String username, String phone) {
        String normalizedEmail = NormalizationUtils.normalizeLower(email);
        String normalizedUsername = NormalizationUtils.normalizeLower(username);
        String normalizedPhone = NormalizationUtils.normalizePhone(phone);

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(normalizedUsername)) {
            throw new UsernameAlreadyExistsException();
        }
        if (normalizedPhone != null && userRepository.existsByNormalizedPhoneAndDeletedAtIsNull(normalizedPhone)) {
            throw new PhoneAlreadyExistsException();
        }

        return new NormalizedUserIdentity(normalizedEmail, normalizedUsername, normalizedPhone);
    }

    public record NormalizedUserIdentity(String email, String username, String normalizedPhone) {
    }
}
