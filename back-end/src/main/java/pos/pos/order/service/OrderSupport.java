package pos.pos.order.service;

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
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.repository.InventoryMovementRepository;
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
import pos.pos.tables.enums.TableStatus;
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
    private final pos.pos.order.realtime.OrderChangeNotifier orderChangeNotifier;
    private final pos.pos.payment.service.PaymentCalculator paymentCalculator;
    private final InventoryMovementRepository inventoryMovementRepository;

    public Order requireOrder(UUID restaurantId, UUID orderId) {
        boolean writing = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return (writing ? orderRepository.findForUpdate(orderId, restaurantId) : orderRepository.findByIdAndRestaurant_Id(orderId, restaurantId))
                .orElseThrow(OrderNotFoundException::new);
    }

    public void assertLineItemNotSaleConsumed(OrderLineItem lineItem) {
        if (inventoryMovementRepository.existsByOrderLineItem_IdAndMovementType(
                lineItem.getId(), InventoryMovementType.SALE_CONSUMPTION
        )) {
            throw new AuthException("This item's ingredients were already consumed and it can no longer be changed", HttpStatus.CONFLICT);
        }
    }

    public void assertNoSaleConsumedItems(Order order, String operation) {
        boolean hasConsumedItems = order.getLineItems().stream()
                .anyMatch(lineItem -> inventoryMovementRepository.existsByOrderLineItem_IdAndMovementType(
                        lineItem.getId(), InventoryMovementType.SALE_CONSUMPTION
                ));
        if (hasConsumedItems) {
            throw new AuthException("Orders with consumed inventory cannot be " + operation, HttpStatus.CONFLICT);
        }
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

    public RestaurantTable resolveTableForUpdate(UUID branchId, UUID tableId) {
        if (tableId == null) {
            return null;
        }
        return restaurantTableRepository.findByIdAndBranchIdForUpdate(tableId, branchId)
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

        assertTableCanReceiveOrder(table);

        Settings settings = loadSettings(table.getRestaurant());
        if (!settings.isAllowOpenTickets()
                && orderRepository.findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(
                        table.getId(),
                        OPEN_ORDER_STATUSES
                ).isPresent()) {
            throw new AuthException("This table already has an open order", HttpStatus.BAD_REQUEST);
        }
    }

    public void assertTableCanReceiveOrder(RestaurantTable table) {
        if (table == null) {
            return;
        }
        if (!table.isActive()) {
            throw new AuthException("An inactive table cannot receive an order", HttpStatus.CONFLICT);
        }
        if (table.getMergedInto() != null) {
            throw new AuthException("A merged table cannot receive a new order", HttpStatus.CONFLICT);
        }
        if (table.getStatus() != TableStatus.AVAILABLE && table.getStatus() != TableStatus.OCCUPIED) {
            throw new AuthException("This table is not available for a new order", HttpStatus.CONFLICT);
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
            // Truncating a time-ordered UUID repeats the same timestamp prefix during bursts.
            String suffix = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 12)
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
            Order saved = orderRepository.saveAndFlush(order);
            notifyOrderBranchChanged(saved);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Order update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * With "Send items to the kitchen automatically" on, the new items of an open order go to the kitchen at once.
     * Returns true when something was sent, so the caller syncs the kitchen display after saving.
     */
    public boolean autoFireIfEnabled(Order order, UUID actorId) {
        if (order.getStatus() != OrderStatus.OPEN || !loadOrderRules(order.getRestaurant()).isAutoFireToKitchen()) {
            return false;
        }
        List<OrderLineItem> pending = order.getLineItems().stream()
                .filter(this::isFinanciallyActive)
                .filter(lineItem -> lineItem.getStatus() == OrderLineItemStatus.PENDING)
                .filter(OrderLineItem::goesToKitchen)
                .toList();
        if (pending.isEmpty()) {
            return false;
        }
        pending.forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.FIRED));
        order.setFulfillmentStatus(OrderFulfillmentStatus.IN_PREPARATION);
        recalculateTotals(order);
        addEvent(order, OrderEventType.SENT_TO_KITCHEN, "Sent to the kitchen automatically", actorId);
        return true;
    }

    // Permission check on the signed-in user of this request; work without a user (jobs, guests) is not limited.
    private static boolean currentUserHas(String authority) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    // True while the order still holds money from a payment (captured and not fully refunded or voided).
    public boolean hasMoneyTaken(Order order) {
        if (order.getPayments() == null) {
            return false;
        }
        return order.getPayments().stream()
                .filter(payment -> payment.getStatus() == pos.pos.payment.enums.PaymentStatus.CAPTURED
                        || payment.getStatus() == pos.pos.payment.enums.PaymentStatus.PARTIALLY_REFUNDED
                        || payment.getStatus() == pos.pos.payment.enums.PaymentStatus.AUTHORIZED)
                .anyMatch(payment -> payment.getAmount().add(payment.getTipAmount()).add(payment.getSurchargeAmount())
                        .subtract(payment.getRefundedAmount()).signum() > 0);
    }

    public void notifyOrderBranchChanged(Order order) {
        orderChangeNotifier.changedAfterCommit(order.getRestaurant().getId(), order.getBranch().getId());
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

        boolean sameItem = lineItem.getMenuItem() != null && Objects.equals(lineItem.getMenuItem().getId(), menuItem.getId());
        boolean sameVariant = Objects.equals(lineItem.getVariant() == null ? null : lineItem.getVariant().getId(), variant == null ? null : variant.getId());
        var oldOptions = new java.util.HashMap<UUID, OrderItemOption>();
        lineItem.getOptions().forEach(o -> oldOptions.put(o.getOptionItem().getId(), o));
        lineItem.setMenuItem(menuItem);
        lineItem.setVariant(variant);
        if (!sameItem) {
            lineItem.setItemNameSnapshot(menuItem.getName());
            lineItem.setUnitPriceSnapshot(defaultMoney(menuItem.getBasePrice()));
        }
        if (!sameItem || !sameVariant) {
            lineItem.setVariantPriceDeltaSnapshot(variant == null ? ZERO : defaultSignedMoney(variant.getPriceDelta()));
            lineItem.setVariantNameSnapshot(variant == null ? null : variant.getName());
            lineItem.setSkuSnapshot(variant != null && variant.getSku() != null ? variant.getSku() : menuItem.getSku());
        }
        lineItem.setQuantity(request.getQuantity());
        lineItem.setStatus(lineItem.getStatus() == null ? OrderLineItemStatus.PENDING : lineItem.getStatus());
        lineItem.setNotes(request.getNotes());
        if (replaceOptions) {
            var requested = request.getOptions() == null ? java.util.List.<CreateOrderItemOptionRequest>of() : request.getOptions();
            requireDistinctIds(requested.stream().map(CreateOrderItemOptionRequest::getOptionItemId).toList(), "Duplicate options");
            var requestedIds = requested.stream().map(CreateOrderItemOptionRequest::getOptionItemId).collect(java.util.stream.Collectors.toSet());
            new ArrayList<>(lineItem.getOptions()).stream().filter(o -> !sameItem || !requestedIds.contains(o.getOptionItem().getId())).forEach(lineItem::removeOption);
            for (var selected : requested) {
                OrderItemOption previous = sameItem ? oldOptions.get(selected.getOptionItemId()) : null;
                if (previous == null) lineItem.addOption(buildOption(order, lineItem, selected));
                else {
                    requireOptionItem(order.getRestaurant().getId(), menuItem.getId(), selected.getOptionItemId());
                    previous.setQuantity(selected.getQuantity() == null ? 1 : selected.getQuantity());
                    previous.setNotes(selected.getNotes());
                }
            }
        }
        validateOptionSelection(lineItem);
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
        option.setInventoryRecipeSnapshot(optionItem.getInventoryRecipe());
        option.setInventoryRecipeQuantitySnapshot(optionItem.getInventoryRecipeQuantity());
        option.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        option.setNotes(request.getNotes());
        return option;
    }

    /** Validate the final selection, including per-item overrides; quantities do not bypass distinct-choice limits. */
    public void validateOptionSelection(OrderLineItem line) {
        requireDistinctIds(line.getOptions().stream().map(o -> o.getOptionItem().getId()).toList(), "Duplicate options");
        for (var link : line.getMenuItem().getOptionGroups()) {
            var group = link.getOptionGroup();
            long count = line.getOptions().stream().filter(o -> Objects.equals(o.getOptionItem().getOptionGroup().getId(), group.getId())).count();
            if (!group.isActive()) {
                if (count > 0) throw new AuthException("Option group is inactive", HttpStatus.BAD_REQUEST);
                continue;
            }
            Integer min = link.getMinSelectOverride() != null ? link.getMinSelectOverride() : group.getMinSelect();
            Integer max = link.getMaxSelectOverride() != null ? link.getMaxSelectOverride() : group.getMaxSelect();
            boolean required = link.getRequiredOverride() != null ? link.getRequiredOverride() : group.isRequired();
            int minimum = Math.max(min == null ? 0 : min, required ? 1 : 0);
            if (count < minimum || (max != null && count > max)) {
                throw new AuthException("Check option selections for " + group.getName() + " (minimum " + minimum + ", maximum " + (max == null ? "unlimited" : max) + ")", HttpStatus.BAD_REQUEST);
            }
        }
    }

    public void replaceDiscounts(Order order, List<CreateOrderDiscountRequest> requests, UUID actorId) {
        List<OrderDiscount> existing = new ArrayList<>(order.getDiscounts());
        // Clearing discounts through the order PATCH path must have the same manager-only guard as
        // adding, editing, or deleting them through the dedicated discount endpoints.
        if (!existing.isEmpty() || (requests != null && !requests.isEmpty())) {
            assertCanManageDiscounts(order.getRestaurant());
        }
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
        SettingsOrderRule rules = loadOrderRules(order.getRestaurant());
        if (rules.isRequireReasonForDiscount()
                && NormalizationUtils.normalize(request.getReason()) == null) {
            throw new AuthException("A reason is required for order discounts", HttpStatus.BAD_REQUEST);
        }
        if (request.getDiscountType() == pos.pos.order.enums.OrderDiscountType.PERCENTAGE
                && request.getDiscountValue() != null && request.getDiscountValue().compareTo(ONE_HUNDRED) > 0) {
            throw new AuthException("A percentage discount can be at most 100%", HttpStatus.BAD_REQUEST);
        }
        assertCanManageDiscounts(rules);

        discount.setName(request.getName());
        discount.setDiscountType(request.getDiscountType());
        discount.setDiscountValue(defaultMoney(request.getDiscountValue()));
        discount.setReason(request.getReason());
        discount.setAppliedBy(actorId);
        discount.setAmountApplied(ZERO);
    }

    public void assertCanManageDiscounts(Restaurant restaurant) {
        assertCanManageDiscounts(loadOrderRules(restaurant));
    }

    private void assertCanManageDiscounts(SettingsOrderRule rules) {
        // "Allow discounts without a manager" off: only someone trusted to void (a manager) may manage them.
        if (!rules.isAllowDiscountWithoutManager() && !currentUserHas("ORDER_VOID")) {
            throw new AuthException("A manager has to give discounts at this restaurant", HttpStatus.FORBIDDEN);
        }
    }

    public void refreshLineItemPricing(OrderLineItem lineItem) {
        BigDecimal baseUnitPrice = defaultMoney(lineItem.getUnitPriceSnapshot());
        BigDecimal variantDelta = defaultSignedMoney(lineItem.getVariantPriceDeltaSnapshot());
        BigDecimal optionDelta = lineItem.getOptions().stream()
                .map(option -> defaultSignedMoney(option.getPriceDeltaSnapshot())
                        .multiply(BigDecimal.valueOf(option.getQuantity())))
                .reduce(ZERO, BigDecimal::add);

        BigDecimal priceDeltaTotal = variantDelta.multiply(BigDecimal.valueOf(lineItem.getQuantity())).add(optionDelta.multiply(BigDecimal.valueOf(lineItem.isOptionsPerUnit() ? lineItem.getQuantity() : 1)));
        BigDecimal gross = baseUnitPrice.multiply(BigDecimal.valueOf(lineItem.getQuantity())).add(priceDeltaTotal);

        lineItem.setPriceDeltaTotal(money(priceDeltaTotal));
        lineItem.setDiscountTotal(money(defaultMoney(lineItem.getDiscountTotal())));
        lineItem.setTaxTotal(money(defaultMoney(lineItem.getTaxTotal())));
        lineItem.setLineTotal(money(gross.subtract(lineItem.getDiscountTotal()).add(lineItem.getTaxTotal())));
    }

    public void recalculateTotals(Order order) {
        if (order.getTaxRateSnapshot() == null) {
            var settings = loadSettings(order.getRestaurant());
            order.setTaxRateSnapshot(defaultMoney(settings.getOrderTaxRate()));
            order.setTaxInclusiveSnapshot(settings.isOrderTaxInclusive());
        }
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
                .sorted(Comparator.comparingInt(OrderDiscount::getDiscountSequence))
                .toList()) {
            BigDecimal applied = calculateDiscountAmount(discount, remainingDiscountableBase);
            discount.setAmountApplied(applied);
            discountTotal = discountTotal.add(applied);
            remainingDiscountableBase = maxZero(remainingDiscountableBase.subtract(applied));
        }

        BigDecimal taxTotal = ZERO;
        BigDecimal allocatedDiscount = ZERO;
        BigDecimal remainingGross = subtotal;
        List<OrderLineItem> active = activeLineItems(order);
        for (int index = 0; index < active.size(); index++) {
            OrderLineItem line = active.get(index);
            BigDecimal gross = grossAmount(line);
            BigDecimal lineDiscount = index == active.size() - 1 ? discountTotal.subtract(allocatedDiscount)
                    : proportionalAmount(discountTotal.subtract(allocatedDiscount), gross, remainingGross);
            // Bound allocations to each line; carry any rounding remainder to following lines.
            lineDiscount = lineDiscount.min(gross).min(discountTotal.subtract(allocatedDiscount)).max(ZERO);
            allocatedDiscount = allocatedDiscount.add(lineDiscount);
            remainingGross = remainingGross.subtract(gross);
            BigDecimal taxable = maxZero(gross.subtract(lineDiscount));
            BigDecimal rate = order.getTaxRateSnapshot();
            BigDecimal lineTax = taxable.multiply(rate).divide(order.isTaxInclusiveSnapshot() ? ONE_HUNDRED.add(rate) : ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            line.setDiscountTotal(money(lineDiscount)); line.setTaxTotal(lineTax);
            line.setLineTotal(money(taxable.add(order.isTaxInclusiveSnapshot() ? ZERO : lineTax)));
            taxTotal = taxTotal.add(lineTax);
        }
        BigDecimal discountedSubtotal = maxZero(subtotal.subtract(discountTotal));
        BigDecimal serviceChargeTotal = calculateServiceCharge(order.getRestaurant(), discountedSubtotal);
        BigDecimal total = discountedSubtotal.add(order.isTaxInclusiveSnapshot() ? ZERO : taxTotal).add(serviceChargeTotal);

        order.setSubtotal(money(subtotal));
        order.setDiscountTotal(money(discountTotal));
        order.setTaxTotal(money(taxTotal));
        order.setServiceChargeTotal(money(serviceChargeTotal));
        order.setTotal(money(total));
        refreshPrepaidPaymentStatus(order);
        refreshOrderFulfillment(order);
    }

    // Orders with money paid in advance are PAID while it covers the total and PARTIALLY_PAID once more is ordered.
    // Refunded/voided states and orders without a prepayment are left alone.
    public void refreshPrepaidPaymentStatus(Order order) {
        // With payments taken at the till, the payment status follows what they cover of the new total.
        if (paymentCalculator != null && order.getPayments() != null && !order.getPayments().isEmpty()
                && order.getStatus() != OrderStatus.VOIDED
                && !paymentCalculator.moneyPayments(order).isEmpty()) {
            order.setPaymentStatus(paymentCalculator.paymentStatus(order, loadSettings(order.getRestaurant())));
            return;
        }
        BigDecimal prepaid = defaultMoney(order.getPrepaidTotal());
        if (prepaid.signum() <= 0) {
            return;
        }
        if (!EnumSet.of(OrderPaymentStatus.UNPAID, OrderPaymentStatus.PARTIALLY_PAID, OrderPaymentStatus.PAID)
                .contains(order.getPaymentStatus())) {
            return;
        }
        order.setPaymentStatus(prepaid.compareTo(defaultMoney(order.getTotal())) >= 0
                ? OrderPaymentStatus.PAID
                : OrderPaymentStatus.PARTIALLY_PAID);
    }

    public void refreshOrderFulfillment(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.VOIDED) {
            // Cancellation belongs to OrderStatus; retain the last service progress.
            return;
        }

        List<OrderLineItem> activeLineItems = activeLineItems(order);
        if (activeLineItems.isEmpty()) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.PENDING);
            return;
        }

        boolean allFulfilled = activeLineItems.stream().allMatch(lineItem -> lineItem.getStatus() == OrderLineItemStatus.FULFILLED);
        // Counter items (not sent to the kitchen) are ready to serve as soon as they're ordered.
        boolean allReadyOrFulfilled = activeLineItems.stream().allMatch(lineItem ->
                lineItem.getStatus() == OrderLineItemStatus.READY
                        || lineItem.getStatus() == OrderLineItemStatus.FULFILLED
                        || (lineItem.getStatus() == OrderLineItemStatus.PENDING && !lineItem.goesToKitchen())
        );
        boolean anyFulfilled = activeLineItems.stream().anyMatch(lineItem -> lineItem.getStatus() == OrderLineItemStatus.FULFILLED);
        boolean anyProgress = activeLineItems.stream().anyMatch(lineItem ->
                lineItem.getStatus() == OrderLineItemStatus.FIRED
                        || lineItem.getStatus() == OrderLineItemStatus.PREPARING
                        || lineItem.getStatus() == OrderLineItemStatus.READY
                        || lineItem.getStatus() == OrderLineItemStatus.FULFILLED
        );

        if (allFulfilled) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.FULFILLED);
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

    public BigDecimal splitDiscountAmount(Order order, Collection<OrderLineItem> selected, OrderDiscount discount) {
        BigDecimal source = activeLineItems(order).stream().map(this::grossAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal target = selected.stream().map(this::grossAmount).reduce(ZERO, BigDecimal::add);
        return proportionalAmount(discount.getAmountApplied(), target, source);
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
        clone.setVariantPriceDeltaSnapshot(sourceLineItem.getVariantPriceDeltaSnapshot());
        clone.setOptionsPerUnit(sourceLineItem.isOptionsPerUnit());
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
            optionClone.setInventoryRecipeSnapshot(option.getInventoryRecipeSnapshot());
            optionClone.setInventoryRecipeQuantitySnapshot(option.getInventoryRecipeQuantitySnapshot());
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

    public OrderResponse toSummaryResponse(Order order, int itemCount) {
        return orderMapper.toResponse(order, false, false, itemCount);
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
