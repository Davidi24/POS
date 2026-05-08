package pos.pos.unit.tables.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.dto.TableMergeRequest;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.enums.TableLocationType;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.mapper.RestaurantTableMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;
import pos.pos.tables.service.RestaurantTableAvailabilityService;
import pos.pos.tables.service.RestaurantTableLayoutService;
import pos.pos.tables.service.RestaurantTableSupport;
import pos.pos.tables.service.RestaurantTableService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantTableService")
class RestaurantTableServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID PRIMARY_TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");
    private static final UUID CHILD_TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000706");
    private static final UUID SECOND_CHILD_TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private TableCategoryRepository tableCategoryRepository;

    @Mock
    private ReservationTableAssignmentRepository reservationTableAssignmentRepository;

    @Mock
    private RestaurantTableLayoutService restaurantTableLayoutService;

    @Mock
    private RestaurantTableAvailabilityService restaurantTableAvailabilityService;

    @Spy
    private RestaurantTableMapper restaurantTableMapper;

    private RestaurantTableService restaurantTableService;

    @BeforeEach
    void setUp() {
        RestaurantTableSupport restaurantTableSupport = new RestaurantTableSupport(
                restaurantTableRepository,
                tableCategoryRepository,
                restaurantTableMapper
        );
        restaurantTableService = new RestaurantTableService(
                restaurantScopeService,
                restaurantTableRepository,
                reservationTableAssignmentRepository,
                restaurantTableSupport,
                restaurantTableLayoutService,
                restaurantTableAvailabilityService
        );
    }

    @Test
    @DisplayName("Should create a table with defaults and category mapping")
    void shouldCreateTableWithDefaultsAndCategoryMapping() {
        Authentication authentication = authentication();
        Branch branch = branch();
        TableCategory category = category(branch);
        TableRequest request = TableRequest.builder()
                .categoryId(CATEGORY_ID)
                .tableNumber("a-01")
                .name("Window")
                .capacity(4)
                .floor("Main")
                .build();

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(tableCategoryRepository.findByIdAndBranch_Id(CATEGORY_ID, BRANCH_ID)).thenReturn(Optional.of(category));
        when(restaurantTableRepository.saveAndFlush(any(RestaurantTable.class))).thenAnswer(invocation -> {
            RestaurantTable table = invocation.getArgument(0);
            table.setId(PRIMARY_TABLE_ID);
            table.setCreatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
            table.setUpdatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
            return table;
        });

        TableResponse response = restaurantTableService.createTable(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getId()).isEqualTo(PRIMARY_TABLE_ID);
        assertThat(response.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(response.getShape()).isEqualTo(TableShape.RECTANGLE);
        assertThat(response.getStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(response.getActive()).isTrue();
        assertThat(response.getEffectiveCapacity()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should merge child tables into primary table and return effective capacity")
    void shouldMergeChildTablesIntoPrimaryTableAndReturnEffectiveCapacity() {
        Authentication authentication = authentication();
        Branch branch = branch();
        RestaurantTable primaryTable = table(branch, PRIMARY_TABLE_ID, "A1", 4);
        RestaurantTable firstChild = table(branch, CHILD_TABLE_ID, "A2", 2);
        RestaurantTable secondChild = table(branch, SECOND_CHILD_TABLE_ID, "A3", 2);

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(restaurantTableRepository.findByIdAndBranch_Id(PRIMARY_TABLE_ID, BRANCH_ID)).thenReturn(Optional.of(primaryTable));
        when(restaurantTableRepository.findAllByBranch_IdAndIdIn(eq(BRANCH_ID), anyCollection()))
                .thenReturn(List.of(firstChild, secondChild));
        when(restaurantTableRepository.existsByMergedInto_Id(CHILD_TABLE_ID)).thenReturn(false);
        when(restaurantTableRepository.existsByMergedInto_Id(SECOND_CHILD_TABLE_ID)).thenReturn(false);
        when(restaurantTableRepository.saveAllAndFlush(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));
        when(restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(PRIMARY_TABLE_ID)).thenReturn(List.of(firstChild, secondChild));

        TableResponse response = restaurantTableService.mergeTable(
                authentication,
                RESTAURANT_ID,
                BRANCH_ID,
                PRIMARY_TABLE_ID,
                TableMergeRequest.builder()
                        .tableIds(List.of(CHILD_TABLE_ID, SECOND_CHILD_TABLE_ID))
                        .build()
        );

        assertThat(firstChild.getMergedInto()).isEqualTo(primaryTable);
        assertThat(secondChild.getMergedInto()).isEqualTo(primaryTable);
        assertThat(response.getMergedTableIds()).containsExactly(CHILD_TABLE_ID, SECOND_CHILD_TABLE_ID);
        assertThat(response.getEffectiveCapacity()).isEqualTo(8);
        verify(restaurantScopeService, never()).requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID);
    }

    @Test
    @DisplayName("Should reject delete when reservation assignments exist")
    void shouldRejectDeleteWhenReservationAssignmentsExist() {
        Authentication authentication = authentication();
        Branch branch = branch();
        RestaurantTable table = table(branch, PRIMARY_TABLE_ID, "A1", 4);

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantTableRepository.findByIdAndBranch_Id(PRIMARY_TABLE_ID, BRANCH_ID)).thenReturn(Optional.of(table));
        when(reservationTableAssignmentRepository.existsByRestaurantTable_Id(PRIMARY_TABLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> restaurantTableService.deleteTable(authentication, RESTAURANT_ID, BRANCH_ID, PRIMARY_TABLE_ID))
                .isInstanceOf(AuthException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(restaurantTableRepository, never()).delete(any(RestaurantTable.class));
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("tables.admin@pos.example")
                        .username("tables.admin")
                        .active(true)
                        .build(),
                null
        );
    }

    private Branch branch() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Tables Restaurant");
        restaurant.setLegalName("Tables Restaurant LLC");
        restaurant.setCode("TABLES_RESTAURANT");
        restaurant.setSlug("tables-restaurant");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        branch.setName("Main Branch");
        branch.setCode("MAIN");
        return branch;
    }

    private TableCategory category(Branch branch) {
        TableCategory category = new TableCategory();
        category.setId(CATEGORY_ID);
        category.setBranch(branch);
        category.setCode("MAIN");
        category.setName("Main");
        category.setDefaultCapacity(4);
        category.setLocationType(TableLocationType.INDOOR);
        return category;
    }

    private RestaurantTable table(Branch branch, UUID id, String tableNumber, int capacity) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setRestaurant(branch.getRestaurant());
        table.setBranch(branch);
        table.setTableNumber(tableNumber);
        table.setName(tableNumber);
        table.setCapacity(capacity);
        table.setShape(TableShape.RECTANGLE);
        table.setStatus(TableStatus.AVAILABLE);
        table.setActive(true);
        table.setCreatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        table.setUpdatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        return table;
    }
}
