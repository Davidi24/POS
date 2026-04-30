package pos.pos.unit.menu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.menu.OptionItemGroupMismatchException;
import pos.pos.menu.dto.CreateOptionItemRequest;
import pos.pos.menu.dto.OptionItemResponse;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.menu.service.OptionItemService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptionItemService")
class OptionItemServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000491");
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000492");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000493");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000494");
    private static final UUID FOREIGN_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000495");

    @Mock
    private OptionGroupRepository optionGroupRepository;

    @Mock
    private OptionItemRepository optionItemRepository;

    private OptionItemService optionItemService;
    private StubRestaurantScopeService restaurantScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        restaurantScopeService = new StubRestaurantScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        optionItemService = new OptionItemService(
                optionGroupRepository,
                optionItemRepository,
                new MenuMapper(),
                restaurantScopeService,
                restaurantValidationService
        );
    }

    @Test
    @DisplayName("getOptionItems should apply the availability filter")
    void shouldReturnFilteredItems() {
        Restaurant restaurant = restaurant();
        OptionGroup group = optionGroup(restaurant, GROUP_ID);
        OptionItem item = optionItem(group, ITEM_ID, "Bacon");

        restaurantScopeService.accessibleRestaurant = restaurant;
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(optionItemRepository.findByOptionGroupIdAndAvailableOrderByDisplayOrderAscNameAsc(GROUP_ID, true)).willReturn(List.of(item));

        List<OptionItemResponse> response = optionItemService.getOptionItems(authentication(), GROUP_ID, true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(ITEM_ID);
        assertThat(response.get(0).getName()).isEqualTo("Bacon");
    }

    @Test
    @DisplayName("createOptionItem should derive code from name and persist defaults")
    void shouldCreateOptionItem() {
        Restaurant restaurant = restaurant();
        OptionGroup group = optionGroup(restaurant, GROUP_ID);
        CreateOptionItemRequest request = CreateOptionItemRequest.builder()
                .name(" Extra Cheese ")
                .priceDelta(new BigDecimal("1.25"))
                .build();

        restaurantScopeService.manageableRestaurant = restaurant;
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(optionItemRepository.existsByOptionGroupIdAndName(GROUP_ID, "Extra Cheese")).willReturn(false);
        given(optionItemRepository.saveAndFlush(any(OptionItem.class))).willAnswer(invocation -> {
            OptionItem saved = invocation.getArgument(0);
            saved.setId(ITEM_ID);
            return saved;
        });

        OptionItemResponse response = optionItemService.createOptionItem(authentication(), GROUP_ID, request);

        ArgumentCaptor<OptionItem> captor = ArgumentCaptor.forClass(OptionItem.class);
        verify(optionItemRepository).saveAndFlush(captor.capture());
        OptionItem saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("EXTRA_CHEESE");
        assertThat(saved.getName()).isEqualTo("Extra Cheese");
        assertThat(saved.getPriceDelta()).isEqualByComparingTo("1.25");
        assertThat(saved.isAvailable()).isTrue();
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(response.getId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("updateOptionItem should reject items that belong to another option group")
    void shouldRejectMismatchedItem() {
        Restaurant restaurant = restaurant();
        OptionGroup group = optionGroup(restaurant, GROUP_ID);
        OptionGroup foreignGroup = optionGroup(restaurant, FOREIGN_GROUP_ID);
        OptionItem foreignItem = optionItem(foreignGroup, ITEM_ID, "Bacon");

        restaurantScopeService.manageableRestaurant = restaurant;
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(optionItemRepository.findById(ITEM_ID)).willReturn(Optional.of(foreignItem));

        assertThatThrownBy(() -> optionItemService.updateOptionItem(
                authentication(),
                GROUP_ID,
                ITEM_ID,
                pos.pos.menu.dto.UpdateOptionItemRequest.builder()
                        .name("Bacon")
                        .available(true)
                        .displayOrder(1)
                        .build()
        )).isInstanceOf(OptionItemGroupMismatchException.class)
                .hasMessage("Option item does not belong to this option group");
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("owner@pos.local")
                        .username("owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        restaurant.setCode("POS_MAIN");
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        return restaurant;
    }

    private OptionGroup optionGroup(Restaurant restaurant, UUID groupId) {
        OptionGroupType type = new OptionGroupType();
        type.setId(TYPE_ID);
        type.setCode("SINGLE_SELECT");
        type.setName("Single Select");

        OptionGroup group = new OptionGroup();
        group.setId(groupId);
        group.setRestaurant(restaurant);
        group.setType(type);
        group.setName("Sauces");
        group.setActive(true);
        group.setDisplayOrder(1);
        return group;
    }

    private OptionItem optionItem(OptionGroup group, UUID itemId, String name) {
        OptionItem item = new OptionItem();
        item.setId(itemId);
        item.setOptionGroup(group);
        item.setCode("BACON");
        item.setName(name);
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
    }

    private static class StubRestaurantScopeService extends RestaurantScopeService {

        private Restaurant accessibleRestaurant;
        private Restaurant manageableRestaurant;

        StubRestaurantScopeService() {
            super(null, null, null, null, null);
        }

        @Override
        public Restaurant requireAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
            return accessibleRestaurant;
        }

        @Override
        public Restaurant requireManageableRestaurant(Authentication authentication, UUID restaurantId) {
            return manageableRestaurant;
        }
    }

    private static class StubRestaurantValidationService extends RestaurantValidationService {

        StubRestaurantValidationService() {
            super(null, null);
        }

        @Override
        public void validateManageableStatus(RestaurantStatus status) {
        }
    }
}
