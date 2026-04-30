package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.settings.SettingsTemplateNotFoundException;
import pos.pos.settings.dto.SettingsTemplateResponse;
import pos.pos.settings.dto.SettingsExportResponse;
import pos.pos.settings.dto.SettingsTemplateRequest;
import pos.pos.settings.entity.SettingsTemplate;
import pos.pos.settings.repository.SettingsTemplateRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsTemplateService {

    private final SettingsTemplateRepository settingsTemplateRepository;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsOperationsService settingsOperationsService;

    @Transactional(readOnly = true)
    public List<SettingsTemplateResponse> listTemplates() {
        return settingsTemplateRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SettingsTemplateResponse getTemplate(UUID templateId) {
        return toResponse(requireTemplate(templateId));
    }

    @Transactional
    public SettingsTemplateResponse createTemplate(Authentication authentication, SettingsTemplateRequest request) {
        SettingsTemplate settingsTemplate = new SettingsTemplate();
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        applyTemplateFields(settingsTemplate, request);
        settingsTemplate.setCreatedBy(actorId);
        settingsTemplate.setUpdatedBy(actorId);
        return toResponse(settingsTemplateRepository.saveAndFlush(settingsTemplate));
    }

    @Transactional
    public SettingsTemplateResponse updateTemplate(
            Authentication authentication,
            UUID templateId,
            SettingsTemplateRequest request
    ) {
        SettingsTemplate settingsTemplate = requireTemplate(templateId);
        applyTemplateFields(settingsTemplate, request);
        settingsTemplate.setUpdatedBy(settingsDomainSupport.currentActorId(authentication));
        return toResponse(settingsTemplateRepository.saveAndFlush(settingsTemplate));
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        settingsTemplateRepository.delete(requireTemplate(templateId));
        settingsTemplateRepository.flush();
    }

    @Transactional
    public SettingsExportResponse applyTemplate(
            Authentication authentication,
            UUID restaurantId,
            UUID templateId
    ) {
        SettingsTemplate settingsTemplate = requireTemplate(templateId);
        return settingsOperationsService.importSettings(authentication, restaurantId, settingsTemplate.getPayload());
    }

    private SettingsTemplate requireTemplate(UUID templateId) {
        return settingsTemplateRepository.findById(templateId)
                .orElseThrow(SettingsTemplateNotFoundException::new);
    }

    private void applyTemplateFields(SettingsTemplate settingsTemplate, SettingsTemplateRequest request) {
        settingsTemplate.setTemplateName(request.getTemplateName());
        settingsTemplate.setDescription(request.getDescription());
        settingsTemplate.setPayload(request.getPayload());
    }

    private SettingsTemplateResponse toResponse(SettingsTemplate settingsTemplate) {
        return SettingsTemplateResponse.builder()
                .id(settingsTemplate.getId())
                .templateName(settingsTemplate.getTemplateName())
                .description(settingsTemplate.getDescription())
                .payload(settingsTemplate.getPayload())
                .createdAt(settingsTemplate.getCreatedAt())
                .updatedAt(settingsTemplate.getUpdatedAt())
                .createdBy(settingsTemplate.getCreatedBy())
                .updatedBy(settingsTemplate.getUpdatedBy())
                .build();
    }
}
