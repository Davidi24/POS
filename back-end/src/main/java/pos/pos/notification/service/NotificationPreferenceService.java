package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.notification.dto.NotificationPreferenceRequest;
import pos.pos.notification.dto.NotificationPreferenceResponse;
import pos.pos.notification.entity.NotificationPreference;
import pos.pos.notification.mapper.NotificationMapper;
import pos.pos.notification.repository.NotificationPreferenceRepository;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationMapper notificationMapper;
    private final ActorScopeService actorScopeService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getCurrentPreferences(Authentication authentication) {
        UUID userId = actorScopeService.currentUserId(authentication);
        return notificationPreferenceRepository.findAllByUser_IdOrderByChannelAscEventCodeAsc(userId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<NotificationPreferenceResponse> upsertCurrentPreferences(
            Authentication authentication,
            List<NotificationPreferenceRequest> requests
    ) {
        UUID userId = actorScopeService.currentUserId(authentication);
        var user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        for (NotificationPreferenceRequest request : requests) {
            String eventCode = NormalizationUtils.normalizeUpper(request.getEventCode());
            NotificationPreference preference = notificationPreferenceRepository
                    .findByUser_IdAndChannelAndEventCode(userId, request.getChannel(), eventCode)
                    .orElseGet(() -> {
                        NotificationPreference created = new NotificationPreference();
                        created.setUser(user);
                        created.setChannel(request.getChannel());
                        created.setEventCode(eventCode);
                        return created;
                    });
            preference.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
            notificationPreferenceRepository.save(preference);
        }

        notificationPreferenceRepository.flush();
        return getCurrentPreferences(authentication);
    }
}
