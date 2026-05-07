package pos.pos.unit.tables.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.dto.ReorderTableCategoriesRequest;
import pos.pos.tables.dto.TableCategoryRequest;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.enums.TableLocationType;
import pos.pos.tables.mapper.TableCategoryMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;
import pos.pos.tables.service.TableCategoryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TableCategoryService")
class TableCategoryServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000602");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000603");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000604");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-05-06T12:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-05-06T12:30:00Z");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private TableCategoryRepository tableCategoryRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @org.mockito.Spy
    private TableCategoryMapper tableCategoryMapper;

    @InjectMocks
    private TableCategoryService tableCategoryService;

    @Test
    @DisplayName("Should create a table category for an accessible branch")
    void shouldCreateTableCategoryForAccessibleBranch() {
        Authentication authentication = authentication();
        Branch branch = branch();
        TableCategoryRequest request = TableCategoryRequest.builder()
                .code("patio")
                .name("Patio")
                .description("Outdoor seats")
                .defaultCapacity(4)
                .locationType(TableLocationType.PATIO)
                .color("#22aa66")
                .displayOrder(3)
                .active(true)
                .build();

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(tableCategoryRepository.saveAndFlush(any(TableCategory.class))).thenAnswer(invocation -> {
            TableCategory category = invocation.getArgument(0);
            category.setId(CATEGORY_ID);
            category.setCreatedAt(CREATED_AT);
            category.setUpdatedAt(UPDATED_AT);
            return category;
        });

        TableCategoryResponse response = tableCategoryService.createTableCategory(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getId()).isEqualTo(CATEGORY_ID);
        assertThat(response.getRestaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getCode()).isEqualTo("patio");
        assertThat(response.getDefaultCapacity()).isEqualTo(4);
        assertThat(response.getLocationType()).isEqualTo(TableLocationType.PATIO);
        assertThat(response.getDisplayOrder()).isEqualTo(3);
        assertThat(response.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should apply default category values when optional fields are omitted")
    void shouldApplyDefaultCategoryValuesWhenOptionalFieldsAreOmitted() {
        Authentication authentication = authentication();
        Branch branch = branch();
        TableCategoryRequest request = TableCategoryRequest.builder()
                .code("main")
                .name("Main Dining")
                .defaultCapacity(4)
                .displayOrder(0)
                .build();

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(tableCategoryRepository.saveAndFlush(any(TableCategory.class))).thenAnswer(invocation -> {
            TableCategory category = invocation.getArgument(0);
            category.setId(CATEGORY_ID);
            category.setCreatedAt(CREATED_AT);
            category.setUpdatedAt(UPDATED_AT);
            return category;
        });

        TableCategoryResponse response = tableCategoryService.createTableCategory(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getLocationType()).isEqualTo(TableLocationType.INDOOR);
        assertThat(response.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should reject reorder requests that do not include every category exactly once")
    void shouldRejectInvalidReorderRequests() {
        Authentication authentication = authentication();
        Branch branch = branch();
        TableCategory first = tableCategory(UUID.fromString("00000000-0000-0000-0000-000000000605"), "MAIN_DINING", "Main Dining", 0);
        TableCategory second = tableCategory(UUID.fromString("00000000-0000-0000-0000-000000000606"), "PATIO", "Patio", 1);

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(tableCategoryRepository.findAllByBranch_IdOrderByDisplayOrderAscNameAsc(BRANCH_ID)).thenReturn(List.of(first, second));

        ReorderTableCategoriesRequest request = ReorderTableCategoriesRequest.builder()
                .categoryIds(List.of(first.getId()))
                .build();

        assertThatThrownBy(() -> tableCategoryService.reorderTableCategories(authentication, RESTAURANT_ID, BRANCH_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("categoryIds must include every table category exactly once");

        verify(tableCategoryRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("Should reject deletion while tables still reference the category")
    void shouldRejectDeleteWhenCategoryIsStillAssignedToTables() {
        Authentication authentication = authentication();
        Branch branch = branch();
        TableCategory tableCategory = tableCategory(CATEGORY_ID, "VIP", "VIP", 0);

        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(tableCategoryRepository.findByIdAndBranch_Id(CATEGORY_ID, BRANCH_ID)).thenReturn(Optional.of(tableCategory));
        when(restaurantTableRepository.existsByCategory_Id(CATEGORY_ID)).thenReturn(true);

        assertThatThrownBy(() -> tableCategoryService.deleteTableCategory(authentication, RESTAURANT_ID, BRANCH_ID, CATEGORY_ID))
                .isInstanceOf(AuthException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(tableCategoryRepository, never()).delete(any(TableCategory.class));
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

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        return branch;
    }

    private TableCategory tableCategory(UUID id, String code, String name, int displayOrder) {
        TableCategory tableCategory = new TableCategory();
        tableCategory.setId(id);
        tableCategory.setBranch(branch());
        tableCategory.setCode(code);
        tableCategory.setName(name);
        tableCategory.setDefaultCapacity(4);
        tableCategory.setLocationType(TableLocationType.INDOOR);
        tableCategory.setDisplayOrder(displayOrder);
        tableCategory.setActive(true);
        return tableCategory;
    }
}
