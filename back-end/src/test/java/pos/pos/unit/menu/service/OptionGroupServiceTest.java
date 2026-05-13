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
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.OptionGroupDeletionBlockedException;
import pos.pos.menu.dto.CreateOptionGroupRequest;
import pos.pos.menu.dto.OptionGroupResponse;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.menu.service.OptionGroupService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptionGroupService")
class OptionGroupServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000481");
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000482");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000483");

    @Mock
    private OptionGroupRepository optionGroupRepository;

    @Mock
    private OptionGroupTypeRepository optionGroupTypeRepository;

    @Mock
    private OptionItemRepository optionItemRepository;

    @Mock
    private MenuItemOptionGroupRepository menuItemOptionGroupRepository;

    private OptionGroupService optionGroupService;
    private StubRestaurantScopeService restaurantScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        restaurantScopeService = new StubRestaurantScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        optionGroupService = new OptionGroupService(
                optionGroupRepository,
                optionGroupTypeRepository,
                optionItemRepository,
                menuItemOptionGroupRepository,
                new MenuMapper(),
                restaurantScopeService,
                restaurantValidationService
        );
    }

    @Test
    @DisplayName("getOptionGroups should include items when requested")
    void shouldReturnOptionGroupsWithItems() {
        Restaurant restaurant = restaurant();
        OptionGroupType type = type();
        OptionGroup group = optionGroup(restaurant, type);
        OptionItem item = optionItem(group, "Bacon");

        restaurantScopeService.accessibleRestaurant = restaurant;
        given(optionGroupRepository.searchByRestaurant(RESTAURANT_ID, TYPE_ID, true, "%sauce%")).willReturn(List.of(group));
        given(optionItemRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(GROUP_ID)).willReturn(List.of(item));

        List<OptionGroupResponse> response = optionGroupService.getOptionGroups(authentication(), RESTAURANT_ID, TYPE_ID, true, "sauce", true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(GROUP_ID);
        assertThat(response.get(0).getItems()).hasSize(1);
        assertThat(response.get(0).getItems().get(0).getName()).isEqualTo("Bacon");
    }

    @Test
    @DisplayName("createOptionGroup should normalize values and persist defaults")
    void shouldCreateOptionGroup() {
        Restaurant restaurant = restaurant();
        OptionGroupType type = type();
        CreateOptionGroupRequest request = CreateOptionGroupRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .typeId(TYPE_ID)
                .name(" Sauces ")
                .description(" Choose a sauce ")
                .minSelect(0)
                .maxSelect(2)
                .build();

        restaurantScopeService.manageableRestaurant = restaurant;
        given(optionGroupTypeRepository.findById(TYPE_ID)).willReturn(Optional.of(type));
        given(optionGroupRepository.existsByRestaurantIdAndName(RESTAURANT_ID, "Sauces")).willReturn(false);
        given(optionGroupRepository.saveAndFlush(any(OptionGroup.class))).willAnswer(invocation -> {
            OptionGroup saved = invocation.getArgument(0);
            saved.setId(GROUP_ID);
            return saved;
        });

        OptionGroupResponse response = optionGroupService.createOptionGroup(authentication(), request);

        ArgumentCaptor<OptionGroup> captor = ArgumentCaptor.forClass(OptionGroup.class);
        verify(optionGroupRepository).saveAndFlush(captor.capture());
        OptionGroup saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Sauces");
        assertThat(saved.getDescription()).isEqualTo("Choose a sauce");
        assertThat(saved.getMinSelect()).isZero();
        assertThat(saved.getMaxSelect()).isEqualTo(2);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(response.getId()).isEqualTo(GROUP_ID);
    }

    @Test
    @DisplayName("updateOptionGroup should reject minSelect greater than maxSelect")
    void shouldRejectInvalidSelectionBounds() {
        Restaurant restaurant = restaurant();
        OptionGroupType type = type();
        OptionGroup group = optionGroup(restaurant, type);

        restaurantScopeService.manageableRestaurant = restaurant;
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(optionGroupTypeRepository.findById(TYPE_ID)).willReturn(Optional.of(type));

        assertThatThrownBy(() -> optionGroupService.updateOptionGroup(
                authentication(),
                GROUP_ID,
                pos.pos.menu.dto.UpdateOptionGroupRequest.builder()
                        .typeId(TYPE_ID)
                        .name("Sauces")
                        .required(false)
                        .active(true)
                        .displayOrder(0)
                        .minSelect(3)
                        .maxSelect(2)
                        .build()
        )).isInstanceOf(AuthException.class)
                .hasMessage("minSelect must be less than or equal to maxSelect");
    }

    @Test
    @DisplayName("deleteOptionGroup should reject groups that still have dependent records")
    void shouldRejectDeleteWhenDependenciesExist() {
        Restaurant restaurant = restaurant();
        OptionGroupType type = type();
        OptionGroup group = optionGroup(restaurant, type);

        restaurantScopeService.manageableRestaurant = restaurant;
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(optionItemRepository.existsByOptionGroupId(GROUP_ID)).willReturn(true);

        assertThatThrownBy(() -> optionGroupService.deleteOptionGroup(authentication(), GROUP_ID))
                .isInstanceOf(OptionGroupDeletionBlockedException.class)
                .hasMessage("Option group cannot be deleted while it still has items or menu item assignments");

        verify(optionGroupRepository, never()).delete(any(OptionGroup.class));
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

    private OptionGroupType type() {
        OptionGroupType type = new OptionGroupType();
        type.setId(TYPE_ID);
        type.setCode("SINGLE_SELECT");
        type.setName("Single Select");
        return type;
    }

    private OptionGroup optionGroup(Restaurant restaurant, OptionGroupType type) {
        OptionGroup group = new OptionGroup();
        group.setId(GROUP_ID);
        group.setRestaurant(restaurant);
        group.setType(type);
        group.setName("Sauces");
        group.setDescription("Choose a sauce");
        group.setMinSelect(0);
        group.setMaxSelect(2);
        group.setRequired(false);
        group.setActive(true);
        group.setDisplayOrder(1);
        return group;
    }

    private OptionItem optionItem(OptionGroup group, String name) {
        OptionItem item = new OptionItem();
        item.setId(UUID.fromString("00000000-0000-0000-0000-000000000484"));
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
