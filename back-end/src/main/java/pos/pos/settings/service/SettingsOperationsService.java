package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.settings.dto.BranchBusinessHoursTransferRequest;
import pos.pos.settings.dto.BranchEffectiveSettingsResponse;
import pos.pos.settings.dto.BranchSpecialHoursTransferRequest;
import pos.pos.settings.dto.EffectiveBusinessHoursResponse;
import pos.pos.settings.dto.EffectiveReservationRulesResponse;
import pos.pos.settings.dto.OrderRuleSettingsResponse;
import pos.pos.settings.dto.ReceiptSettingsResponse;
import pos.pos.settings.dto.ReservationRuleResponse;
import pos.pos.settings.dto.ReservationRuleTransferRequest;
import pos.pos.settings.dto.ReplaceBusinessHoursRequest;
import pos.pos.settings.dto.SettingsAuditLogResponse;
import pos.pos.settings.dto.SettingsExportResponse;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.SettingsTransferCoreRequest;
import pos.pos.settings.dto.SettingsTransferRequest;
import pos.pos.settings.dto.SettingsValidationResponse;
import pos.pos.settings.dto.SpecialHourCalendarResponse;
import pos.pos.settings.dto.SpecialHourResponse;
import pos.pos.settings.dto.TodayBusinessHoursResponse;
import pos.pos.settings.dto.UpdateOrderRuleSettingsRequest;
import pos.pos.settings.dto.UpdateReceiptSettingsRequest;
import pos.pos.settings.dto.UpdateRestaurantSettingsRequest;
import pos.pos.settings.dto.UpsertBusinessHourRequest;
import pos.pos.settings.dto.UpsertReservationRuleRequest;
import pos.pos.settings.dto.UpsertSpecialHourRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsAuditLog;
import pos.pos.settings.entity.SettingsReservationRule;
import pos.pos.settings.entity.SettingsSpecialHour;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.repository.SettingsAuditLogRepository;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsSpecialHourRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsOperationsService {

    private static final String EXPORT_SCHEMA_VERSION = "settings.v1";

    private final SettingsService settingsService;
    private final SettingsDetailService settingsDetailService;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;
    private final SettingsAuditLogRepository settingsAuditLogRepository;
    private final SettingsReservationRuleRepository reservationRuleRepository;
    private final SettingsSpecialHourRepository specialHourRepository;
    private final BranchRepository branchRepository;
    private final SettingsMapper settingsMapper;

    @Transactional
    public BranchEffectiveSettingsResponse getEffectiveBranchSettings(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        SettingsResponse settings = settingsService.getSettings(authentication, restaurantId);
        ReceiptSettingsResponse receipt = settingsDetailService.getReceiptSettings(authentication, restaurantId);
        OrderRuleSettingsResponse orderRules = settingsDetailService.getOrderRules(authentication, restaurantId);

        return BranchEffectiveSettingsResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .restaurantCode(branch.getRestaurant().getCode())
                .restaurantTimezone(branch.getRestaurant().getTimezone())
                .defaultBranch(branch.getId().equals(settings.getDefaultBranchId()))
                .settings(settings)
                .receipt(receipt)
                .orderRules(orderRules)
                .build();
    }

    @Transactional
    public EffectiveReservationRulesResponse getEffectiveReservationRules(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of(branch.getRestaurant().getTimezone()));

        List<ReservationRuleResponse> items = settingsDetailService.getReservationRules(authentication, restaurantId).stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getActive()))
                .filter(rule -> rule.getBranchId() == null || branchId.equals(rule.getBranchId()))
                .filter(rule -> rule.getEffectiveFrom() == null || !now.isBefore(rule.getEffectiveFrom()))
                .filter(rule -> rule.getEffectiveTo() == null || now.isBefore(rule.getEffectiveTo()))
                .toList();

        return EffectiveReservationRulesResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .evaluatedAt(now)
                .items(items)
                .build();
    }

    @Transactional
    public TodayBusinessHoursResponse getTodayBusinessHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        LocalDate today = LocalDate.now(ZoneId.of(branch.getRestaurant().getTimezone()));
        int dayOfWeek = today.getDayOfWeek().getValue();

        var businessHour = settingsDetailService.getBusinessHour(authentication, restaurantId, branchId, dayOfWeek);
        List<SpecialHourResponse> specialHours = specialHourRepository
                .findAllByBranch_IdAndSpecialDateBetweenOrderBySpecialDateAsc(branchId, today, today).stream()
                .map(settingsMapper::toSpecialHourResponse)
                .toList();
        SpecialHourResponse specialHour = specialHours.isEmpty() ? null : specialHours.get(0);

        return TodayBusinessHoursResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .date(today)
                .dayOfWeek(dayOfWeek)
                .specialHoursApplied(specialHour != null)
                .closed(specialHour != null ? specialHour.getClosed() : businessHour.getClosed())
                .overnight(specialHour == null ? businessHour.getOvernight() : false)
                .openTime(specialHour != null ? specialHour.getOpenTime() : businessHour.getOpenTime())
                .closeTime(specialHour != null ? specialHour.getCloseTime() : businessHour.getCloseTime())
                .businessHour(businessHour)
                .specialHour(specialHour)
                .build();
    }

    @Transactional
    public EffectiveBusinessHoursResponse getEffectiveBusinessHours(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        LocalDate startDate = LocalDate.now(ZoneId.of(branch.getRestaurant().getTimezone()));
        LocalDate endDate = startDate.plusDays(30);

        return EffectiveBusinessHoursResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .evaluatedAt(OffsetDateTime.now(ZoneId.of(branch.getRestaurant().getTimezone())))
                .weeklySchedule(settingsDetailService.getBusinessHours(authentication, restaurantId, branchId))
                .upcomingSpecialHours(specialHourRepository
                        .findAllByBranch_IdAndSpecialDateBetweenOrderBySpecialDateAsc(branchId, startDate, endDate).stream()
                        .map(settingsMapper::toSpecialHourResponse)
                        .toList())
                .build();
    }

    @Transactional
    public SpecialHourCalendarResponse getSpecialHoursCalendar(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        LocalDate resolvedStartDate = startDate == null
                ? LocalDate.now(ZoneId.of(branch.getRestaurant().getTimezone()))
                : startDate;
        LocalDate resolvedEndDate = endDate == null ? resolvedStartDate.plusDays(30) : endDate;

        if (resolvedEndDate.isBefore(resolvedStartDate)) {
            throw new AuthException("endDate must be on or after startDate", HttpStatus.BAD_REQUEST);
        }

        return SpecialHourCalendarResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .startDate(resolvedStartDate)
                .endDate(resolvedEndDate)
                .items(specialHourRepository
                        .findAllByBranch_IdAndSpecialDateBetweenOrderBySpecialDateAsc(branchId, resolvedStartDate, resolvedEndDate).stream()
                        .map(settingsMapper::toSpecialHourResponse)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public SettingsValidationResponse validateTransferPayload(
            Authentication authentication,
            UUID restaurantId,
            SettingsTransferRequest request
    ) {
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        List<String> issues = collectTransferPayloadIssues(restaurant, request);

        return SettingsValidationResponse.builder()
                .valid(issues.isEmpty())
                .issues(issues)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingsAuditLogResponse> getSettingsHistory(
            Authentication authentication,
            UUID restaurantId,
            Integer page,
            Integer size
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Page<SettingsAuditLogResponse> historyPage = settingsAuditLogRepository
                .findAllByRestaurant_IdOrderByCreatedAtDesc(
                        restaurantId,
                        PageRequest.of(page == null ? 0 : page, size == null ? 50 : size, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .map(this::toAuditLogResponse);

        return PageResponse.from(historyPage);
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingsAuditLogResponse> getSettingsAuditLogs(
            Authentication authentication,
            UUID restaurantId,
            Integer page,
            Integer size
    ) {
        return getSettingsHistory(authentication, restaurantId, page, size);
    }

    @Transactional
    public SettingsExportResponse exportSettings(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Map<UUID, Branch> branchesById = branchRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId).stream()
                .collect(Collectors.toMap(Branch::getId, branch -> branch, (left, right) -> left, LinkedHashMap::new));

        SettingsResponse settings = settingsService.getSettings(authentication, restaurantId);
        ReceiptSettingsResponse receipt = settingsDetailService.getReceiptSettings(authentication, restaurantId);
        OrderRuleSettingsResponse orderRules = settingsDetailService.getOrderRules(authentication, restaurantId);
        List<ReservationRuleResponse> reservationRules = settingsDetailService.getReservationRules(authentication, restaurantId);

        List<BranchBusinessHoursTransferRequest> businessHours = new ArrayList<>();
        List<BranchSpecialHoursTransferRequest> specialHours = new ArrayList<>();
        for (Branch branch : branchesById.values()) {
            businessHours.add(BranchBusinessHoursTransferRequest.builder()
                    .branchCode(branch.getCode())
                    .items(settingsDetailService.getBusinessHours(authentication, restaurantId, branch.getId()).stream()
                            .map(hour -> UpsertBusinessHourRequest.builder()
                                    .dayOfWeek(hour.getDayOfWeek())
                                    .openTime(hour.getOpenTime())
                                    .closeTime(hour.getCloseTime())
                                    .closed(hour.getClosed())
                                    .overnight(hour.getOvernight())
                                    .build())
                            .toList())
                    .build());

            specialHours.add(BranchSpecialHoursTransferRequest.builder()
                    .branchCode(branch.getCode())
                    .items(settingsDetailService.getSpecialHours(authentication, restaurantId, branch.getId()).stream()
                            .map(hour -> UpsertSpecialHourRequest.builder()
                                    .specialDate(hour.getSpecialDate())
                                    .openTime(hour.getOpenTime())
                                    .closeTime(hour.getCloseTime())
                                    .closed(hour.getClosed())
                                    .note(hour.getNote())
                                    .build())
                            .toList())
                    .build());
        }

        SettingsTransferRequest payload = SettingsTransferRequest.builder()
                .core(toTransferCore(settings, branchesById))
                .receipt(toReceiptUpdateRequest(receipt))
                .orderRules(toOrderRuleUpdateRequest(orderRules))
                .reservationRules(reservationRules.stream()
                        .map(rule -> ReservationRuleTransferRequest.builder()
                                .branchCode(rule.getBranchId() == null ? null : branchesById.get(rule.getBranchId()).getCode())
                                .ruleName(rule.getRuleName())
                                .priority(rule.getPriority())
                                .active(rule.getActive())
                                .effectiveFrom(rule.getEffectiveFrom())
                                .effectiveTo(rule.getEffectiveTo())
                                .advanceBookingDays(rule.getAdvanceBookingDays())
                                .minPartySize(rule.getMinPartySize())
                                .maxPartySize(rule.getMaxPartySize())
                                .defaultDurationMinutes(rule.getDefaultDurationMinutes())
                                .bufferMinutes(rule.getBufferMinutes())
                                .allowOnlineReservations(rule.getAllowOnlineReservations())
                                .requireDeposit(rule.getRequireDeposit())
                                .depositType(rule.getDepositType())
                                .depositValue(rule.getDepositValue())
                                .autoConfirmReservations(rule.getAutoConfirmReservations())
                                .cancellationWindowHours(rule.getCancellationWindowHours())
                                .build())
                        .toList())
                .businessHours(businessHours)
                .specialHours(specialHours)
                .build();

        settingsAuditService.log(
                restaurant,
                null,
                "SETTINGS",
                settings.getId(),
                "EXPORT",
                "Exported restaurant settings",
                settingsDomainSupport.currentActorId(authentication)
        );

        return SettingsExportResponse.builder()
                .schemaVersion(EXPORT_SCHEMA_VERSION)
                .restaurantId(restaurantId)
                .restaurantCode(restaurant.getCode())
                .exportedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .payload(payload)
                .build();
    }

    @Transactional
    public SettingsExportResponse importSettings(
            Authentication authentication,
            UUID restaurantId,
            SettingsTransferRequest request
    ) {
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        List<String> issues = collectTransferPayloadIssues(restaurant, request);
        if (!issues.isEmpty()) {
            throw new AuthException(String.join(", ", issues), HttpStatus.BAD_REQUEST);
        }

        applyTransferPayload(authentication, restaurant, request);

        settingsAuditService.log(
                restaurant,
                null,
                "SETTINGS",
                null,
                "IMPORT",
                "Imported restaurant settings payload",
                settingsDomainSupport.currentActorId(authentication)
        );

        return exportSettings(authentication, restaurantId);
    }

    @Transactional
    public SettingsExportResponse cloneSettings(
            Authentication authentication,
            UUID restaurantId,
            UUID sourceRestaurantId
    ) {
        SettingsExportResponse sourceExport = exportSettings(authentication, sourceRestaurantId);
        Restaurant targetRestaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        applyTransferPayload(authentication, targetRestaurant, sourceExport.getPayload());

        settingsAuditService.log(
                targetRestaurant,
                null,
                "SETTINGS",
                null,
                "CLONE",
                "Cloned settings from restaurant " + sourceExport.getRestaurantCode(),
                settingsDomainSupport.currentActorId(authentication)
        );

        return exportSettings(authentication, restaurantId);
    }

    private void applyTransferPayload(Authentication authentication, Restaurant restaurant, SettingsTransferRequest request) {
        Map<String, Branch> branchesByCode = resolveBranchesByCode(restaurant.getId());

        settingsService.updateSettings(authentication, restaurant.getId(), toCoreUpdateRequest(request.getCore(), branchesByCode));
        settingsDetailService.updateReceiptSettings(authentication, restaurant.getId(), request.getReceipt());
        settingsDetailService.updateOrderRules(authentication, restaurant.getId(), request.getOrderRules());

        replaceReservationRules(restaurant, request.getReservationRules(), branchesByCode);

        for (BranchBusinessHoursTransferRequest branchBusinessHours : emptyIfNull(request.getBusinessHours())) {
            Branch branch = branchesByCode.get(normalizeBranchCode(branchBusinessHours.getBranchCode()));
            settingsDetailService.replaceBusinessHours(
                    authentication,
                    restaurant.getId(),
                    branch.getId(),
                    ReplaceBusinessHoursRequest.builder().items(branchBusinessHours.getItems()).build()
            );
        }

        for (BranchSpecialHoursTransferRequest branchSpecialHours : emptyIfNull(request.getSpecialHours())) {
            Branch branch = branchesByCode.get(normalizeBranchCode(branchSpecialHours.getBranchCode()));
            replaceSpecialHours(branch, branchSpecialHours.getItems());
        }
    }

    private void replaceReservationRules(
            Restaurant restaurant,
            List<ReservationRuleTransferRequest> rules,
            Map<String, Branch> branchesByCode
    ) {
        Settings settings = settingsDomainSupport.loadOrCreateSettings(restaurant, null);
        List<SettingsReservationRule> existingRules = reservationRuleRepository
                .findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(restaurant.getId());
        if (!existingRules.isEmpty()) {
            reservationRuleRepository.deleteAll(existingRules);
            reservationRuleRepository.flush();
        }

        List<SettingsReservationRule> itemsToSave = new ArrayList<>();
        for (ReservationRuleTransferRequest rule : emptyIfNull(rules)) {
            validateReservationTransferRule(rule);

            SettingsReservationRule item = new SettingsReservationRule();
            item.setSettings(settings);
            item.setBranch(rule.getBranchCode() == null ? null : branchesByCode.get(normalizeBranchCode(rule.getBranchCode())));
            item.setRuleName(rule.getRuleName());
            item.setPriority(rule.getPriority());
            item.setActive(Boolean.TRUE.equals(rule.getActive()));
            item.setEffectiveFrom(rule.getEffectiveFrom());
            item.setEffectiveTo(rule.getEffectiveTo());
            item.setAdvanceBookingDays(rule.getAdvanceBookingDays());
            item.setMinPartySize(rule.getMinPartySize());
            item.setMaxPartySize(rule.getMaxPartySize());
            item.setDefaultDurationMinutes(rule.getDefaultDurationMinutes());
            item.setBufferMinutes(rule.getBufferMinutes());
            item.setAllowOnlineReservations(Boolean.TRUE.equals(rule.getAllowOnlineReservations()));
            item.setRequireDeposit(Boolean.TRUE.equals(rule.getRequireDeposit()));
            item.setDepositType(Boolean.TRUE.equals(rule.getRequireDeposit()) ? rule.getDepositType() : null);
            item.setDepositValue(Boolean.TRUE.equals(rule.getRequireDeposit()) ? rule.getDepositValue() : null);
            item.setAutoConfirmReservations(Boolean.TRUE.equals(rule.getAutoConfirmReservations()));
            item.setCancellationWindowHours(rule.getCancellationWindowHours());
            itemsToSave.add(item);
        }

        if (!itemsToSave.isEmpty()) {
            reservationRuleRepository.saveAllAndFlush(itemsToSave);
        }
    }

    private void replaceSpecialHours(Branch branch, List<UpsertSpecialHourRequest> items) {
        validateDuplicateSpecialDates(items);
        List<SettingsSpecialHour> existingSpecialHours = specialHourRepository.findAllByBranch_IdOrderBySpecialDateAsc(branch.getId());
        if (!existingSpecialHours.isEmpty()) {
            specialHourRepository.deleteAll(existingSpecialHours);
            specialHourRepository.flush();
        }

        List<SettingsSpecialHour> newSpecialHours = new ArrayList<>();
        for (UpsertSpecialHourRequest item : emptyIfNull(items)) {
            SettingsSpecialHour specialHour = new SettingsSpecialHour();
            specialHour.setBranch(branch);
            specialHour.setSpecialDate(item.getSpecialDate());
            specialHour.setOpenTime(item.getOpenTime());
            specialHour.setCloseTime(item.getCloseTime());
            specialHour.setClosed(Boolean.TRUE.equals(item.getClosed()));
            specialHour.setNote(item.getNote());
            newSpecialHours.add(specialHour);
        }

        if (!newSpecialHours.isEmpty()) {
            specialHourRepository.saveAllAndFlush(newSpecialHours);
        }
    }

    private List<String> collectTransferPayloadIssues(Restaurant restaurant, SettingsTransferRequest request) {
        Map<String, Branch> branchesByCode = resolveBranchesByCode(restaurant.getId());
        List<String> issues = new ArrayList<>();

        if (request.getCore().getDefaultBranchCode() != null
                && !branchesByCode.containsKey(normalizeBranchCode(request.getCore().getDefaultBranchCode()))) {
            issues.add("core.defaultBranchCode must reference a branch in the target restaurant");
        }

        for (ReservationRuleTransferRequest rule : emptyIfNull(request.getReservationRules())) {
            if (rule.getBranchCode() != null
                    && !branchesByCode.containsKey(normalizeBranchCode(rule.getBranchCode()))) {
                issues.add("reservationRules.branchCode must reference a branch in the target restaurant");
            }

            if (rule.getMaxPartySize() < rule.getMinPartySize()) {
                issues.add("reservationRules.maxPartySize must be greater than or equal to minPartySize");
            }

            if (rule.getEffectiveFrom() != null
                    && rule.getEffectiveTo() != null
                    && !rule.getEffectiveTo().isAfter(rule.getEffectiveFrom())) {
                issues.add("reservationRules.effectiveTo must be after effectiveFrom");
            }

            if (Boolean.TRUE.equals(rule.getRequireDeposit())
                    && (rule.getDepositType() == null || rule.getDepositValue() == null)) {
                issues.add("reservationRules depositType and depositValue are required when requireDeposit is true");
            }
        }

        Set<String> seenBusinessHourBranches = new LinkedHashSet<>();
        for (BranchBusinessHoursTransferRequest businessHours : emptyIfNull(request.getBusinessHours())) {
            String branchCode = normalizeBranchCode(businessHours.getBranchCode());
            if (!branchesByCode.containsKey(branchCode)) {
                issues.add("businessHours.branchCode must reference a branch in the target restaurant");
            }
            if (!seenBusinessHourBranches.add(branchCode)) {
                issues.add("businessHours.branchCode must be unique");
            }
            collectBusinessHourIssues(businessHours.getItems(), issues);
        }

        Set<String> seenSpecialHourBranches = new LinkedHashSet<>();
        for (BranchSpecialHoursTransferRequest specialHours : emptyIfNull(request.getSpecialHours())) {
            String branchCode = normalizeBranchCode(specialHours.getBranchCode());
            if (!branchesByCode.containsKey(branchCode)) {
                issues.add("specialHours.branchCode must reference a branch in the target restaurant");
            }
            if (!seenSpecialHourBranches.add(branchCode)) {
                issues.add("specialHours.branchCode must be unique");
            }
            validateDuplicateSpecialDates(specialHours.getItems(), issues);
        }

        return issues;
    }

    private void collectBusinessHourIssues(Collection<UpsertBusinessHourRequest> items, List<String> issues) {
        Set<Integer> days = new LinkedHashSet<>();
        for (UpsertBusinessHourRequest item : items) {
            if (!days.add(item.getDayOfWeek())) {
                issues.add("businessHours.items dayOfWeek must be unique");
            }
        }
        if (days.size() != 7) {
            issues.add("businessHours.items must define all 7 days exactly once");
        }
    }

    private void validateReservationTransferRule(ReservationRuleTransferRequest rule) {
        if (rule.getMaxPartySize() < rule.getMinPartySize()) {
            throw new AuthException("maxPartySize must be greater than or equal to minPartySize", HttpStatus.BAD_REQUEST);
        }
        if (rule.getEffectiveFrom() != null
                && rule.getEffectiveTo() != null
                && !rule.getEffectiveTo().isAfter(rule.getEffectiveFrom())) {
            throw new AuthException("effectiveTo must be after effectiveFrom", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(rule.getRequireDeposit())
                && (rule.getDepositType() == null || rule.getDepositValue() == null)) {
            throw new AuthException("depositType and depositValue are required when requireDeposit is true", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDuplicateSpecialDates(Collection<UpsertSpecialHourRequest> items) {
        List<String> issues = new ArrayList<>();
        validateDuplicateSpecialDates(items, issues);
        if (!issues.isEmpty()) {
            throw new AuthException(String.join(", ", issues), HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDuplicateSpecialDates(Collection<UpsertSpecialHourRequest> items, List<String> issues) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (UpsertSpecialHourRequest item : emptyIfNull(items)) {
            if (!dates.add(item.getSpecialDate())) {
                issues.add("specialHours.items specialDate must be unique");
            }
        }
    }

    private Map<String, Branch> resolveBranchesByCode(UUID restaurantId) {
        return branchRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId).stream()
                .collect(Collectors.toMap(
                        branch -> normalizeBranchCode(branch.getCode()),
                        branch -> branch,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String normalizeBranchCode(String branchCode) {
        return NormalizationUtils.normalizeUpper(branchCode);
    }

    private SettingsTransferCoreRequest toTransferCore(SettingsResponse settings, Map<UUID, Branch> branchesById) {
        return SettingsTransferCoreRequest.builder()
                .defaultBranchCode(settings.getDefaultBranchId() == null ? null : branchesById.get(settings.getDefaultBranchId()).getCode())
                .defaultLanguage(settings.getDefaultLanguage())
                .dateFormat(settings.getDateFormat())
                .timeFormat(settings.getTimeFormat())
                .weekStartDay(settings.getWeekStartDay())
                .orderSequencePrefix(settings.getOrderSequencePrefix())
                .invoiceSequencePrefix(settings.getInvoiceSequencePrefix())
                .reservationSlotMinutes(settings.getReservationSlotMinutes())
                .defaultTableTurnTimeMinutes(settings.getDefaultTableTurnTimeMinutes())
                .serviceChargeEnabled(settings.getServiceChargeEnabled())
                .serviceChargeType(settings.getServiceChargeType())
                .serviceChargeValue(settings.getServiceChargeValue())
                .cashRoundingEnabled(settings.getCashRoundingEnabled())
                .cashRoundingIncrement(settings.getCashRoundingIncrement())
                .allowSplitBills(settings.getAllowSplitBills())
                .allowOpenTickets(settings.getAllowOpenTickets())
                .requireCustomerForInvoice(settings.getRequireCustomerForInvoice())
                .enableQrOrdering(settings.getEnableQrOrdering())
                .enableTakeaway(settings.getEnableTakeaway())
                .enableDelivery(settings.getEnableDelivery())
                .build();
    }

    private UpdateRestaurantSettingsRequest toCoreUpdateRequest(
            SettingsTransferCoreRequest core,
            Map<String, Branch> branchesByCode
    ) {
        Branch defaultBranch = core.getDefaultBranchCode() == null ? null : branchesByCode.get(normalizeBranchCode(core.getDefaultBranchCode()));
        return UpdateRestaurantSettingsRequest.builder()
                .defaultBranchId(defaultBranch == null ? null : defaultBranch.getId())
                .defaultLanguage(core.getDefaultLanguage())
                .dateFormat(core.getDateFormat())
                .timeFormat(core.getTimeFormat())
                .weekStartDay(core.getWeekStartDay())
                .orderSequencePrefix(core.getOrderSequencePrefix())
                .invoiceSequencePrefix(core.getInvoiceSequencePrefix())
                .reservationSlotMinutes(core.getReservationSlotMinutes())
                .defaultTableTurnTimeMinutes(core.getDefaultTableTurnTimeMinutes())
                .serviceChargeEnabled(core.getServiceChargeEnabled())
                .serviceChargeType(core.getServiceChargeType())
                .serviceChargeValue(core.getServiceChargeValue())
                .cashRoundingEnabled(core.getCashRoundingEnabled())
                .cashRoundingIncrement(core.getCashRoundingIncrement())
                .allowSplitBills(core.getAllowSplitBills())
                .allowOpenTickets(core.getAllowOpenTickets())
                .requireCustomerForInvoice(core.getRequireCustomerForInvoice())
                .enableQrOrdering(core.getEnableQrOrdering())
                .enableTakeaway(core.getEnableTakeaway())
                .enableDelivery(core.getEnableDelivery())
                .build();
    }

    private UpdateReceiptSettingsRequest toReceiptUpdateRequest(ReceiptSettingsResponse receipt) {
        return UpdateReceiptSettingsRequest.builder()
                .autoPrintCustomerReceipt(receipt.getAutoPrintCustomerReceipt())
                .autoPrintKitchenTicket(receipt.getAutoPrintKitchenTicket())
                .receiptCopies(receipt.getReceiptCopies())
                .showLogo(receipt.getShowLogo())
                .showTaxBreakdown(receipt.getShowTaxBreakdown())
                .showServerName(receipt.getShowServerName())
                .showTableName(receipt.getShowTableName())
                .showOrderNumber(receipt.getShowOrderNumber())
                .showQrCode(receipt.getShowQrCode())
                .printVoidedItems(receipt.getPrintVoidedItems())
                .footerNote(receipt.getFooterNote())
                .build();
    }

    private UpdateOrderRuleSettingsRequest toOrderRuleUpdateRequest(OrderRuleSettingsResponse orderRules) {
        return UpdateOrderRuleSettingsRequest.builder()
                .autoFireToKitchen(orderRules.getAutoFireToKitchen())
                .allowItemVoid(orderRules.getAllowItemVoid())
                .allowDiscountWithoutManager(orderRules.getAllowDiscountWithoutManager())
                .allowBackdatedOrders(orderRules.getAllowBackdatedOrders())
                .requireReasonForVoid(orderRules.getRequireReasonForVoid())
                .requireReasonForDiscount(orderRules.getRequireReasonForDiscount())
                .mergeOrdersEnabled(orderRules.getMergeOrdersEnabled())
                .transferOrdersEnabled(orderRules.getTransferOrdersEnabled())
                .reopenClosedOrdersEnabled(orderRules.getReopenClosedOrdersEnabled())
                .build();
    }

    private SettingsAuditLogResponse toAuditLogResponse(SettingsAuditLog auditLog) {
        return SettingsAuditLogResponse.builder()
                .id(auditLog.getId())
                .restaurantId(auditLog.getRestaurant() == null ? null : auditLog.getRestaurant().getId())
                .branchId(auditLog.getBranch() == null ? null : auditLog.getBranch().getId())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction())
                .message(auditLog.getMessage())
                .actorUserId(auditLog.getActorUserId())
                .occurredAt(auditLog.getCreatedAt())
                .build();
    }

    private <T> List<T> emptyIfNull(List<T> items) {
        return items == null ? List.of() : items;
    }

    private <T> Collection<T> emptyIfNull(Collection<T> items) {
        return items == null ? List.of() : items;
    }
}
