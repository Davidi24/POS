package pos.pos.unit.operations.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.enums.AuditSeverity;
import pos.pos.audit.enums.AuditSource;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.enums.DeviceType;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.entity.InventoryCountLine;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLevel;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.inventory.enums.InventoryCountStatus;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryLocationType;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.entity.NotificationPreference;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationStatus;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.entity.PaymentTransaction;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.enums.PaymentTransactionStatus;
import pos.pos.payment.enums.PaymentTransactionType;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.report.entity.ReportDefinition;
import pos.pos.report.entity.ReportExecution;
import pos.pos.report.enums.ReportExecutionStatus;
import pos.pos.report.enums.ReportFormat;
import pos.pos.report.enums.ReportFrequency;
import pos.pos.report.enums.ReportType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.entity.ShiftBreak;
import pos.pos.shift.enums.ShiftBreakType;
import pos.pos.shift.enums.ShiftStatus;
import pos.pos.support.AbstractTestProfilePostgresTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OperationalDomainEntityPersistenceTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should persist connected operational domains with normalized values")
    void shouldPersistConnectedOperationalDomainsWithNormalizedValues() {
        Restaurant restaurant = restaurant();
        entityManager.persist(restaurant);

        Branch branch = branch(restaurant);
        entityManager.persist(branch);

        User user = user(restaurant, branch);
        entityManager.persist(user);

        Device device = device(restaurant, branch);
        entityManager.persist(device);

        Menu menu = menu(restaurant);
        entityManager.persist(menu);

        MenuSection section = menuSection(menu);
        entityManager.persist(section);

        MenuItem menuItem = menuItem(section);
        entityManager.persist(menuItem);

        InventoryItem inventoryItem = inventoryItem(restaurant);
        entityManager.persist(inventoryItem);

        InventoryLocation inventoryLocation = inventoryLocation(restaurant, branch);
        entityManager.persist(inventoryLocation);

        InventoryLevel inventoryLevel = inventoryLevel(inventoryLocation, inventoryItem);
        entityManager.persist(inventoryLevel);

        Recipe recipe = recipe(restaurant, menuItem);
        RecipeComponent recipeComponent = recipeComponent(recipe, inventoryItem);
        recipe.addComponent(recipeComponent);
        entityManager.persist(recipe);

        Order order = order(restaurant, branch);
        OrderLineItem orderLineItem = orderLineItem(menuItem);
        order.addLineItem(orderLineItem);
        entityManager.persist(order);

        InventoryCount inventoryCount = inventoryCount(restaurant, branch, inventoryLocation);
        InventoryCountLine inventoryCountLine = inventoryCountLine(inventoryItem);
        inventoryCount.addLine(inventoryCountLine);
        entityManager.persist(inventoryCount);

        InventoryMovement inventoryMovement = inventoryMovement(inventoryLocation, inventoryItem, orderLineItem);
        entityManager.persist(inventoryMovement);

        Shift shift = shift(restaurant, branch, user, device);
        ShiftBreak shiftBreak = shiftBreak();
        shift.addBreak(shiftBreak);
        entityManager.persist(shift);

        KdsStation kdsStation = kdsStation(restaurant, branch, device);
        entityManager.persist(kdsStation);

        KdsStationRouting kdsStationRouting = kdsStationRouting(kdsStation, menuItem);
        entityManager.persist(kdsStationRouting);

        KdsTicket kdsTicket = kdsTicket(restaurant, branch, kdsStation, order);
        KdsTicketItem kdsTicketItem = kdsTicketItem(orderLineItem);
        kdsTicket.addItem(kdsTicketItem);
        entityManager.persist(kdsTicket);

        Payment payment = payment(restaurant, branch, order, shift);
        PaymentTransaction paymentTransaction = paymentTransaction();
        payment.addTransaction(paymentTransaction);
        entityManager.persist(payment);

        ReportDefinition reportDefinition = reportDefinition(restaurant, branch);
        entityManager.persist(reportDefinition);

        ReportExecution reportExecution = reportExecution(reportDefinition, user);
        entityManager.persist(reportExecution);

        NotificationTemplate notificationTemplate = notificationTemplate(restaurant);
        entityManager.persist(notificationTemplate);

        NotificationPreference notificationPreference = notificationPreference(user);
        entityManager.persist(notificationPreference);

        Notification notification = notification(restaurant, branch, notificationTemplate, user);
        entityManager.persist(notification);

        AuditLog auditLog = auditLog(restaurant, branch, user);
        entityManager.persist(auditLog);

        entityManager.flush();
        entityManager.clear();

        InventoryItem storedInventoryItem = entityManager.find(InventoryItem.class, inventoryItem.getId());
        InventoryLevel storedInventoryLevel = entityManager.find(InventoryLevel.class, inventoryLevel.getId());
        Recipe storedRecipe = entityManager.find(Recipe.class, recipe.getId());
        RecipeComponent storedRecipeComponent = entityManager.find(RecipeComponent.class, recipeComponent.getId());
        InventoryCountLine storedInventoryCountLine = entityManager.find(InventoryCountLine.class, inventoryCountLine.getId());
        InventoryMovement storedInventoryMovement = entityManager.find(InventoryMovement.class, inventoryMovement.getId());
        Shift storedShift = entityManager.find(Shift.class, shift.getId());
        ShiftBreak storedShiftBreak = entityManager.find(ShiftBreak.class, shiftBreak.getId());
        KdsStation storedKdsStation = entityManager.find(KdsStation.class, kdsStation.getId());
        KdsStationRouting storedKdsStationRouting = entityManager.find(KdsStationRouting.class, kdsStationRouting.getId());
        KdsTicket storedKdsTicket = entityManager.find(KdsTicket.class, kdsTicket.getId());
        KdsTicketItem storedKdsTicketItem = entityManager.find(KdsTicketItem.class, kdsTicketItem.getId());
        Payment storedPayment = entityManager.find(Payment.class, payment.getId());
        PaymentTransaction storedPaymentTransaction = entityManager.find(PaymentTransaction.class, paymentTransaction.getId());
        ReportDefinition storedReportDefinition = entityManager.find(ReportDefinition.class, reportDefinition.getId());
        ReportExecution storedReportExecution = entityManager.find(ReportExecution.class, reportExecution.getId());
        NotificationTemplate storedNotificationTemplate = entityManager.find(NotificationTemplate.class, notificationTemplate.getId());
        NotificationPreference storedNotificationPreference = entityManager.find(NotificationPreference.class, notificationPreference.getId());
        Notification storedNotification = entityManager.find(Notification.class, notification.getId());
        AuditLog storedAuditLog = entityManager.find(AuditLog.class, auditLog.getId());

        assertThat(storedInventoryItem.getCode()).isEqualTo("TOMATO_SAUCE");
        assertThat(storedInventoryItem.getSupplierSku()).isEqualTo("SUP-442");
        assertThat(storedInventoryLevel.getCommittedQuantity()).isEqualByComparingTo("2.500");

        assertThat(storedRecipe.getCode()).isEqualTo("MARGHERITA_PIZZA");
        assertThat(storedRecipe.getYieldUnit()).isEqualTo(InventoryUnit.PORTION);
        assertThat(storedRecipeComponent.getComponentNameSnapshot()).isEqualTo("Tomato Sauce");

        assertThat(storedInventoryCountLine.getItemNameSnapshot()).isEqualTo("Tomato Sauce");
        assertThat(storedInventoryCountLine.getVarianceQuantity()).isEqualByComparingTo("-1.000");
        assertThat(storedInventoryCountLine.getVarianceValue()).isEqualByComparingTo("2.20");
        assertThat(storedInventoryMovement.getReferenceType()).isEqualTo("ORDER_FIRE");

        assertThat(storedShift.getStartedAt()).isNotNull();
        assertThat(storedShift.getDeclaredCashTips()).isEqualByComparingTo("15.00");
        assertThat(storedShiftBreak.getNotes()).isEqualTo("team break");

        assertThat(storedKdsStation.getCode()).isEqualTo("HOT_LINE");
        assertThat(storedKdsStationRouting.getCourseLabel()).isEqualTo("Main Course");
        assertThat(storedKdsTicket.getTicketNumber()).isEqualTo("KDS-1001");
        assertThat(storedKdsTicketItem.getItemNameSnapshot()).isEqualTo("Margherita Pizza");

        assertThat(storedPayment.getCurrency()).isEqualTo("USD");
        assertThat(storedPayment.getReferenceNumber()).isEqualTo("PMT-1001");
        assertThat(storedPaymentTransaction.getCurrency()).isEqualTo("USD");
        assertThat(storedPaymentTransaction.getResponseCode()).isEqualTo("APPROVED");

        assertThat(storedReportDefinition.getCode()).isEqualTo("DAILY_CLOSE");
        assertThat(storedReportDefinition.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(storedReportExecution.getStatus()).isEqualTo(ReportExecutionStatus.COMPLETED);

        assertThat(storedNotificationTemplate.getCode()).isEqualTo("ORDER_READY");
        assertThat(storedNotificationPreference.getEventCode()).isEqualTo("ORDER_READY");
        assertThat(storedNotification.getEventCode()).isEqualTo("ORDER_READY");
        assertThat(storedNotification.getReadAt()).isNotNull();

        assertThat(storedAuditLog.getEntityType()).isEqualTo("PAYMENT");
        assertThat(storedAuditLog.getAction()).isEqualTo("CAPTURED");
        assertThat(storedAuditLog.getCreatedAt()).isNotNull();
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(" Margherita House ");
        restaurant.setLegalName(" Margherita House GmbH ");
        restaurant.setCode(" margherita_house ");
        restaurant.setSlug(" margherita-house ");
        restaurant.setCurrency(" usd ");
        restaurant.setTimezone(" Europe/Berlin ");
        restaurant.setStatus(RestaurantStatus.PENDING);
        return restaurant;
    }

    private Branch branch(Restaurant restaurant) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName(" Mitte Branch ");
        branch.setCode(" mitte ");
        branch.setStatus(BranchStatus.ACTIVE);
        return branch;
    }

    private User user(Restaurant restaurant, Branch branch) {
        User user = new User();
        user.setRestaurantId(restaurant.getId());
        user.setDefaultBranchId(branch.getId());
        user.setEmail(" manager@example.test ");
        user.setUsername(" manager.one ");
        user.setPasswordHash(" hash ");
        user.setFirstName(" Mina ");
        user.setLastName(" Manager ");
        user.setStatus(" active ");
        return user;
    }

    private Device device(Restaurant restaurant, Branch branch) {
        Device device = new Device();
        device.setRestaurant(restaurant);
        device.setBranch(branch);
        device.setCode(" hot line ");
        device.setName(" Hot Line Screen ");
        device.setDeviceType(DeviceType.KDS);
        device.setStatus(DeviceStatus.ACTIVE);
        return device;
    }

    private Menu menu(Restaurant restaurant) {
        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(" dinner ");
        menu.setName(" Dinner ");
        return menu;
    }

    private MenuSection menuSection(Menu menu) {
        MenuSection section = new MenuSection();
        section.setMenu(menu);
        section.setName(" Pizza ");
        return section;
    }

    private MenuItem menuItem(MenuSection section) {
        MenuItem menuItem = new MenuItem();
        menuItem.setSection(section);
        menuItem.setSku(" pizza-001 ");
        menuItem.setName(" Margherita Pizza ");
        menuItem.setBasePrice(new BigDecimal("12.50"));
        return menuItem;
    }

    private InventoryItem inventoryItem(Restaurant restaurant) {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setRestaurant(restaurant);
        inventoryItem.setCode(" tomato sauce ");
        inventoryItem.setName(" Tomato Sauce ");
        inventoryItem.setItemType(InventoryItemType.INGREDIENT);
        inventoryItem.setBaseUnit(InventoryUnit.KILOGRAM);
        inventoryItem.setSupplierSku(" sup-442 ");
        inventoryItem.setCostPerUnit(new BigDecimal("2.20"));
        inventoryItem.setParLevel(new BigDecimal("10.000"));
        inventoryItem.setReorderPoint(new BigDecimal("5.000"));
        return inventoryItem;
    }

    private InventoryLocation inventoryLocation(Restaurant restaurant, Branch branch) {
        InventoryLocation inventoryLocation = new InventoryLocation();
        inventoryLocation.setRestaurant(restaurant);
        inventoryLocation.setBranch(branch);
        inventoryLocation.setCode(" kitchen ");
        inventoryLocation.setName(" Main Kitchen ");
        inventoryLocation.setLocationType(InventoryLocationType.KITCHEN);
        return inventoryLocation;
    }

    private InventoryLevel inventoryLevel(InventoryLocation inventoryLocation, InventoryItem inventoryItem) {
        InventoryLevel inventoryLevel = new InventoryLevel();
        inventoryLevel.setLocation(inventoryLocation);
        inventoryLevel.setInventoryItem(inventoryItem);
        inventoryLevel.setOnHandQuantity(new BigDecimal("24.500"));
        inventoryLevel.setCommittedQuantity(new BigDecimal("2.500"));
        inventoryLevel.setParQuantity(new BigDecimal("10.000"));
        inventoryLevel.setReorderQuantity(new BigDecimal("5.000"));
        return inventoryLevel;
    }

    private Recipe recipe(Restaurant restaurant, MenuItem menuItem) {
        Recipe recipe = new Recipe();
        recipe.setRestaurant(restaurant);
        recipe.setMenuItem(menuItem);
        recipe.setCode(" margherita pizza ");
        recipe.setName(" Margherita Pizza ");
        recipe.setRecipeType(RecipeType.FINISHED_DISH);
        recipe.setStatus(RecipeStatus.ACTIVE);
        recipe.setYieldQuantity(new BigDecimal("1.000"));
        recipe.setYieldUnit(InventoryUnit.PORTION);
        recipe.setPrepTimeMinutes(8);
        recipe.setCookTimeMinutes(6);
        recipe.setTheoreticalCost(new BigDecimal("4.80"));
        return recipe;
    }

    private RecipeComponent recipeComponent(Recipe recipe, InventoryItem inventoryItem) {
        RecipeComponent recipeComponent = new RecipeComponent();
        recipeComponent.setRecipe(recipe);
        recipeComponent.setInventoryItem(inventoryItem);
        recipeComponent.setComponentType(RecipeComponentType.INVENTORY_ITEM);
        recipeComponent.setQuantity(new BigDecimal("0.300"));
        recipeComponent.setUnit(InventoryUnit.KILOGRAM);
        recipeComponent.setYieldLossPercent(new BigDecimal("0.00"));
        recipeComponent.setDisplayOrder(1);
        return recipeComponent;
    }

    private Order order(Restaurant restaurant, Branch branch) {
        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setOrderNumber(" ord-1001 ");
        order.setCurrency(" usd ");
        order.setOrderType(OrderType.DINE_IN);
        order.setSource(OrderSource.POS);
        order.setStatus(OrderStatus.OPEN);
        order.setFulfillmentStatus(OrderFulfillmentStatus.PENDING);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setGuestCount(2);
        order.setSubtotal(new BigDecimal("12.50"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setTaxTotal(new BigDecimal("1.00"));
        order.setServiceChargeTotal(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("13.50"));
        return order;
    }

    private OrderLineItem orderLineItem(MenuItem menuItem) {
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setMenuItem(menuItem);
        orderLineItem.setQuantity(1);
        orderLineItem.setUnitPriceSnapshot(new BigDecimal("12.50"));
        orderLineItem.setDiscountTotal(BigDecimal.ZERO);
        orderLineItem.setTaxTotal(new BigDecimal("1.00"));
        orderLineItem.setLineTotal(new BigDecimal("13.50"));
        orderLineItem.setStatus(OrderLineItemStatus.READY);
        return orderLineItem;
    }

    private InventoryCount inventoryCount(Restaurant restaurant, Branch branch, InventoryLocation inventoryLocation) {
        InventoryCount inventoryCount = new InventoryCount();
        inventoryCount.setRestaurant(restaurant);
        inventoryCount.setBranch(branch);
        inventoryCount.setLocation(inventoryLocation);
        inventoryCount.setCountNumber(" cnt-001 ");
        inventoryCount.setStatus(InventoryCountStatus.COMPLETED);
        inventoryCount.setVarianceValue(BigDecimal.ZERO);
        return inventoryCount;
    }

    private InventoryCountLine inventoryCountLine(InventoryItem inventoryItem) {
        InventoryCountLine inventoryCountLine = new InventoryCountLine();
        inventoryCountLine.setInventoryItem(inventoryItem);
        inventoryCountLine.setExpectedQuantity(new BigDecimal("12.000"));
        inventoryCountLine.setCountedQuantity(new BigDecimal("11.000"));
        inventoryCountLine.setUnit(InventoryUnit.KILOGRAM);
        return inventoryCountLine;
    }

    private InventoryMovement inventoryMovement(InventoryLocation inventoryLocation, InventoryItem inventoryItem, OrderLineItem orderLineItem) {
        InventoryMovement inventoryMovement = new InventoryMovement();
        inventoryMovement.setLocation(inventoryLocation);
        inventoryMovement.setInventoryItem(inventoryItem);
        inventoryMovement.setOrderLineItem(orderLineItem);
        inventoryMovement.setMovementType(InventoryMovementType.SALE_CONSUMPTION);
        inventoryMovement.setQuantityDelta(new BigDecimal("-0.300"));
        inventoryMovement.setUnit(InventoryUnit.KILOGRAM);
        inventoryMovement.setUnitCostSnapshot(new BigDecimal("2.20"));
        inventoryMovement.setReferenceType(" order fire ");
        return inventoryMovement;
    }

    private Shift shift(Restaurant restaurant, Branch branch, User user, Device device) {
        Shift shift = new Shift();
        shift.setRestaurant(restaurant);
        shift.setBranch(branch);
        shift.setUser(user);
        shift.setDevice(device);
        shift.setStatus(ShiftStatus.OPEN);
        shift.setDeclaredCashTips(new BigDecimal("15.00"));
        shift.setDeclaredCardTips(new BigDecimal("10.00"));
        shift.setSalesTotal(new BigDecimal("150.00"));
        shift.setCashSalesTotal(new BigDecimal("30.00"));
        shift.setCardSalesTotal(new BigDecimal("120.00"));
        shift.setOpeningDrawerAmount(new BigDecimal("100.00"));
        shift.setExpectedDrawerAmount(new BigDecimal("130.00"));
        return shift;
    }

    private ShiftBreak shiftBreak() {
        ShiftBreak shiftBreak = new ShiftBreak();
        shiftBreak.setBreakType(ShiftBreakType.REST);
        shiftBreak.setPaid(true);
        shiftBreak.setStartedAt(OffsetDateTime.now().minusMinutes(20));
        shiftBreak.setEndedAt(OffsetDateTime.now().minusMinutes(10));
        shiftBreak.setNotes(" team break ");
        return shiftBreak;
    }

    private KdsStation kdsStation(Restaurant restaurant, Branch branch, Device device) {
        KdsStation kdsStation = new KdsStation();
        kdsStation.setRestaurant(restaurant);
        kdsStation.setBranch(branch);
        kdsStation.setDevice(device);
        kdsStation.setCode(" hot line ");
        kdsStation.setName(" Hot Line ");
        kdsStation.setStationType(KdsStationType.GRILL);
        return kdsStation;
    }

    private KdsStationRouting kdsStationRouting(KdsStation kdsStation, MenuItem menuItem) {
        KdsStationRouting kdsStationRouting = new KdsStationRouting();
        kdsStationRouting.setStation(kdsStation);
        kdsStationRouting.setMenuItem(menuItem);
        kdsStationRouting.setPriority(KdsPriority.RUSH);
        kdsStationRouting.setCourseLabel(" Main Course ");
        return kdsStationRouting;
    }

    private KdsTicket kdsTicket(Restaurant restaurant, Branch branch, KdsStation kdsStation, Order order) {
        KdsTicket kdsTicket = new KdsTicket();
        kdsTicket.setRestaurant(restaurant);
        kdsTicket.setBranch(branch);
        kdsTicket.setStation(kdsStation);
        kdsTicket.setOrder(order);
        kdsTicket.setTicketNumber(" kds-1001 ");
        kdsTicket.setStatus(KdsTicketStatus.FIRED);
        kdsTicket.setPriority(KdsPriority.RUSH);
        kdsTicket.setFiredAt(OffsetDateTime.now());
        return kdsTicket;
    }

    private KdsTicketItem kdsTicketItem(OrderLineItem orderLineItem) {
        KdsTicketItem kdsTicketItem = new KdsTicketItem();
        kdsTicketItem.setOrderLineItem(orderLineItem);
        kdsTicketItem.setStatus(KdsTicketStatus.IN_PROGRESS);
        kdsTicketItem.setPriority(KdsPriority.RUSH);
        return kdsTicketItem;
    }

    private Payment payment(Restaurant restaurant, Branch branch, Order order, Shift shift) {
        Payment payment = new Payment();
        payment.setRestaurant(restaurant);
        payment.setBranch(branch);
        payment.setOrder(order);
        payment.setShift(shift);
        payment.setReferenceNumber(" pmt-1001 ");
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAmount(new BigDecimal("13.50"));
        payment.setCurrency(null);
        return payment;
    }

    private PaymentTransaction paymentTransaction() {
        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setTransactionType(PaymentTransactionType.SALE);
        paymentTransaction.setStatus(PaymentTransactionStatus.APPROVED);
        paymentTransaction.setAmount(new BigDecimal("13.50"));
        paymentTransaction.setResponseCode(" approved ");
        return paymentTransaction;
    }

    private ReportDefinition reportDefinition(Restaurant restaurant, Branch branch) {
        ReportDefinition reportDefinition = new ReportDefinition();
        reportDefinition.setRestaurant(restaurant);
        reportDefinition.setBranch(branch);
        reportDefinition.setCode(" daily close ");
        reportDefinition.setName(" Daily Close ");
        reportDefinition.setReportType(ReportType.SALES_SUMMARY);
        reportDefinition.setFrequency(ReportFrequency.DAILY);
        reportDefinition.setFormat(ReportFormat.PDF);
        reportDefinition.setScheduleExpression(" 0 0 2 * * * ");
        return reportDefinition;
    }

    private ReportExecution reportExecution(ReportDefinition reportDefinition, User user) {
        ReportExecution reportExecution = new ReportExecution();
        reportExecution.setReportDefinition(reportDefinition);
        reportExecution.setRequestedByUser(user);
        reportExecution.setStatus(ReportExecutionStatus.COMPLETED);
        reportExecution.setStartedAt(OffsetDateTime.now().minusMinutes(2));
        reportExecution.setCompletedAt(OffsetDateTime.now().minusMinutes(1));
        reportExecution.setRowCount(42);
        return reportExecution;
    }

    private NotificationTemplate notificationTemplate(Restaurant restaurant) {
        NotificationTemplate notificationTemplate = new NotificationTemplate();
        notificationTemplate.setRestaurant(restaurant);
        notificationTemplate.setCode(" order ready ");
        notificationTemplate.setName(" Order Ready ");
        notificationTemplate.setChannel(NotificationChannel.PUSH);
        notificationTemplate.setSubjectTemplate(" Order ready ");
        notificationTemplate.setBodyTemplate(" Table {{table}} is ready ");
        return notificationTemplate;
    }

    private NotificationPreference notificationPreference(User user) {
        NotificationPreference notificationPreference = new NotificationPreference();
        notificationPreference.setUser(user);
        notificationPreference.setChannel(NotificationChannel.PUSH);
        notificationPreference.setEventCode(" order ready ");
        return notificationPreference;
    }

    private Notification notification(Restaurant restaurant, Branch branch, NotificationTemplate notificationTemplate, User user) {
        Notification notification = new Notification();
        notification.setRestaurant(restaurant);
        notification.setBranch(branch);
        notification.setTemplate(notificationTemplate);
        notification.setRecipientUser(user);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setStatus(NotificationStatus.READ);
        notification.setPriority(NotificationPriority.HIGH);
        notification.setEventCode(" order ready ");
        notification.setSubject(" Order Ready ");
        notification.setBody(" Pickup at the pass ");
        notification.setSentAt(OffsetDateTime.now().minusMinutes(5));
        notification.setDeliveredAt(OffsetDateTime.now().minusMinutes(4));
        notification.setReadAt(OffsetDateTime.now().minusMinutes(3));
        return notification;
    }

    private AuditLog auditLog(Restaurant restaurant, Branch branch, User user) {
        AuditLog auditLog = new AuditLog();
        auditLog.setRestaurant(restaurant);
        auditLog.setBranch(branch);
        auditLog.setActorUser(user);
        auditLog.setSource(AuditSource.POS);
        auditLog.setSeverity(AuditSeverity.INFO);
        auditLog.setEntityType(" payment ");
        auditLog.setAction(" captured ");
        auditLog.setSummary(" Card payment captured ");
        return auditLog;
    }
}
