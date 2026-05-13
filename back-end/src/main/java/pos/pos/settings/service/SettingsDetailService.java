package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.settings.SettingsBusinessHourNotFoundException;
import pos.pos.exception.settings.SettingsReservationRuleNotFoundException;
import pos.pos.exception.settings.SettingsSpecialHourNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.dto.BusinessHourResponse;
import pos.pos.settings.dto.BulkDeleteSpecialHoursRequest;
import pos.pos.settings.dto.BulkUpsertSpecialHoursRequest;
import pos.pos.settings.dto.CopyBusinessHoursRequest;
import pos.pos.settings.dto.CopyBusinessHoursResponse;
import pos.pos.settings.dto.OrderRuleSettingsResponse;
import pos.pos.settings.dto.ReceiptPreviewResponse;
import pos.pos.settings.dto.ReceiptSettingsResponse;
import pos.pos.settings.dto.ReceiptTestPrintResponse;
import pos.pos.settings.dto.ReorderReservationRulesRequest;
import pos.pos.settings.dto.ReservationRuleResponse;
import pos.pos.settings.dto.ReplaceBusinessHoursRequest;
import pos.pos.settings.dto.SpecialHourResponse;
import pos.pos.settings.dto.UpdateOrderRuleDiscountPolicyRequest;
import pos.pos.settings.dto.UpdateOrderRuleSettingsRequest;
import pos.pos.settings.dto.UpdateOrderRuleVoidPolicyRequest;
import pos.pos.settings.dto.UpdateOrderRuleWorkflowRequest;
import pos.pos.settings.dto.UpdateReceiptSettingsRequest;
import pos.pos.settings.dto.UpdateReservationRulePriorityRequest;
import pos.pos.settings.dto.UpdateReservationRuleStatusRequest;
import pos.pos.settings.dto.UpdateSpecialHourStatusRequest;
import pos.pos.settings.dto.UpsertBusinessHourRequest;
import pos.pos.settings.dto.UpsertReservationRuleRequest;
import pos.pos.settings.dto.UpsertSpecialHourRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsBusinessHour;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.entity.SettingsReceipt;
import pos.pos.settings.entity.SettingsReservationRule;
import pos.pos.settings.entity.SettingsSpecialHour;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.repository.SettingsBusinessHourRepository;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsSpecialHourRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SettingsDetailService {

    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsReservationRuleRepository reservationRuleRepository;
    private final SettingsBusinessHourRepository businessHourRepository;
    private final SettingsSpecialHourRepository specialHourRepository;
    private final SettingsMapper settingsMapper;
    private final SettingsAuditService settingsAuditService;

    @Transactional
    public ReceiptSettingsResponse getReceiptSettings(Authentication authentication, UUID restaurantId) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        return settingsMapper.toReceiptResponse(settings.getReceiptSettings());
    }

    @Transactional
    public ReceiptSettingsResponse updateReceiptSettings(
            Authentication authentication,
            UUID restaurantId,
            UpdateReceiptSettingsRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        applyReceiptUpdate(context.settings().getReceiptSettings(), request);

        return saveSettingsAndAudit(
                context,
                "SETTINGS_RECEIPT",
                saved -> saved.getReceiptSettings().getId(),
                "UPDATE",
                "Updated receipt settings",
                saved -> settingsMapper.toReceiptResponse(saved.getReceiptSettings())
        );
    }

    @Transactional
    public ReceiptPreviewResponse previewReceipt(Authentication authentication, UUID restaurantId) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        List<String> previewLines = buildReceiptPreviewLines(settings);

        return ReceiptPreviewResponse.builder()
                .receiptCopies(settings.getReceiptSettings().getReceiptCopies())
                .showLogo(settings.getReceiptSettings().isShowLogo())
                .showQrCode(settings.getReceiptSettings().isShowQrCode())
                .previewLines(previewLines)
                .build();
    }

    @Transactional
    public ReceiptTestPrintResponse testPrintReceipt(Authentication authentication, UUID restaurantId) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        ReceiptPreviewResponse preview = ReceiptPreviewResponse.builder()
                .receiptCopies(settings.getReceiptSettings().getReceiptCopies())
                .showLogo(settings.getReceiptSettings().isShowLogo())
                .showQrCode(settings.getReceiptSettings().isShowQrCode())
                .previewLines(buildReceiptPreviewLines(settings))
                .build();
        settingsAuditService.log(
                settings.getRestaurant(),
                null,
                "SETTINGS_RECEIPT",
                settings.getReceiptSettings().getId(),
                "TEST_PRINT",
                "Generated a receipt test print payload",
                settingsDomainSupport.currentActorId(authentication)
        );

        return ReceiptTestPrintResponse.builder()
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .receiptCopies(preview.getReceiptCopies())
                .printPayload(String.join(System.lineSeparator(), preview.getPreviewLines()))
                .build();
    }

    @Transactional
    public OrderRuleSettingsResponse getOrderRules(Authentication authentication, UUID restaurantId) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        return settingsMapper.toOrderRuleResponse(settings.getOrderRuleSettings());
    }

    @Transactional
    public OrderRuleSettingsResponse updateOrderRules(
            Authentication authentication,
            UUID restaurantId,
            UpdateOrderRuleSettingsRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        applyOrderRuleUpdate(context.settings().getOrderRuleSettings(), request);

        return saveSettingsAndAudit(
                context,
                "SETTINGS_ORDER_RULE",
                saved -> saved.getOrderRuleSettings().getId(),
                "UPDATE",
                "Replaced order rule settings",
                saved -> settingsMapper.toOrderRuleResponse(saved.getOrderRuleSettings())
        );
    }

    @Transactional
    public OrderRuleSettingsResponse updateVoidPolicy(
            Authentication authentication,
            UUID restaurantId,
            UpdateOrderRuleVoidPolicyRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        applyVoidPolicy(context.settings().getOrderRuleSettings(), request);

        return saveSettingsAndAudit(
                context,
                "SETTINGS_ORDER_RULE",
                saved -> saved.getOrderRuleSettings().getId(),
                "UPDATE_VOID_POLICY",
                "Updated order void policy",
                saved -> settingsMapper.toOrderRuleResponse(saved.getOrderRuleSettings())
        );
    }

    @Transactional
    public OrderRuleSettingsResponse updateDiscountPolicy(
            Authentication authentication,
            UUID restaurantId,
            UpdateOrderRuleDiscountPolicyRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        applyDiscountPolicy(context.settings().getOrderRuleSettings(), request);

        return saveSettingsAndAudit(
                context,
                "SETTINGS_ORDER_RULE",
                saved -> saved.getOrderRuleSettings().getId(),
                "UPDATE_DISCOUNT_POLICY",
                "Updated order discount policy",
                saved -> settingsMapper.toOrderRuleResponse(saved.getOrderRuleSettings())
        );
    }

    @Transactional
    public OrderRuleSettingsResponse updateWorkflow(
            Authentication authentication,
            UUID restaurantId,
            UpdateOrderRuleWorkflowRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        applyWorkflow(context.settings().getOrderRuleSettings(), request);

        return saveSettingsAndAudit(
                context,
                "SETTINGS_ORDER_RULE",
                saved -> saved.getOrderRuleSettings().getId(),
                "UPDATE_WORKFLOW",
                "Updated order workflow settings",
                saved -> settingsMapper.toOrderRuleResponse(saved.getOrderRuleSettings())
        );
    }

    @Transactional
    public List<ReservationRuleResponse> getReservationRules(Authentication authentication, UUID restaurantId) {
        settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        return reservationRuleRepository.findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(restaurantId).stream()
                .map(settingsMapper::toReservationRuleResponse)
                .toList();
    }

    @Transactional
    public ReservationRuleResponse createReservationRule(
            Authentication authentication,
            UUID restaurantId,
            UpsertReservationRuleRequest request
    ) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        SettingsReservationRule rule = new SettingsReservationRule();
        rule.setSettings(settings);
        applyReservationRule(rule, restaurantId, request);
        SettingsReservationRule savedRule = saveReservationRule(rule);
        settingsAuditService.log(
                settings.getRestaurant(),
                savedRule.getBranch(),
                "SETTINGS_RESERVATION_RULE",
                savedRule.getId(),
                "CREATE",
                "Created reservation rule",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toReservationRuleResponse(savedRule);
    }

    @Transactional
    public ReservationRuleResponse getReservationRule(
            Authentication authentication,
            UUID restaurantId,
            UUID ruleId
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        return settingsMapper.toReservationRuleResponse(requireReservationRule(restaurantId, ruleId));
    }

    @Transactional
    public ReservationRuleResponse updateReservationRule(
            Authentication authentication,
            UUID restaurantId,
            UUID ruleId,
            UpsertReservationRuleRequest request
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        SettingsReservationRule rule = requireReservationRule(restaurantId, ruleId);
        applyReservationRule(rule, restaurantId, request);
        SettingsReservationRule savedRule = saveReservationRule(rule);
        settingsAuditService.log(
                savedRule.getSettings().getRestaurant(),
                savedRule.getBranch(),
                "SETTINGS_RESERVATION_RULE",
                savedRule.getId(),
                "UPDATE",
                "Updated reservation rule",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toReservationRuleResponse(savedRule);
    }

    @Transactional
    public ReservationRuleResponse updateReservationRuleStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID ruleId,
            UpdateReservationRuleStatusRequest request
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        SettingsReservationRule rule = requireReservationRule(restaurantId, ruleId);
        rule.setActive(Boolean.TRUE.equals(request.getActive()));
        SettingsReservationRule savedRule = saveReservationRule(rule);
        settingsAuditService.log(
                savedRule.getSettings().getRestaurant(),
                savedRule.getBranch(),
                "SETTINGS_RESERVATION_RULE",
                savedRule.getId(),
                "UPDATE_STATUS",
                "Updated reservation rule status",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toReservationRuleResponse(savedRule);
    }

    @Transactional
    public ReservationRuleResponse updateReservationRulePriority(
            Authentication authentication,
            UUID restaurantId,
            UUID ruleId,
            UpdateReservationRulePriorityRequest request
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        SettingsReservationRule rule = requireReservationRule(restaurantId, ruleId);
        rule.setPriority(request.getPriority());
        SettingsReservationRule savedRule = saveReservationRule(rule);
        settingsAuditService.log(
                savedRule.getSettings().getRestaurant(),
                savedRule.getBranch(),
                "SETTINGS_RESERVATION_RULE",
                savedRule.getId(),
                "UPDATE_PRIORITY",
                "Updated reservation rule priority",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toReservationRuleResponse(savedRule);
    }

    @Transactional
    public void deleteReservationRule(Authentication authentication, UUID restaurantId, UUID ruleId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        SettingsReservationRule rule = requireReservationRule(restaurantId, ruleId);
        reservationRuleRepository.delete(rule);
        reservationRuleRepository.flush();
        settingsAuditService.log(
                rule.getSettings().getRestaurant(),
                rule.getBranch(),
                "SETTINGS_RESERVATION_RULE",
                rule.getId(),
                "DELETE",
                "Deleted reservation rule",
                settingsDomainSupport.currentActorId(authentication)
        );
    }

    @Transactional
    public List<ReservationRuleResponse> reorderReservationRules(
            Authentication authentication,
            UUID restaurantId,
            ReorderReservationRulesRequest request
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        List<SettingsReservationRule> rules = reservationRuleRepository
                .findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(restaurantId);

        Map<UUID, SettingsReservationRule> rulesById = rules.stream()
                .collect(LinkedHashMap::new, (map, rule) -> map.put(rule.getId(), rule), Map::putAll);
        List<UUID> requestedIds = request.getRuleIds();

        if (requestedIds.size() != rules.size()) {
            throw new AuthException("ruleIds must include every reservation rule exactly once", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size() || !rulesById.keySet().equals(uniqueIds)) {
            throw new AuthException("ruleIds must include every reservation rule exactly once", HttpStatus.BAD_REQUEST);
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            rulesById.get(requestedIds.get(index)).setPriority(index);
        }

        List<SettingsReservationRule> savedRules = reservationRuleRepository.saveAllAndFlush(rules);
        settingsAuditService.log(
                savedRules.isEmpty() ? settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId) : savedRules.getFirst().getSettings().getRestaurant(),
                null,
                "SETTINGS_RESERVATION_RULE",
                null,
                "REORDER",
                "Reordered reservation rules",
                settingsDomainSupport.currentActorId(authentication)
        );
        return savedRules.stream()
                .sorted(Comparator.comparingInt(SettingsReservationRule::getPriority)
                        .thenComparing(SettingsReservationRule::getCreatedAt))
                .map(settingsMapper::toReservationRuleResponse)
                .toList();
    }

    @Transactional
    public List<BusinessHourResponse> getBusinessHours(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        return ensureBusinessHours(branch).stream()
                .map(settingsMapper::toBusinessHourResponse)
                .toList();
    }

    @Transactional
    public List<BusinessHourResponse> replaceBusinessHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            ReplaceBusinessHoursRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        validateBusinessHourDefinitions(request.getItems());

        businessHourRepository.deleteAllByBranch_Id(branchId);
        businessHourRepository.flush();

        List<SettingsBusinessHour> created = request.getItems().stream()
                .map(item -> buildBusinessHour(branch, item))
                .toList();

        List<SettingsBusinessHour> savedBusinessHours = businessHourRepository.saveAllAndFlush(created);
        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "SETTINGS_BUSINESS_HOUR",
                null,
                "REPLACE",
                "Replaced branch business hours",
                settingsDomainSupport.currentActorId(authentication)
        );
        return savedBusinessHours.stream()
                .sorted(Comparator.comparingInt(SettingsBusinessHour::getDayOfWeek))
                .map(settingsMapper::toBusinessHourResponse)
                .toList();
    }

    @Transactional
    public BusinessHourResponse getBusinessHour(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            int dayOfWeek
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        validateDayOfWeek(dayOfWeek);
        return settingsMapper.toBusinessHourResponse(requireBusinessHour(branch, dayOfWeek));
    }

    @Transactional
    public BusinessHourResponse updateBusinessHour(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            int dayOfWeek,
            UpsertBusinessHourRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        validateDayOfWeek(dayOfWeek);

        if (request.getDayOfWeek() != dayOfWeek) {
            throw new AuthException("dayOfWeek in path and body must match", HttpStatus.BAD_REQUEST);
        }

        SettingsBusinessHour businessHour = requireBusinessHour(branch, dayOfWeek);
        applyBusinessHour(businessHour, request);
        SettingsBusinessHour savedBusinessHour = saveBusinessHour(businessHour);
        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "SETTINGS_BUSINESS_HOUR",
                savedBusinessHour.getId(),
                "UPDATE",
                "Updated branch business hours for one day",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toBusinessHourResponse(savedBusinessHour);
    }

    @Transactional
    public CopyBusinessHoursResponse copyBusinessHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            CopyBusinessHoursRequest request
    ) {
        Branch sourceBranch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<SettingsBusinessHour> sourceHours = ensureBusinessHours(sourceBranch);
        LinkedHashSet<UUID> targetBranchIds = new LinkedHashSet<>(request.getTargetBranchIds());
        targetBranchIds.remove(branchId);

        for (UUID targetBranchId : targetBranchIds) {
            Branch targetBranch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, targetBranchId);
            businessHourRepository.deleteAllByBranch_Id(targetBranch.getId());
            businessHourRepository.flush();
            businessHourRepository.saveAll(cloneBusinessHours(sourceHours, targetBranch));
            businessHourRepository.flush();
        }

        settingsAuditService.log(
                sourceBranch.getRestaurant(),
                sourceBranch,
                "SETTINGS_BUSINESS_HOUR",
                null,
                "COPY",
                "Copied business hours to " + targetBranchIds.size() + " branches",
                settingsDomainSupport.currentActorId(authentication)
        );

        return CopyBusinessHoursResponse.builder()
                .sourceBranchId(branchId)
                .targetBranchIds(new ArrayList<>(targetBranchIds))
                .copiedDaysPerBranch(sourceHours.size())
                .build();
    }

    @Transactional
    public List<SpecialHourResponse> getSpecialHours(Authentication authentication, UUID restaurantId, UUID branchId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        return specialHourRepository.findAllByBranch_IdOrderBySpecialDateAsc(branchId).stream()
                .map(settingsMapper::toSpecialHourResponse)
                .toList();
    }

    @Transactional
    public SpecialHourResponse createSpecialHour(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpsertSpecialHourRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        SettingsSpecialHour specialHour = new SettingsSpecialHour();
        specialHour.setBranch(branch);
        applySpecialHour(specialHour, request);
        SettingsSpecialHour savedSpecialHour = saveSpecialHour(specialHour);
        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "SETTINGS_SPECIAL_HOUR",
                savedSpecialHour.getId(),
                "CREATE",
                "Created special hours entry",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toSpecialHourResponse(savedSpecialHour);
    }

    @Transactional
    public SpecialHourResponse getSpecialHour(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID specialHourId
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        return settingsMapper.toSpecialHourResponse(requireSpecialHour(branchId, specialHourId));
    }

    @Transactional
    public SpecialHourResponse updateSpecialHour(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID specialHourId,
            UpsertSpecialHourRequest request
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        SettingsSpecialHour specialHour = requireSpecialHour(branchId, specialHourId);
        applySpecialHour(specialHour, request);
        SettingsSpecialHour savedSpecialHour = saveSpecialHour(specialHour);
        settingsAuditService.log(
                savedSpecialHour.getBranch().getRestaurant(),
                savedSpecialHour.getBranch(),
                "SETTINGS_SPECIAL_HOUR",
                savedSpecialHour.getId(),
                "UPDATE",
                "Updated special hours entry",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toSpecialHourResponse(savedSpecialHour);
    }

    @Transactional
    public SpecialHourResponse updateSpecialHourStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID specialHourId,
            UpdateSpecialHourStatusRequest request
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        SettingsSpecialHour specialHour = requireSpecialHour(branchId, specialHourId);
        specialHour.setClosed(Boolean.TRUE.equals(request.getClosed()));
        specialHour.setOpenTime(request.getOpenTime());
        specialHour.setCloseTime(request.getCloseTime());
        SettingsSpecialHour savedSpecialHour = saveSpecialHour(specialHour);
        settingsAuditService.log(
                savedSpecialHour.getBranch().getRestaurant(),
                savedSpecialHour.getBranch(),
                "SETTINGS_SPECIAL_HOUR",
                savedSpecialHour.getId(),
                "UPDATE_STATUS",
                "Updated special hours entry status",
                settingsDomainSupport.currentActorId(authentication)
        );
        return settingsMapper.toSpecialHourResponse(savedSpecialHour);
    }

    @Transactional
    public void deleteSpecialHour(Authentication authentication, UUID restaurantId, UUID branchId, UUID specialHourId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        SettingsSpecialHour specialHour = requireSpecialHour(branchId, specialHourId);
        specialHourRepository.delete(specialHour);
        specialHourRepository.flush();
        settingsAuditService.log(
                specialHour.getBranch().getRestaurant(),
                specialHour.getBranch(),
                "SETTINGS_SPECIAL_HOUR",
                specialHour.getId(),
                "DELETE",
                "Deleted special hours entry",
                settingsDomainSupport.currentActorId(authentication)
        );
    }

    @Transactional
    public List<SpecialHourResponse> bulkUpsertSpecialHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            BulkUpsertSpecialHoursRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        validateDuplicateSpecialDates(request.getItems());

        Map<java.time.LocalDate, SettingsSpecialHour> existingByDate = specialHourRepository
                .findAllByBranch_IdOrderBySpecialDateAsc(branchId).stream()
                .collect(LinkedHashMap::new, (map, hour) -> map.put(hour.getSpecialDate(), hour), Map::putAll);

        List<SettingsSpecialHour> itemsToSave = new ArrayList<>();
        for (UpsertSpecialHourRequest item : request.getItems()) {
            SettingsSpecialHour specialHour = existingByDate.get(item.getSpecialDate());
            if (specialHour == null) {
                specialHour = new SettingsSpecialHour();
                specialHour.setBranch(branch);
            }
            applySpecialHour(specialHour, item);
            itemsToSave.add(specialHour);
        }

        List<SettingsSpecialHour> savedSpecialHours = specialHourRepository.saveAllAndFlush(itemsToSave);
        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "SETTINGS_SPECIAL_HOUR",
                null,
                "BULK_UPSERT",
                "Bulk upserted " + savedSpecialHours.size() + " special hours entries",
                settingsDomainSupport.currentActorId(authentication)
        );
        return savedSpecialHours.stream()
                .sorted(Comparator.comparing(SettingsSpecialHour::getSpecialDate))
                .map(settingsMapper::toSpecialHourResponse)
                .toList();
    }

    @Transactional
    public void bulkDeleteSpecialHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            BulkDeleteSpecialHoursRequest request
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<SettingsSpecialHour> specialHours = request.getSpecialHourIds().stream()
                .map(specialHourId -> requireSpecialHour(branchId, specialHourId))
                .toList();
        specialHourRepository.deleteAll(specialHours);
        specialHourRepository.flush();
        settingsAuditService.log(
                specialHours.isEmpty() ? settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId).getRestaurant() : specialHours.getFirst().getBranch().getRestaurant(),
                specialHours.isEmpty() ? settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId) : specialHours.getFirst().getBranch(),
                "SETTINGS_SPECIAL_HOUR",
                null,
                "BULK_DELETE",
                "Bulk deleted " + specialHours.size() + " special hours entries",
                settingsDomainSupport.currentActorId(authentication)
        );
    }

    private void applyOrderRuleUpdate(SettingsOrderRule orderRuleSettings, UpdateOrderRuleSettingsRequest request) {
        orderRuleSettings.setAutoFireToKitchen(Boolean.TRUE.equals(request.getAutoFireToKitchen()));
        orderRuleSettings.setAllowItemVoid(Boolean.TRUE.equals(request.getAllowItemVoid()));
        orderRuleSettings.setAllowDiscountWithoutManager(Boolean.TRUE.equals(request.getAllowDiscountWithoutManager()));
        orderRuleSettings.setAllowBackdatedOrders(Boolean.TRUE.equals(request.getAllowBackdatedOrders()));
        orderRuleSettings.setRequireReasonForVoid(Boolean.TRUE.equals(request.getRequireReasonForVoid()));
        orderRuleSettings.setRequireReasonForDiscount(Boolean.TRUE.equals(request.getRequireReasonForDiscount()));
        orderRuleSettings.setMergeOrdersEnabled(Boolean.TRUE.equals(request.getMergeOrdersEnabled()));
        orderRuleSettings.setTransferOrdersEnabled(Boolean.TRUE.equals(request.getTransferOrdersEnabled()));
        orderRuleSettings.setReopenClosedOrdersEnabled(Boolean.TRUE.equals(request.getReopenClosedOrdersEnabled()));
    }

    private void applyReceiptUpdate(SettingsReceipt receiptSettings, UpdateReceiptSettingsRequest request) {
        receiptSettings.setAutoPrintCustomerReceipt(Boolean.TRUE.equals(request.getAutoPrintCustomerReceipt()));
        receiptSettings.setAutoPrintKitchenTicket(Boolean.TRUE.equals(request.getAutoPrintKitchenTicket()));
        receiptSettings.setReceiptCopies(request.getReceiptCopies());
        receiptSettings.setShowLogo(Boolean.TRUE.equals(request.getShowLogo()));
        receiptSettings.setShowTaxBreakdown(Boolean.TRUE.equals(request.getShowTaxBreakdown()));
        receiptSettings.setShowServerName(Boolean.TRUE.equals(request.getShowServerName()));
        receiptSettings.setShowTableName(Boolean.TRUE.equals(request.getShowTableName()));
        receiptSettings.setShowOrderNumber(Boolean.TRUE.equals(request.getShowOrderNumber()));
        receiptSettings.setShowQrCode(Boolean.TRUE.equals(request.getShowQrCode()));
        receiptSettings.setPrintVoidedItems(Boolean.TRUE.equals(request.getPrintVoidedItems()));
        receiptSettings.setFooterNote(request.getFooterNote());
    }

    private void applyVoidPolicy(SettingsOrderRule orderRuleSettings, UpdateOrderRuleVoidPolicyRequest request) {
        orderRuleSettings.setAllowItemVoid(Boolean.TRUE.equals(request.getAllowItemVoid()));
        orderRuleSettings.setRequireReasonForVoid(Boolean.TRUE.equals(request.getRequireReasonForVoid()));
    }

    private void applyDiscountPolicy(SettingsOrderRule orderRuleSettings, UpdateOrderRuleDiscountPolicyRequest request) {
        orderRuleSettings.setAllowDiscountWithoutManager(Boolean.TRUE.equals(request.getAllowDiscountWithoutManager()));
        orderRuleSettings.setRequireReasonForDiscount(Boolean.TRUE.equals(request.getRequireReasonForDiscount()));
    }

    private void applyWorkflow(SettingsOrderRule orderRuleSettings, UpdateOrderRuleWorkflowRequest request) {
        orderRuleSettings.setAutoFireToKitchen(Boolean.TRUE.equals(request.getAutoFireToKitchen()));
        orderRuleSettings.setAllowBackdatedOrders(Boolean.TRUE.equals(request.getAllowBackdatedOrders()));
        orderRuleSettings.setMergeOrdersEnabled(Boolean.TRUE.equals(request.getMergeOrdersEnabled()));
        orderRuleSettings.setTransferOrdersEnabled(Boolean.TRUE.equals(request.getTransferOrdersEnabled()));
        orderRuleSettings.setReopenClosedOrdersEnabled(Boolean.TRUE.equals(request.getReopenClosedOrdersEnabled()));
    }

    private List<String> buildReceiptPreviewLines(Settings settings) {
        Restaurant restaurant = settings.getRestaurant();
        SettingsReceipt receiptSettings = settings.getReceiptSettings();
        List<String> lines = new ArrayList<>();

        if (receiptSettings.isShowLogo()) {
            lines.add("[LOGO] " + restaurant.getName());
        } else {
            lines.add(restaurant.getName());
        }

        lines.add("Demo Receipt");

        if (receiptSettings.isShowOrderNumber()) {
            lines.add("Order: " + settings.getOrderSequencePrefix() + "-1001");
        }

        if (receiptSettings.isShowTableName()) {
            lines.add("Table: T12");
        }

        if (receiptSettings.isShowServerName()) {
            lines.add("Server: Demo User");
        }

        lines.add("Subtotal: 42.50 " + restaurant.getCurrency());

        if (receiptSettings.isShowTaxBreakdown()) {
            lines.add("Tax: 3.40 " + restaurant.getCurrency());
        }

        if (settings.isServiceChargeEnabled() && settings.getServiceChargeValue() != null) {
            lines.add("Service Charge: " + settings.getServiceChargeValue().toPlainString());
        }

        lines.add("Total: 45.90 " + restaurant.getCurrency());

        if (receiptSettings.isShowQrCode()) {
            lines.add("QR: https://pos.example/r/" + restaurant.getSlug());
        }

        if (receiptSettings.getFooterNote() != null) {
            lines.add(receiptSettings.getFooterNote());
        }

        return lines;
    }

    private void applyReservationRule(
            SettingsReservationRule rule,
            UUID restaurantId,
            UpsertReservationRuleRequest request
    ) {
        validateReservationRuleRequest(request);
        rule.setBranch(request.getBranchId() == null ? null : settingsDomainSupport.resolveBranch(restaurantId, request.getBranchId()));
        rule.setRuleName(request.getRuleName());
        rule.setPriority(request.getPriority());
        rule.setActive(Boolean.TRUE.equals(request.getActive()));
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setAdvanceBookingDays(request.getAdvanceBookingDays());
        rule.setMinPartySize(request.getMinPartySize());
        rule.setMaxPartySize(request.getMaxPartySize());
        rule.setDefaultDurationMinutes(request.getDefaultDurationMinutes());
        rule.setBufferMinutes(request.getBufferMinutes());
        rule.setAllowOnlineReservations(Boolean.TRUE.equals(request.getAllowOnlineReservations()));
        rule.setRequireDeposit(Boolean.TRUE.equals(request.getRequireDeposit()));
        rule.setDepositType(Boolean.TRUE.equals(request.getRequireDeposit()) ? request.getDepositType() : null);
        rule.setDepositValue(Boolean.TRUE.equals(request.getRequireDeposit()) ? request.getDepositValue() : null);
        rule.setAutoConfirmReservations(Boolean.TRUE.equals(request.getAutoConfirmReservations()));
        rule.setCancellationWindowHours(request.getCancellationWindowHours());
    }

    private void validateReservationRuleRequest(UpsertReservationRuleRequest request) {
        if (request.getMaxPartySize() < request.getMinPartySize()) {
            throw new AuthException("maxPartySize must be greater than or equal to minPartySize", HttpStatus.BAD_REQUEST);
        }

        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw new AuthException("effectiveTo must be after effectiveFrom", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(request.getRequireDeposit())
                && (request.getDepositType() == null || request.getDepositValue() == null)) {
            throw new AuthException("depositType and depositValue are required when requireDeposit is true", HttpStatus.BAD_REQUEST);
        }

        if (request.getDepositValue() != null && request.getDepositValue().signum() < 0) {
            throw new AuthException("depositValue must not be negative", HttpStatus.BAD_REQUEST);
        }
    }

    private SettingsReservationRule requireReservationRule(UUID restaurantId, UUID ruleId) {
        return reservationRuleRepository.findByIdAndSettings_Restaurant_Id(ruleId, restaurantId)
                .orElseThrow(SettingsReservationRuleNotFoundException::new);
    }

    private SettingsReservationRule saveReservationRule(SettingsReservationRule rule) {
        try {
            return reservationRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Reservation rule update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private List<SettingsBusinessHour> ensureBusinessHours(Branch branch) {
        List<SettingsBusinessHour> existing = businessHourRepository.findAllByBranch_IdOrderByDayOfWeekAsc(branch.getId());
        Map<Integer, SettingsBusinessHour> existingByDay = existing.stream()
                .collect(LinkedHashMap::new, (map, hour) -> map.put(hour.getDayOfWeek(), hour), Map::putAll);

        List<SettingsBusinessHour> missing = new ArrayList<>();
        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            if (!existingByDay.containsKey(dayOfWeek)) {
                SettingsBusinessHour businessHour = new SettingsBusinessHour();
                businessHour.setBranch(branch);
                businessHour.setDayOfWeek(dayOfWeek);
                businessHour.setClosed(true);
                businessHour.setOvernight(false);
                missing.add(businessHour);
            }
        }

        if (!missing.isEmpty()) {
            existingByDay.putAll(businessHourRepository.saveAllAndFlush(missing).stream()
                    .collect(LinkedHashMap::new, (map, hour) -> map.put(hour.getDayOfWeek(), hour), Map::putAll));
        }

        return existingByDay.values().stream()
                .sorted(Comparator.comparingInt(SettingsBusinessHour::getDayOfWeek))
                .toList();
    }

    private SettingsBusinessHour requireBusinessHour(Branch branch, int dayOfWeek) {
        ensureBusinessHours(branch);
        return businessHourRepository.findByBranch_IdAndDayOfWeek(branch.getId(), dayOfWeek)
                .orElseThrow(SettingsBusinessHourNotFoundException::new);
    }

    private void validateBusinessHourDefinitions(Collection<UpsertBusinessHourRequest> items) {
        Set<Integer> days = new LinkedHashSet<>();
        for (UpsertBusinessHourRequest item : items) {
            if (!days.add(item.getDayOfWeek())) {
                throw new AuthException("Each dayOfWeek must appear only once", HttpStatus.BAD_REQUEST);
            }
        }

        if (days.size() != 7) {
            throw new AuthException("items must define all 7 days exactly once", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new AuthException("dayOfWeek must be between 1 and 7", HttpStatus.BAD_REQUEST);
        }
    }

    private SettingsBusinessHour buildBusinessHour(Branch branch, UpsertBusinessHourRequest request) {
        SettingsBusinessHour businessHour = new SettingsBusinessHour();
        businessHour.setBranch(branch);
        applyBusinessHour(businessHour, request);
        return businessHour;
    }

    private void applyBusinessHour(SettingsBusinessHour businessHour, UpsertBusinessHourRequest request) {
        businessHour.setDayOfWeek(request.getDayOfWeek());
        businessHour.setClosed(Boolean.TRUE.equals(request.getClosed()));
        businessHour.setOvernight(Boolean.TRUE.equals(request.getOvernight()));
        businessHour.setOpenTime(request.getOpenTime());
        businessHour.setCloseTime(request.getCloseTime());
    }

    private SettingsBusinessHour saveBusinessHour(SettingsBusinessHour businessHour) {
        try {
            return businessHourRepository.saveAndFlush(businessHour);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Business hours update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private List<SettingsBusinessHour> cloneBusinessHours(List<SettingsBusinessHour> sourceHours, Branch targetBranch) {
        return sourceHours.stream()
                .map(sourceHour -> {
                    SettingsBusinessHour businessHour = new SettingsBusinessHour();
                    businessHour.setBranch(targetBranch);
                    businessHour.setDayOfWeek(sourceHour.getDayOfWeek());
                    businessHour.setOpenTime(sourceHour.getOpenTime());
                    businessHour.setCloseTime(sourceHour.getCloseTime());
                    businessHour.setClosed(sourceHour.isClosed());
                    businessHour.setOvernight(sourceHour.isOvernight());
                    return businessHour;
                })
                .toList();
    }

    private SettingsSpecialHour requireSpecialHour(UUID branchId, UUID specialHourId) {
        return specialHourRepository.findByIdAndBranch_Id(specialHourId, branchId)
                .orElseThrow(SettingsSpecialHourNotFoundException::new);
    }

    private void validateDuplicateSpecialDates(Collection<UpsertSpecialHourRequest> items) {
        Set<java.time.LocalDate> dates = new LinkedHashSet<>();
        for (UpsertSpecialHourRequest item : items) {
            if (!dates.add(item.getSpecialDate())) {
                throw new AuthException("specialDate must be unique inside the bulk request", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void applySpecialHour(SettingsSpecialHour specialHour, UpsertSpecialHourRequest request) {
        specialHour.setSpecialDate(request.getSpecialDate());
        specialHour.setClosed(Boolean.TRUE.equals(request.getClosed()));
        specialHour.setOpenTime(request.getOpenTime());
        specialHour.setCloseTime(request.getCloseTime());
        specialHour.setNote(request.getNote());
    }

    private SettingsSpecialHour saveSpecialHour(SettingsSpecialHour specialHour) {
        try {
            return specialHourRepository.saveAndFlush(specialHour);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Special hours update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private SettingsContext loadSettingsContext(Authentication authentication, UUID restaurantId) {
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        Settings settings = settingsDomainSupport.loadOrCreateSettings(authentication, restaurantId);
        return new SettingsContext(actorId, settings);
    }

    private <T> T saveSettingsAndAudit(
            SettingsContext context,
            String entityType,
            Function<Settings, UUID> entityIdExtractor,
            String action,
            String message,
            Function<Settings, T> responseMapper
    ) {
        context.settings().setUpdatedBy(context.actorId());
        Settings savedSettings = settingsDomainSupport.saveSettings(context.settings());
        settingsAuditService.log(
                savedSettings.getRestaurant(),
                null,
                entityType,
                entityIdExtractor.apply(savedSettings),
                action,
                message,
                context.actorId()
        );
        return responseMapper.apply(savedSettings);
    }

    private record SettingsContext(UUID actorId, Settings settings) {
    }
}
