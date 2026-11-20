package pos.pos.unit.order.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.customer.entity.Customer;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
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
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.support.AbstractTestProfilePostgresTest;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.enums.TableLocationType;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderEntityPersistenceTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should persist the order graph with normalized snapshots and linked relationships")
    void shouldPersistTheOrderGraphWithNormalizedSnapshotsAndLinkedRelationships() {
        Restaurant restaurant = restaurant();
        entityManager.persist(restaurant);

        Branch branch = branch(restaurant);
        entityManager.persist(branch);

        Customer customer = customer(restaurant);
        entityManager.persist(customer);

        TableCategory category = tableCategory(branch);
        entityManager.persist(category);

        RestaurantTable restaurantTable = restaurantTable(restaurant, branch, category);
        entityManager.persist(restaurantTable);

        Menu menu = menu(restaurant);
        entityManager.persist(menu);

        MenuSection section = menuSection(menu);
        entityManager.persist(section);

        MenuItem menuItem = menuItem(section);
        entityManager.persist(menuItem);

        MenuVariant variant = menuVariant(menuItem);
        entityManager.persist(variant);

        OptionGroupType optionGroupType = optionGroupType();
        entityManager.persist(optionGroupType);

        OptionGroup optionGroup = optionGroup(restaurant, optionGroupType);
        entityManager.persist(optionGroup);

        OptionItem optionItem = optionItem(optionGroup);
        entityManager.persist(optionItem);

        Reservation reservation = reservation(restaurant, branch, customer);
        entityManager.persist(reservation);

        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setRestaurantTable(restaurantTable);
        order.setReservation(reservation);
        order.setCustomer(customer);
        order.setOrderNumber(" ord-1001 ");
        order.setCurrency(" usd ");
        order.setOrderType(OrderType.DINE_IN);
        order.setSource(OrderSource.QR_TABLE);
        order.setStatus(OrderStatus.CLOSED);
        order.setFulfillmentStatus(OrderFulfillmentStatus.FULFILLED);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setGuestCount(2);
        order.setNotes("  table side birthday order  ");
        order.setSubtotal(new BigDecimal("27.00"));
        order.setDiscountTotal(new BigDecimal("2.00"));
        order.setTaxTotal(new BigDecimal("2.36"));
        order.setServiceChargeTotal(new BigDecimal("1.50"));
        order.setTotal(new BigDecimal("28.86"));

        OrderLineItem lineItem = new OrderLineItem();
        lineItem.setMenuItem(menuItem);
        lineItem.setVariant(variant);
        lineItem.setQuantity(2);
        lineItem.setUnitPriceSnapshot(null);
        lineItem.setPriceDeltaTotal(null);
        lineItem.setDiscountTotal(new BigDecimal("1.00"));
        lineItem.setTaxTotal(new BigDecimal("2.36"));
        lineItem.setLineTotal(new BigDecimal("28.86"));
        lineItem.setStatus(OrderLineItemStatus.READY);
        lineItem.setNotes("  no pickle  ");

        OrderItemOption option = new OrderItemOption();
        option.setOptionItem(optionItem);
        option.setQuantity(2);
        option.setNotes("  on half  ");
        lineItem.addOption(option);

        OrderDiscount discount = new OrderDiscount();
        discount.setName("  Happy Hour  ");
        discount.setDiscountType(OrderDiscountType.PERCENTAGE);
        discount.setDiscountValue(new BigDecimal("10.00"));
        discount.setAmountApplied(new BigDecimal("2.00"));
        discount.setReason("  lunch promotion  ");

        OrderEvent event = new OrderEvent();
        event.setEventType(OrderEventType.CREATED);
        event.setNote("  opened from qr order  ");

        order.addLineItem(lineItem);
        order.addDiscount(discount);
        order.addEvent(event);

        entityManager.persist(order);
        entityManager.flush();
        entityManager.clear();

        Order storedOrder = entityManager.find(Order.class, order.getId());
        OrderLineItem storedLineItem = entityManager.find(OrderLineItem.class, lineItem.getId());
        OrderItemOption storedOption = entityManager.find(OrderItemOption.class, option.getId());
        OrderDiscount storedDiscount = entityManager.find(OrderDiscount.class, discount.getId());
        OrderEvent storedEvent = entityManager.find(OrderEvent.class, event.getId());

        assertThat(storedOrder.getOrderNumber()).isEqualTo("ORD-1001");
        assertThat(storedOrder.getCurrency()).isEqualTo("USD");
        assertThat(storedOrder.getNotes()).isEqualTo("table side birthday order");
        assertThat(storedOrder.getOpenedAt()).isNotNull();
        assertThat(storedOrder.getClosedAt()).isNotNull();
        assertThat(storedOrder.getCreatedAt()).isNotNull();
        assertThat(storedOrder.getUpdatedAt()).isNotNull();

        assertThat(storedLineItem.getItemNameSnapshot()).isEqualTo("House Burger");
        assertThat(storedLineItem.getVariantNameSnapshot()).isEqualTo("Large");
        assertThat(storedLineItem.getSkuSnapshot()).isEqualTo("BRG-001-L");
        assertThat(storedLineItem.getUnitPriceSnapshot()).isEqualByComparingTo("12.50");
        assertThat(storedLineItem.getPriceDeltaTotal()).isEqualByComparingTo("2.00");
        assertThat(storedLineItem.getNotes()).isEqualTo("no pickle");
        assertThat(storedLineItem.getCreatedAt()).isNotNull();

        assertThat(storedOption.getOptionNameSnapshot()).isEqualTo("Extra Cheese");
        assertThat(storedOption.getPriceDeltaSnapshot()).isEqualByComparingTo("1.50");
        assertThat(storedOption.getNotes()).isEqualTo("on half");
        assertThat(storedOption.getCreatedAt()).isNotNull();

        assertThat(storedDiscount.getName()).isEqualTo("Happy Hour");
        assertThat(storedDiscount.getReason()).isEqualTo("lunch promotion");
        assertThat(storedDiscount.getCreatedAt()).isNotNull();
        assertThat(storedDiscount.getUpdatedAt()).isNotNull();

        assertThat(storedEvent.getNote()).isEqualTo("opened from qr order");
        assertThat(storedEvent.getCreatedAt()).isNotNull();
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(" Riverside Grill ");
        restaurant.setLegalName(" Riverside Grill LLC ");
        restaurant.setCode(" riverside_grill ");
        restaurant.setSlug(" riverside-grill ");
        restaurant.setCurrency(" usd ");
        restaurant.setTimezone(" Europe/Berlin ");
        restaurant.setStatus(RestaurantStatus.PENDING);
        return restaurant;
    }

    private Branch branch(Restaurant restaurant) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName(" Main Branch ");
        branch.setCode(" main ");
        branch.setStatus(BranchStatus.ACTIVE);
        return branch;
    }

    private Customer customer(Restaurant restaurant) {
        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setCode(" guest-001 ");
        customer.setFirstName(" Alice ");
        customer.setLastName(" Example ");
        customer.setEmail(" ALICE@EXAMPLE.TEST ");
        customer.setPhone(" +49 151 234 5678 ");
        return customer;
    }

    private TableCategory tableCategory(Branch branch) {
        TableCategory category = new TableCategory();
        category.setBranch(branch);
        category.setCode(" main dining ");
        category.setName(" Main Dining ");
        category.setDescription(" Prime room ");
        category.setDefaultCapacity(4);
        category.setLocationType(TableLocationType.INDOOR);
        category.setColor(" #aa33cc ");
        return category;
    }

    private RestaurantTable restaurantTable(Restaurant restaurant, Branch branch, TableCategory category) {
        RestaurantTable restaurantTable = new RestaurantTable();
        restaurantTable.setRestaurant(restaurant);
        restaurantTable.setBranch(branch);
        restaurantTable.setCategory(category);
        restaurantTable.setTableNumber(" a-01 ");
        restaurantTable.setName(" Window Two ");
        restaurantTable.setCapacity(4);
        restaurantTable.setFloor(" Main Floor ");
        restaurantTable.setPositionX(new BigDecimal("120.00"));
        restaurantTable.setPositionY(new BigDecimal("80.00"));
        restaurantTable.setShape(TableShape.ROUND);
        restaurantTable.setStatus(TableStatus.AVAILABLE);
        return restaurantTable;
    }

    private Menu menu(Restaurant restaurant) {
        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(" lunch specials ");
        menu.setName(" Lunch Specials ");
        return menu;
    }

    private MenuSection menuSection(Menu menu) {
        MenuSection section = new MenuSection();
        section.setMenu(menu);
        section.setName(" Burgers ");
        return section;
    }

    private MenuItem menuItem(MenuSection section) {
        MenuItem menuItem = new MenuItem();
        menuItem.setSection(section);
        menuItem.setSku(" brg-001 ");
        menuItem.setName(" House Burger ");
        menuItem.setBasePrice(new BigDecimal("12.50"));
        return menuItem;
    }

    private MenuVariant menuVariant(MenuItem menuItem) {
        MenuVariant variant = new MenuVariant();
        variant.setMenuItem(menuItem);
        variant.setName(" Large ");
        variant.setSku(" brg-001-l ");
        variant.setPriceDelta(new BigDecimal("2.00"));
        return variant;
    }

    private OptionGroupType optionGroupType() {
        OptionGroupType optionGroupType = new OptionGroupType();
        optionGroupType.setName(" Add Ons ");
        return optionGroupType;
    }

    private OptionGroup optionGroup(Restaurant restaurant, OptionGroupType optionGroupType) {
        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRestaurant(restaurant);
        optionGroup.setType(optionGroupType);
        optionGroup.setName(" Toppings ");
        optionGroup.setMinSelect(0);
        optionGroup.setMaxSelect(3);
        return optionGroup;
    }

    private OptionItem optionItem(OptionGroup optionGroup) {
        OptionItem optionItem = new OptionItem();
        optionItem.setOptionGroup(optionGroup);
        optionItem.setCode(" extra cheese ");
        optionItem.setName(" Extra Cheese ");
        optionItem.setPriceDelta(new BigDecimal("1.50"));
        return optionItem;
    }

    private Reservation reservation(Restaurant restaurant, Branch branch, Customer customer) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1).withSecond(0).withNano(0);

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setReservationCode(" rsv-order-01 ");
        reservation.setSource(ReservationSource.INTERNAL);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPartySize(2);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusMinutes(90));
        reservation.setContactName(" Alice Example ");
        reservation.setDepositRequired(false);
        reservation.setDepositStatus(ReservationDepositStatus.NOT_REQUIRED);
        reservation.setConfirmedAt(start.minusHours(2));
        return reservation;
    }
}
