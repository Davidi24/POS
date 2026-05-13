package pos.pos.order.service;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.customer.CustomerNotFoundException;
import pos.pos.exception.order.OrderDiscountNotFoundException;
import pos.pos.exception.order.OrderItemOptionNotFoundException;
import pos.pos.exception.order.OrderLineItemNotFoundException;
import pos.pos.exception.order.OrderNotFoundException;
import pos.pos.exception.reservation.ReservationNotFoundException;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.order.dto.CreateOrderDiscountRequest;
import pos.pos.order.dto.CreateOrderItemOptionRequest;
import pos.pos.order.dto.CreateOrderLineItemRequest;
import pos.pos.order.dto.OrderAuditResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderTotalsResponse;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderEvent;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderDiscountType;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.mapper.OrderMapper;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderSupport {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final EnumSet<OrderStatus> OPEN_ORDER_STATUSES = EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN);
    private static final EnumSet<OrderLineItemStatus> INACTIVE_LINE_ITEM_STATUSES = EnumSet.of(
            OrderLineItemStatus.CANCELLED,
            OrderLineItemStatus.VOIDED
    );
    private static final int ORDER_NUMBER_ATTEMPTS = 16;

    private final RestaurantScopeService restaurantScopeService;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final OptionItemRepository optionItemRepository;
    private final SettingsRepository settingsRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public Order requireOrder(UUID restaurantId, UUID orderId) {
        return orderRepository.findByIdAndRestaurant_Id(orderId, restaurantId)
                .orElseThrow(OrderNotFoundException::new);
    }

    public Order requirePublicOrder(String orderNumber) {
        String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
        if (normalizedOrderNumber == null) {
            throw new AuthException("Order not found", HttpStatus.NOT_FOUND);
        }

        Order order = orderRepository.findTopByOrderNumberOrderByCreatedAtDesc(normalizedOrderNumber)
                .orElseThrow(() -> new AuthException("Order not found", HttpStatus.NOT_FOUND));
        if (order.getSource() != OrderSource.QR_TABLE) {
            throw new AuthException("Order not found", HttpStatus.NOT_FOUND);
        }
        return order;
    }

    public Branch requirePublicBranch(String restaurantSlug, String branchCode) {
        String normalizedRestaurantSlug = NormalizationUtils.normalizeLower(restaurantSlug);
        String normalizedBranchCode = NormalizationUtils.normalizeCode(branchCode, 100);
        if (normalizedRestaurantSlug == null || normalizedBranchCode == null) {
            throw new AuthException("Branch not available for QR ordering", HttpStatus.NOT_FOUND);
        }

        Branch branch = branchRepository.findByRestaurant_SlugAndCodeAndDeletedAtIsNull(
                        normalizedRestaurantSlug,
                        normalizedBranchCode
                )
                .orElseThrow(() -> new AuthException("Branch not available for QR ordering", HttpStatus.NOT_FOUND));

        if (!branch.isActive() || !branch.getRestaurant().isActive()) {
            throw new AuthException("Branch not available for QR ordering", HttpStatus.NOT_FOUND);
        }

        return branch;
    }

    public RestaurantTable requirePublicTable(Branch branch, String tableCode) {
        String normalizedTableCode = NormalizationUtils.normalizeCode(tableCode, 30);
        if (normalizedTableCode == null) {
            throw new AuthException("Table not available for QR ordering", HttpStatus.NOT_FOUND);
        }

        RestaurantTable table = restaurantTableRepository.findByBranch_IdAndTableNumber(branch.getId(), normalizedTableCode)
                .orElseThrow(() -> new AuthException("Table not available for QR ordering", HttpStatus.NOT_FOUND));

        if (!table.isActive()) {
            throw new AuthException("Table not available for QR ordering", HttpStatus.NOT_FOUND);
        }

        return table;
    }

    public Branch resolveManagedBranch(org.springframework.security.core.Authentication authentication, UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            throw new AuthException("branchId is required", HttpStatus.BAD_REQUEST);
        }
        return restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
    }

    public Branch resolveAccessibleBranch(org.springframework.security.core.Authentication authentication, UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            throw new AuthException("branchId is required", HttpStatus.BAD_REQUEST);
        }
        return restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
    }

    public Customer resolveCustomer(UUID restaurantId, UUID customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(customerId, restaurantId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    public Reservation resolveReservation(UUID restaurantId, UUID reservationId) {
        if (reservationId == null) {
            return null;
        }
        return reservationRepository.findByIdAndRestaurant_Id(reservationId, restaurantId)
                .orElseThrow(ReservationNotFoundException::new);
    }

    public RestaurantTable resolveTable(UUID branchId, UUID tableId) {
        if (tableId == null) {
            return null;
        }
        return restaurantTableRepository.findByIdAndBranch_Id(tableId, branchId)
                .orElseThrow(() -> new AuthException("Table not found", HttpStatus.NOT_FOUND));
    }

    public MenuItem requireMenuItem(UUID restaurantId, UUID menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new AuthException("menuItemId references a missing menu item", HttpStatus.BAD_REQUEST));

        if (menuItem.getSection() == null
                || menuItem.getSection().getMenu() == null
                || menuItem.getSection().getMenu().getRestaurant() == null
                || !Objects.equals(menuItem.getSection().getMenu().getRestaurant().getId(), restaurantId)) {
            throw new AuthException("menuItemId must belong to the same restaurant", HttpStatus.BAD_REQUEST);
        }

        if (!menuItem.isAvailable()
                || !menuItem.getSection().isActive()
                || !menuItem.getSection().getMenu().isActive()) {
            throw new AuthException("Selected menu item is not available", HttpStatus.BAD_REQUEST);
        }

        return menuItem;
    }

    public MenuVariant resolveVariant(MenuItem menuItem, UUID variantId) {
        if (variantId == null) {
            return null;
        }

        MenuVariant variant = menuVariantRepository.findById(variantId)
                .orElseThrow(() -> new AuthException("variantId references a missing menu variant", HttpStatus.BAD_REQUEST));

        if (variant.getMenuItem() == null || !Objects.equals(variant.getMenuItem().getId(), menuItem.getId())) {
            throw new AuthException("variantId must belong to the selected menu item", HttpStatus.BAD_REQUEST);
        }

        if (!variant.isActive()) {
            throw new AuthException("Selected menu variant is not active", HttpStatus.BAD_REQUEST);
        }

        return variant;
    }

    public OptionItem requireOptionItem(UUID restaurantId, UUID menuItemId, UUID optionItemId) {
        OptionItem optionItem = optionItemRepository.findById(optionItemId)
                .orElseThrow(() -> new AuthException("optionItemId references a missing option item", HttpStatus.BAD_REQUEST));

        if (optionItem.getOptionGroup() == null
                || optionItem.getOptionGroup().getRestaurant() == null
                || !Objects.equals(optionItem.getOptionGroup().getRestaurant().getId(), restaurantId)) {
            throw new AuthException("optionItemId must belong to the same restaurant", HttpStatus.BAD_REQUEST);
        }

        boolean linkedToMenuItem = optionItem.getOptionGroup().getMenuItemLinks().stream()
                .anyMatch(link -> link.getMenuItem() != null && Objects.equals(link.getMenuItem().getId(), menuItemId));
        if (!linkedToMenuItem) {
            throw new AuthException("Selected option item is not linked to the selected menu item", HttpStatus.BAD_REQUEST);
        }

        if (!optionItem.isAvailable() || !optionItem.getOptionGroup().isActive()) {
            throw new AuthException("Selected option item is not available", HttpStatus.BAD_REQUEST);
        }

        return optionItem;
    }

    public OrderLineItem requireLineItem(Order order, UUID lineItemId) {
        return order.getLineItems().stream()
                .filter(lineItem -> Objects.equals(lineItem.getId(), lineItemId))
                .findFirst()
                .orElseThrow(OrderLineItemNotFoundException::new);
    }

    public OrderItemOption requireOption(OrderLineItem lineItem, UUID optionId) {
        return lineItem.getOptions().stream()
                .filter(option -> Objects.equals(option.getId(), optionId))
                .findFirst()
                .orElseThrow(OrderItemOptionNotFoundException::new);
    }

    public OrderDiscount requireDiscount(Order order, UUID discountId) {
        return order.getDiscounts().stream()
                .filter(discount -> Objects.equals(discount.getId(), discountId))
                .findFirst()
                .orElseThrow(OrderDiscountNotFoundException::new);
    }

    public Settings loadSettings(Restaurant restaurant) {
        if (restaurant == null || restaurant.getId() == null) {
            return new Settings();
        }

        return settingsRepository.findByRestaurant_Id(restaurant.getId())
                .orElseGet(() -> {
                    Settings settings = new Settings();
                    settings.setRestaurant(restaurant);
                    return settings;
                });
    }

    public SettingsOrderRule loadOrderRules(Restaurant restaurant) {
        Settings settings = loadSettings(restaurant);
        return settings.getOrderRuleSettings() == null ? new SettingsOrderRule() : settings.getOrderRuleSettings();
    }

    public void assertQrOrderingEnabled(Restaurant restaurant) {
        if (!loadSettings(restaurant).isEnableQrOrdering()) {
            throw new AuthException("QR ordering is not enabled for this restaurant", HttpStatus.FORBIDDEN);
        }
    }

    public void validateOrderMode(Restaurant restaurant, OrderType orderType) {
        Settings settings = loadSettings(restaurant);
        if (orderType == OrderType.TAKEAWAY && !settings.isEnableTakeaway()) {
            throw new AuthException("Takeaway orders are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        if (orderType == OrderType.DELIVERY && !settings.isEnableDelivery()) {
            throw new AuthException("Delivery orders are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateOpenedAt(Restaurant restaurant, OffsetDateTime openedAt) {
        if (openedAt == null) {
            return;
        }

        if (openedAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1))) {
            throw new AuthException("openedAt must not be in the future", HttpStatus.BAD_REQUEST);
        }

        if (openedAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                && !loadOrderRules(restaurant).isAllowBackdatedOrders()) {
            throw new AuthException("Backdated orders are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
    }

    public void assertTableCanAcceptNewOrder(RestaurantTable table) {
        if (table == null) {
            return;
        }

        Settings settings = loadSettings(table.getRestaurant());
        if (!settings.isAllowOpenTickets()
                && orderRepository.findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(
                        table.getId(),
                        OPEN_ORDER_STATUSES
                ).isPresent()) {
            throw new AuthException("This table already has an open order", HttpStatus.BAD_REQUEST);
        }
    }

    public Optional<Order> findCurrentOpenOrderForTable(UUID tableId) {
        return orderRepository.findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(tableId, OPEN_ORDER_STATUSES);
    }

    public String nextOrderNumber(Restaurant restaurant) {
        String prefix = NormalizationUtils.normalizeCode(loadSettings(restaurant).getOrderSequencePrefix(), 20);
        if (prefix == null) {
            prefix = "ORD";
        }

        for (int attempt = 0; attempt < ORDER_NUMBER_ATTEMPTS; attempt++) {
            String suffix = UuidCreator.getTimeOrdered().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
            String candidate = prefix + "-" + suffix;
            if (!orderRepository.existsByRestaurant_IdAndOrderNumber(restaurant.getId(), candidate)) {
                return candidate;
            }
        }

        throw new AuthException("Order number could not be generated", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public Order saveOrder(Order order) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Order update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void addEvent(Order order, OrderEventType eventType, String note, UUID actorId) {
        OrderEvent event = new OrderEvent();
        event.setEventType(eventType);
        event.setNote(note);
        event.setCreatedBy(actorId);
        order.addEvent(event);
    }

    public void replaceItems(Order order, List<CreateOrderLineItemRequest> requests) {
        List<OrderLineItem> existing = new ArrayList<>(order.getLineItems());
        existing.forEach(order::removeLineItem);

        if (requests == null) {
            return;
        }

        for (CreateOrderLineItemRequest request : requests) {
            order.addLineItem(buildLineItem(order, request));
        }
    }

    public OrderLineItem buildLineItem(Order order, CreateOrderLineItemRequest request) {
        OrderLineItem lineItem = new OrderLineItem();
        applyLineItemRequest(order, lineItem, request, true);
        return lineItem;
    }

    public void applyLineItemRequest(
            Order order,
            OrderLineItem lineItem,
            CreateOrderLineItemRequest request,
            boolean replaceOptions
    ) {
        MenuItem menuItem = requireMenuItem(order.getRestaurant().getId(), request.getMenuItemId());
        MenuVariant variant = resolveVariant(menuItem, request.getVariantId());

        lineItem.setMenuItem(menuItem);
        lineItem.setVariant(variant);
        lineItem.setItemNameSnapshot(menuItem.getName());
        lineItem.setVariantNameSnapshot(variant == null ? null : variant.getName());
        lineItem.setSkuSnapshot(variant != null && variant.getSku() != null ? variant.getSku() : menuItem.getSku());
        lineItem.setQuantity(request.getQuantity());
        lineItem.setUnitPriceSnapshot(defaultMoney(menuItem.getBasePrice()));
        lineItem.setStatus(lineItem.getStatus() == null ? OrderLineItemStatus.PENDING : lineItem.getStatus());
        lineItem.setNotes(request.getNotes());

        if (replaceOptions) {
            lineItem.getOptions().clear();
            addOptions(order, lineItem, request.getOptions());
        }

        refreshLineItemPricing(lineItem);
    }

    public void addOptions(Order order, OrderLineItem lineItem, List<CreateOrderItemOptionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        requireDistinctIds(
                requests.stream().map(CreateOrderItemOptionRequest::getOptionItemId).toList(),
                "options must not contain duplicate optionItemId values"
        );

        for (CreateOrderItemOptionRequest request : requests) {
            lineItem.addOption(buildOption(order, lineItem, request));
        }
    }

    public OrderItemOption buildOption(Order order, OrderLineItem lineItem, CreateOrderItemOptionRequest request) {
        OptionItem optionItem = requireOptionItem(
                order.getRestaurant().getId(),
                lineItem.getMenuItem().getId(),
                request.getOptionItemId()
        );

        OrderItemOption option = new OrderItemOption();
        option.setOptionItem(optionItem);
        option.setOptionNameSnapshot(optionItem.getName());
        option.setPriceDeltaSnapshot(defaultSignedMoney(optionItem.getPriceDelta()));
        option.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        option.setNotes(request.getNotes());
        return option;
    }

    public void replaceDiscounts(Order order, List<CreateOrderDiscountRequest> requests, UUID actorId) {
        List<OrderDiscount> existing = new ArrayList<>(order.getDiscounts());
        existing.forEach(order::removeDiscount);

        if (requests == null) {
            return;
        }

        for (CreateOrderDiscountRequest request : requests) {
            order.addDiscount(buildDiscount(order, request, actorId));
        }
    }

    public OrderDiscount buildDiscount(Order order, CreateOrderDiscountRequest request, UUID actorId) {
        OrderDiscount discount = new OrderDiscount();
        applyDiscountRequest(order, discount, request, actorId);
        return discount;
    }

    public void applyDiscountRequest(Order order, OrderDiscount discount, CreateOrderDiscountRequest request, UUID actorId) {
        if (loadOrderRules(order.getRestaurant()).isRequireReasonForDiscount()
                && NormalizationUtils.normalize(request.getReason()) == null) {
            throw new AuthException("A reason is required for order discounts", HttpStatus.BAD_REQUEST);
        }

        discount.setName(request.getName());
        discount.setDiscountType(request.getDiscountType());
        discount.setDiscountValue(defaultMoney(request.getDiscountValue()));
        discount.setReason(request.getReason());
        discount.setAppliedBy(actorId);
        discount.setAmountApplied(ZERO);
    }

    public void refreshLineItemPricing(OrderLineItem lineItem) {
        BigDecimal baseUnitPrice = defaultMoney(lineItem.getUnitPriceSnapshot());
        BigDecimal variantDelta = lineItem.getVariant() == null ? ZERO : defaultSignedMoney(lineItem.getVariant().getPriceDelta());
        BigDecimal optionDelta = lineItem.getOptions().stream()
                .map(option -> defaultSignedMoney(option.getPriceDeltaSnapshot())
                        .multiply(BigDecimal.valueOf(option.getQuantity())))
                .reduce(ZERO, BigDecimal::add);

        BigDecimal priceDeltaTotal = variantDelta.multiply(BigDecimal.valueOf(lineItem.getQuantity())).add(optionDelta);
        BigDecimal gross = baseUnitPrice.multiply(BigDecimal.valueOf(lineItem.getQuantity())).add(priceDeltaTotal);

        lineItem.setPriceDeltaTotal(money(priceDeltaTotal));
        lineItem.setDiscountTotal(money(defaultMoney(lineItem.getDiscountTotal())));
        lineItem.setTaxTotal(money(defaultMoney(lineItem.getTaxTotal())));
        lineItem.setLineTotal(money(gross.subtract(lineItem.getDiscountTotal()).add(lineItem.getTaxTotal())));
    }

    public void recalculateTotals(Order order) {
        BigDecimal subtotal = ZERO;
        for (OrderLineItem lineItem : order.getLineItems()) {
            refreshLineItemPricing(lineItem);
            if (isFinanciallyActive(lineItem)) {
                subtotal = subtotal.add(grossAmount(lineItem));
            }
        }

        BigDecimal remainingDiscountableBase = money(subtotal);
        BigDecimal discountTotal = ZERO;
        for (OrderDiscount discount : order.getDiscounts().stream()
                .sorted(Comparator.comparing(OrderDiscount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            BigDecimal applied = calculateDiscountAmount(discount, remainingDiscountableBase);
            discount.setAmountApplied(applied);
            discountTotal = discountTotal.add(applied);
            remainingDiscountableBase = maxZero(remainingDiscountableBase.subtract(applied));
        }

        BigDecimal taxTotal = ZERO;
        BigDecimal discountedSubtotal = maxZero(subtotal.subtract(discountTotal));
        BigDecimal serviceChargeTotal = calculateServiceCharge(order.getRestaurant(), discountedSubtotal);
        BigDecimal total = discountedSubtotal.add(taxTotal).add(serviceChargeTotal);

        order.setSubtotal(money(subtotal));
        order.setDiscountTotal(money(discountTotal));
        order.setTaxTotal(money(taxTotal));
        order.setServiceChargeTotal(money(serviceChargeTotal));
        order.setTotal(money(total));
        refreshOrderFulfillment(order);
    }

    public void refreshOrderFulfillment(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.VOIDED) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.CANCELLED);
            return;
        }

        List<OrderLineItem> activeLineItems = activeLineItems(order);
        if (activeLineItems.isEmpty()) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.PENDING);
            return;
        }

        boolean allFulfilled = activeLineItems.stream().allMatch(lineItem -> lineItem.getStatus() == OrderLineItemStatus.FULFILLED);
        boolean allReadyOrFulfilled = activeLineItems.stream().allMatch(lineItem ->
                lineItem.getStatus() == OrderLineItemStatus.READY || lineItem.getStatus() == OrderLineItemStatus.FULFILLED
        );
        boolean anyFulfilled = activeLineItems.stream().anyMatch(lineItem -> lineItem.getStatus() == OrderLineItemStatus.FULFILLED);
        boolean anyProgress = activeLineItems.stream().anyMatch(lineItem ->
                lineItem.getStatus() == OrderLineItemStatus.FIRED
                        || lineItem.getStatus() == OrderLineItemStatus.PREPARING
                        || lineItem.getStatus() == OrderLineItemStatus.READY
                        || lineItem.getStatus() == OrderLineItemStatus.FULFILLED
        );

        if (allFulfilled) {
            order.setFulfillmentStatus(order.getOrderType() == OrderType.DELIVERY
                    ? OrderFulfillmentStatus.DELIVERED
                    : OrderFulfillmentStatus.FULFILLED);
            return;
        }
        if (allReadyOrFulfilled) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.READY);
            return;
        }
        if (anyFulfilled) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.PARTIALLY_FULFILLED);
            return;
        }
        if (anyProgress) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.IN_PREPARATION);
            return;
        }

        order.setFulfillmentStatus(OrderFulfillmentStatus.PENDING);
    }

    public BigDecimal splitAllocatedDiscountTotal(Order order, Collection<OrderLineItem> selectedLineItems) {
        BigDecimal sourceSubtotal = activeLineItems(order).stream()
                .map(this::grossAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal selectedSubtotal = selectedLineItems.stream()
                .map(this::grossAmount)
                .reduce(ZERO, BigDecimal::add);
        if (sourceSubtotal.signum() <= 0 || selectedSubtotal.signum() <= 0) {
            return ZERO;
        }
        return proportionalAmount(order.getDiscountTotal(), selectedSubtotal, sourceSubtotal);
    }

    public OrderLineItem cloneLineItem(OrderLineItem sourceLineItem) {
        OrderLineItem clone = new OrderLineItem();
        clone.setMenuItem(sourceLineItem.getMenuItem());
        clone.setVariant(sourceLineItem.getVariant());
        clone.setItemNameSnapshot(sourceLineItem.getItemNameSnapshot());
        clone.setVariantNameSnapshot(sourceLineItem.getVariantNameSnapshot());
        clone.setSkuSnapshot(sourceLineItem.getSkuSnapshot());
        clone.setQuantity(sourceLineItem.getQuantity());
        clone.setUnitPriceSnapshot(defaultMoney(sourceLineItem.getUnitPriceSnapshot()));
        clone.setPriceDeltaTotal(defaultSignedMoney(sourceLineItem.getPriceDeltaTotal()));
        clone.setDiscountTotal(defaultMoney(sourceLineItem.getDiscountTotal()));
        clone.setTaxTotal(defaultMoney(sourceLineItem.getTaxTotal()));
        clone.setLineTotal(defaultMoney(sourceLineItem.getLineTotal()));
        clone.setStatus(sourceLineItem.getStatus());
        clone.setNotes(sourceLineItem.getNotes());

        for (OrderItemOption option : sourceLineItem.getOptions()) {
            OrderItemOption optionClone = new OrderItemOption();
            optionClone.setOptionItem(option.getOptionItem());
            optionClone.setOptionNameSnapshot(option.getOptionNameSnapshot());
            optionClone.setPriceDeltaSnapshot(defaultSignedMoney(option.getPriceDeltaSnapshot()));
            optionClone.setQuantity(option.getQuantity());
            optionClone.setNotes(option.getNotes());
            clone.addOption(optionClone);
        }

        return clone;
    }

    public OrderDiscount cloneDiscount(OrderDiscount sourceDiscount, BigDecimal amountApplied, UUID actorId) {
        OrderDiscount clone = new OrderDiscount();
        clone.setName(sourceDiscount.getName());
        clone.setDiscountType(sourceDiscount.getDiscountType());
        clone.setDiscountValue(defaultMoney(sourceDiscount.getDiscountValue()));
        clone.setAmountApplied(money(amountApplied));
        clone.setReason(sourceDiscount.getReason());
        clone.setAppliedBy(actorId == null ? sourceDiscount.getAppliedBy() : actorId);
        return clone;
    }

    public void pruneZeroValueDiscounts(Order order) {
        new ArrayList<>(order.getDiscounts()).stream()
                .filter(discount -> defaultMoney(discount.getAmountApplied()).signum() <= 0)
                .forEach(order::removeDiscount);
    }

    public OrderResponse toResponse(Order order) {
        return orderMapper.toResponse(order);
    }

    public OrderResponse toResponse(Order order, boolean includeChildren, boolean includeEvents) {
        return orderMapper.toResponse(order, includeChildren, includeEvents);
    }

    public OrderAuditResponse toAuditResponse(Order order) {
        return orderMapper.toAuditResponse(order);
    }

    public OrderTotalsResponse toTotalsResponse(Order order) {
        return orderMapper.toTotalsResponse(order);
    }

    public OrderLineItemResponse toLineItemResponse(OrderLineItem lineItem) {
        return orderMapper.toLineItemResponse(lineItem);
    }

    public OrderEventResponse toEventResponse(OrderEvent event) {
        return orderMapper.toEventResponse(event);
    }

    public List<OrderLineItem> activeLineItems(Order order) {
        return order.getLineItems().stream()
                .filter(this::isFinanciallyActive)
                .sorted(Comparator.comparing(OrderLineItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public void requireCompleteWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new AuthException("from and to must both be provided together", HttpStatus.BAD_REQUEST);
        }
        if (!to.isAfter(from)) {
            throw new AuthException("to must be after from", HttpStatus.BAD_REQUEST);
        }
    }

    public String normalizeOrderNumber(String orderNumber) {
        return NormalizationUtils.normalizeUpper(orderNumber);
    }

    public boolean isFinanciallyActive(OrderLineItem lineItem) {
        return lineItem.getStatus() != null && !INACTIVE_LINE_ITEM_STATUSES.contains(lineItem.getStatus());
    }

    public void requireDistinctIds(List<UUID> ids, String message) {
        List<UUID> nonNullIds = ids.stream().filter(Objects::nonNull).toList();
        Set<UUID> uniqueIds = new LinkedHashSet<>(nonNullIds);
        if (uniqueIds.size() != nonNullIds.size()) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    public void requireVoidReasonIfNeeded(Order order, String reason) {
        if (loadOrderRules(order.getRestaurant()).isRequireReasonForVoid()
                && NormalizationUtils.normalize(reason) == null) {
            throw new AuthException("A reason is required for void operations", HttpStatus.BAD_REQUEST);
        }
    }

    public void appendReasonToLineItem(OrderLineItem lineItem, String reason) {
        String normalizedReason = NormalizationUtils.normalize(reason);
        if (normalizedReason == null) {
            return;
        }
        String currentNotes = NormalizationUtils.normalize(lineItem.getNotes());
        lineItem.setNotes(currentNotes == null ? normalizedReason : currentNotes + " | " + normalizedReason);
    }

    private BigDecimal grossAmount(OrderLineItem lineItem) {
        return money(defaultMoney(lineItem.getUnitPriceSnapshot())
                .multiply(BigDecimal.valueOf(lineItem.getQuantity()))
                .add(defaultSignedMoney(lineItem.getPriceDeltaTotal())));
    }

    private BigDecimal calculateDiscountAmount(OrderDiscount discount, BigDecimal discountableBase) {
        if (discountableBase.signum() <= 0) {
            return ZERO;
        }

        BigDecimal requestedAmount = switch (discount.getDiscountType()) {
            case PERCENTAGE -> discountableBase
                    .multiply(defaultMoney(discount.getDiscountValue()))
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT, PROMOTION, LOYALTY, MANUAL, COMP -> defaultMoney(discount.getDiscountValue());
        };

        return money(requestedAmount.min(discountableBase));
    }

    private BigDecimal calculateServiceCharge(Restaurant restaurant, BigDecimal discountedSubtotal) {
        Settings settings = loadSettings(restaurant);
        if (!settings.isServiceChargeEnabled()
                || settings.getServiceChargeType() == null
                || settings.getServiceChargeValue() == null
                || discountedSubtotal.signum() <= 0) {
            return ZERO;
        }

        if (settings.getServiceChargeType() == ServiceChargeType.FIXED_AMOUNT) {
            return money(settings.getServiceChargeValue());
        }

        return money(discountedSubtotal
                .multiply(settings.getServiceChargeValue())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal proportionalAmount(BigDecimal totalAmount, BigDecimal selectedSubtotal, BigDecimal sourceSubtotal) {
        if (defaultMoney(totalAmount).signum() <= 0 || selectedSubtotal.signum() <= 0 || sourceSubtotal.signum() <= 0) {
            return ZERO;
        }

        return money(totalAmount
                .multiply(selectedSubtotal)
                .divide(sourceSubtotal, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.signum() < 0 ? ZERO : money(value);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal defaultSignedMoney(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
