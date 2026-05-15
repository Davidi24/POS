package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.dto.NotificationTemplateRequest;
import pos.pos.notification.dto.NotificationTemplateResponse;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.repository.NotificationTemplateRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.scope.ActorScopeService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationMapper notificationMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final ActorScopeService actorScopeService;

    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> getTemplates(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return notificationTemplateRepository.findAllByRestaurant_IdOrderByCodeAscChannelAsc(restaurantId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Transactional
    public NotificationTemplateResponse createTemplate(
            Authentication authentication,
            UUID restaurantId,
            NotificationTemplateRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = actorScopeService.currentUserId(authentication);

        NotificationTemplate template = new NotificationTemplate();
        template.setRestaurant(restaurant);
        applyRequest(template, request, actorId);
        return notificationMapper.toResponse(save(template));
    }

    @Transactional
    public NotificationTemplateResponse updateTemplate(
            Authentication authentication,
            UUID restaurantId,
            UUID templateId,
            NotificationTemplateRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = actorScopeService.currentUserId(authentication);

        NotificationTemplate template = notificationTemplateRepository.findByIdAndRestaurant_Id(templateId, restaurantId)
                .orElseThrow(() -> new AuthException("Notification template not found", HttpStatus.NOT_FOUND));
        applyRequest(template, request, actorId);
        return notificationMapper.toResponse(save(template));
    }

    @Transactional
    public void deleteTemplate(Authentication authentication, UUID restaurantId, UUID templateId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        NotificationTemplate template = notificationTemplateRepository.findByIdAndRestaurant_Id(templateId, restaurantId)
                .orElseThrow(() -> new AuthException("Notification template not found", HttpStatus.NOT_FOUND));
        notificationTemplateRepository.delete(template);
        notificationTemplateRepository.flush();
    }

    private void applyRequest(NotificationTemplate template, NotificationTemplateRequest request, UUID actorId) {
        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setChannel(request.getChannel());
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setActive(Boolean.TRUE.equals(request.getActive()));
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(actorId);
        }
        template.setUpdatedBy(actorId);
    }

    private NotificationTemplate save(NotificationTemplate template) {
        try {
            return notificationTemplateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Notification template update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
