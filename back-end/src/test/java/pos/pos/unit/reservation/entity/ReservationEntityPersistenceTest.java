package pos.pos.unit.reservation.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.customer.entity.Customer;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationStatusHistory;
import pos.pos.reservation.entity.ReservationTableAssignment;
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
class ReservationEntityPersistenceTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should persist reservation and table entities with normalized values and linked relationships")
    void shouldPersistReservationAndTableEntitiesWithNormalizedValuesAndLinkedRelationships() {
        Restaurant restaurant = restaurant();
        entityManager.persist(restaurant);

        Branch branch = branch(restaurant);
        entityManager.persist(branch);

        Customer customer = customer(restaurant);
        entityManager.persist(customer);

        TableCategory category = new TableCategory();
        category.setBranch(branch);
        category.setCode(" main dining ");
        category.setName(" Main Dining ");
        category.setDescription(" Prime room ");
        category.setDefaultCapacity(4);
        category.setLocationType(TableLocationType.INDOOR);
        category.setColor(" #aa33cc ");
        category.setDisplayOrder(2);
        entityManager.persist(category);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setBranch(branch);
        table.setCategory(category);
        table.setTableNumber(" a-01 ");
        table.setName(" Window Two ");
        table.setCapacity(4);
        table.setFloor(" first floor ");
        table.setPositionX(new BigDecimal("12.50"));
        table.setPositionY(new BigDecimal("8.75"));
        table.setShape(TableShape.ROUND);
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrCodeValue(" https://pos.example.test/tables/a-01 ");
        entityManager.persist(table);

        OffsetDateTime start = OffsetDateTime.now().plusDays(1).withSecond(0).withNano(0);
        OffsetDateTime confirmedAt = OffsetDateTime.now().withSecond(0).withNano(0);

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setReservationCode(" rsv-001 ");
        reservation.setSource(ReservationSource.PHONE);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPartySize(4);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusMinutes(90));
        reservation.setContactName("  Alice Example  ");
        reservation.setContactPhone(" +49 151 234 5678 ");
        reservation.setContactEmail(" ALICE@EXAMPLE.TEST ");
        reservation.setSeatingPreference(" window ");
        reservation.setSpecialRequests(" birthday candles ");
        reservation.setInternalNotes(" vip repeat guest ");
        reservation.setDepositRequired(true);
        reservation.setDepositAmount(new BigDecimal("25.00"));
        reservation.setDepositStatus(ReservationDepositStatus.PENDING);
        reservation.setConfirmedAt(confirmedAt);

        ReservationStatusHistory history = new ReservationStatusHistory();
        history.setOldStatus(ReservationStatus.PENDING);
        history.setNewStatus(ReservationStatus.CONFIRMED);
        history.setReason(" phone confirmation ");
        reservation.addStatusHistory(history);

        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        assignment.setPrimaryAssignment(true);
        reservation.addTableAssignment(assignment);

        entityManager.persist(reservation);
        entityManager.flush();
        entityManager.clear();

        Customer storedCustomer = entityManager.find(Customer.class, customer.getId());
        TableCategory storedCategory = entityManager.find(TableCategory.class, category.getId());
        RestaurantTable storedTable = entityManager.find(RestaurantTable.class, table.getId());
        Reservation storedReservation = entityManager.find(Reservation.class, reservation.getId());
        ReservationStatusHistory storedHistory = entityManager.find(ReservationStatusHistory.class, history.getId());
        ReservationTableAssignment storedAssignment = entityManager.find(ReservationTableAssignment.class, assignment.getId());

        assertThat(storedCustomer.getEmail()).isEqualTo("alice@example.test");
        assertThat(storedCustomer.getPhone()).isEqualTo("+491512345678");

        assertThat(storedCategory.getCode()).isEqualTo("MAIN_DINING");
        assertThat(storedCategory.getName()).isEqualTo("Main Dining");
        assertThat(storedCategory.getColor()).isEqualTo("#AA33CC");

        assertThat(storedTable.getTableNumber()).isEqualTo("A_01");
        assertThat(storedTable.getName()).isEqualTo("Window Two");
        assertThat(storedTable.getFloor()).isEqualTo("first floor");
        assertThat(storedTable.getQrCodeValue()).isEqualTo("https://pos.example.test/tables/a-01");
        assertThat(storedTable.getCreatedAt()).isNotNull();
        assertThat(storedTable.getUpdatedAt()).isNotNull();

        assertThat(storedReservation.getReservationCode()).isEqualTo("RSV_001");
        assertThat(storedReservation.getContactName()).isEqualTo("Alice Example");
        assertThat(storedReservation.getContactPhone()).isEqualTo("+491512345678");
        assertThat(storedReservation.getContactEmail()).isEqualTo("alice@example.test");
        assertThat(storedReservation.getSeatingPreference()).isEqualTo("window");
        assertThat(storedReservation.getDepositAmount()).isEqualByComparingTo("25.00");
        assertThat(storedReservation.getCreatedAt()).isNotNull();

        assertThat(storedHistory.getChangedAt()).isNotNull();
        assertThat(storedHistory.getReason()).isEqualTo("phone confirmation");

        assertThat(storedAssignment.isPrimaryAssignment()).isTrue();
        assertThat(storedAssignment.getAssignedAt()).isNotNull();
        assertThat(storedAssignment.getCreatedAt()).isNotNull();
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
}
