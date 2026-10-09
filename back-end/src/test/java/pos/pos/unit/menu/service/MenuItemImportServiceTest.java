package pos.pos.unit.menu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import pos.pos.kds.repository.KdsStationRoutingRepository;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.service.MenuItemImportService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.security.scope.ActorScopeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "Import existing" makes copies (agreed with the owner): a copy's price is its own.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Importing items into a special menu")
class MenuItemImportServiceTest {

    @Mock MenuRepository menuRepository;
    @Mock MenuSectionRepository sectionRepository;
    @Mock MenuItemRepository itemRepository;
    @Mock MenuVariantRepository variantRepository;
    @Mock MenuItemOptionGroupRepository optionGroupRepository;
    @Mock KdsStationRoutingRepository routingRepository;
    @Mock ActorScopeService actorScopeService;
    @Mock MenuPolicy menuPolicy;
    @Mock Authentication authentication;

    private Menu menu(Restaurant restaurant) {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setRestaurant(restaurant);
        return menu;
    }

    private MenuSection section(Menu menu) {
        MenuSection section = new MenuSection();
        section.setId(UUID.randomUUID());
        section.setMenu(menu);
        return section;
    }

    private MenuItemImportService service() {
        return new MenuItemImportService(menuRepository, sectionRepository, itemRepository, variantRepository, optionGroupRepository,
                routingRepository, new MenuMapper(), actorScopeService, menuPolicy);
    }

    @Test
    @DisplayName("copies the dish with its price and variants into the section, as a separate item")
    void copies() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        Menu special = menu(restaurant);
        MenuSection extras = section(special);
        MenuItem tiramisu = new MenuItem();
        tiramisu.setId(UUID.randomUUID());
        tiramisu.setSection(section(menu(restaurant)));
        tiramisu.setName("Tiramisù");
        tiramisu.setSku("DES-01");
        tiramisu.setBasePrice(new BigDecimal("7.50"));
        MenuVariant big = new MenuVariant();
        big.setName("Big");
        big.setPriceDelta(new BigDecimal("2.00"));
        big.setMenuItem(tiramisu);
        when(menuRepository.findByIdAndRestaurantDeletedAtIsNull(special.getId())).thenReturn(Optional.of(special));
        when(sectionRepository.findById(extras.getId())).thenReturn(Optional.of(extras));
        when(itemRepository.findAllForImportByIdIn(List.of(tiramisu.getId()))).thenReturn(List.of(tiramisu));
        when(itemRepository.saveAndFlush(any(MenuItem.class))).thenAnswer(call -> {
            MenuItem copy = call.getArgument(0);
            copy.setId(UUID.randomUUID());
            return copy;
        });
        when(variantRepository.findByMenuItemIdInOrdered(List.of(tiramisu.getId()))).thenReturn(List.of(big));
        when(variantRepository.save(any(MenuVariant.class))).thenAnswer(call -> call.getArgument(0));

        var imported = service().importItems(authentication, special.getId(), extras.getId(), List.of(tiramisu.getId()));

        assertThat(imported).singleElement().satisfies(copy -> {
            assertThat(copy.getId()).isNotEqualTo(tiramisu.getId());
            assertThat(copy.getName()).isEqualTo("Tiramisù");
            assertThat(copy.getBasePrice()).isEqualByComparingTo("7.50");
            assertThat(copy.getSku()).isNull();
        });
        verify(variantRepository).save(org.mockito.ArgumentMatchers.argThat(v -> v.getName().equals("Big") && v.getMenuItem() != null && v.getMenuItem() != tiramisu));
        assertThat(tiramisu.getSection()).isNotSameAs(extras);
        verify(itemRepository, never()).findAllById(any());
        verify(variantRepository, never()).findByMenuItemIdOrderByDisplayOrderAscNameAsc(tiramisu.getId());
    }

    @Test
    @DisplayName("loads source dishes and their variants, option groups, and routings in batches")
    void loadsImportSourcesInBatches() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        Menu targetMenu = menu(restaurant);
        MenuSection targetSection = section(targetMenu);
        Menu sourceMenu = menu(restaurant);
        MenuSection sourceSection = section(sourceMenu);
        MenuItem first = new MenuItem();
        first.setId(UUID.randomUUID());
        first.setSection(sourceSection);
        first.setName("First");
        first.setBasePrice(new BigDecimal("1.00"));
        MenuItem second = new MenuItem();
        second.setId(UUID.randomUUID());
        second.setSection(sourceSection);
        second.setName("Second");
        second.setBasePrice(new BigDecimal("2.00"));
        List<UUID> itemIds = List.of(first.getId(), second.getId());
        when(menuRepository.findByIdAndRestaurantDeletedAtIsNull(targetMenu.getId())).thenReturn(Optional.of(targetMenu));
        when(sectionRepository.findById(targetSection.getId())).thenReturn(Optional.of(targetSection));
        when(itemRepository.findAllForImportByIdIn(itemIds)).thenReturn(List.of(first, second));
        when(itemRepository.saveAndFlush(any(MenuItem.class))).thenAnswer(call -> {
            MenuItem copy = call.getArgument(0);
            copy.setId(UUID.randomUUID());
            return copy;
        });

        service().importItems(authentication, targetMenu.getId(), targetSection.getId(), itemIds);

        verify(itemRepository).findAllForImportByIdIn(itemIds);
        verify(variantRepository).findByMenuItemIdInOrdered(itemIds);
        verify(optionGroupRepository).findByMenuItemIdInOrdered(itemIds);
        verify(routingRepository).findAllByMenuItemIdInOrdered(itemIds);
        verify(itemRepository, never()).findAllById(any());
        verify(variantRepository, never()).findByMenuItemIdOrderByDisplayOrderAscNameAsc(any());
        verify(optionGroupRepository, never()).findByMenuItemIdOrdered(any());
    }

    @Test
    @DisplayName("refuses items from another restaurant")
    void otherRestaurant() {
        Restaurant mine = new Restaurant();
        mine.setId(UUID.randomUUID());
        mine.setStatus(RestaurantStatus.ACTIVE);
        Restaurant other = new Restaurant();
        other.setId(UUID.randomUUID());
        Menu special = menu(mine);
        MenuSection extras = section(special);
        MenuItem foreign = new MenuItem();
        foreign.setId(UUID.randomUUID());
        foreign.setSection(section(menu(other)));
        when(menuRepository.findByIdAndRestaurantDeletedAtIsNull(special.getId())).thenReturn(Optional.of(special));
        when(sectionRepository.findById(extras.getId())).thenReturn(Optional.of(extras));
        when(itemRepository.findAllForImportByIdIn(any())).thenAnswer(call ->
                call.getArgument(0).equals(List.of(foreign.getId())) ? List.of(foreign) : List.of()
        );

        assertThatThrownBy(() -> service().importItems(authentication, special.getId(), extras.getId(), List.of(foreign.getId())))
                .hasMessageContaining("this restaurant's menus");
    }
}
