package pos.pos.unit.preorder.service;

import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.entity.PreOrderItem;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Small builders shared by the pre-order tests.
final class PreOrderFixtures {

    static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000009001");
    static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000009002");
    static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000009003");

    private PreOrderFixtures() {
    }

    static Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setCurrency("EUR");
        restaurant.setTimezone("Europe/Rome");
        return restaurant;
    }

    static Reservation reservation(ReservationStatus status, OffsetDateTime start) {
        Restaurant restaurant = restaurant();
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        Reservation reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setReservationCode("RSV-1");
        reservation.setContactName("Anna Rossi");
        reservation.setPartySize(4);
        reservation.setStatus(status);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusHours(2));
        return reservation;
    }

    static OffsetDateTime inHours(long hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours);
    }

    static MenuItem menuItem(String name, String price, boolean sendToKitchen) {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setRestaurant(restaurant());
        MenuSection section = new MenuSection();
        section.setId(UUID.randomUUID());
        section.setMenu(menu);
        MenuItem item = new MenuItem();
        item.setId(UUID.randomUUID());
        item.setName(name);
        item.setBasePrice(new BigDecimal(price));
        item.setSection(section);
        item.setSendToKitchen(sendToKitchen);
        return item;
    }

    static PreOrder preOrder(Reservation reservation, PreOrderStatus status, int leadMinutes) {
        PreOrder preOrder = new PreOrder();
        preOrder.setId(UUID.randomUUID());
        preOrder.setRestaurant(reservation.getRestaurant());
        preOrder.setReservation(reservation);
        preOrder.setStatus(status);
        preOrder.setLeadMinutes(leadMinutes);
        preOrder.setCurrency("EUR");
        preOrder.setTotal(new BigDecimal("30.00"));
        preOrder.setPaidAmount(new BigDecimal("30.00"));
        return preOrder;
    }

    static PreOrderItem item(MenuItem menuItem, int quantity, String unitPrice) {
        PreOrderItem item = new PreOrderItem();
        item.setMenuItem(menuItem);
        item.setItemNameSnapshot(menuItem.getName());
        item.setQuantity(quantity);
        item.setUnitPriceSnapshot(new BigDecimal(unitPrice));
        return item;
    }
}
