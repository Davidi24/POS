package pos.pos.unit.menu.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pos.pos.menu.entity.*;
import pos.pos.menu.repository.*;
import pos.pos.menu.service.MenuContentTransferService;
import pos.pos.menu.util.MenuNames;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.exception.menu.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

class MenuContentTransferServiceTest {
    private final MenuRepository menus = mock(MenuRepository.class);
    private final MenuSectionRepository sections = mock(MenuSectionRepository.class);
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final MenuContentTransferService service = new MenuContentTransferService(menus, sections, items, mock(EntityManager.class));

    @Test
    void preservesDistinctItemsAndAppendsThemToExistingFallback() {
        Menu menu = menu("Dinner");
        MenuSection source = section(menu, "Mains", 500);
        MenuSection fallback = section(menu, "Uncategorized", 0);
        fallback.setActive(false);
        MenuItem existing = item(fallback, "Soup", 100);
        MenuItem moved = item(source, "soup", 0);
        MenuItem another = item(source, "Soup", 1);
        UUID originalId = moved.getId();
        when(sections.findByMenuIdOrderByDisplayOrderAscNameAsc(menu.getId())).thenReturn(List.of(fallback, source));
        when(items.findBySectionIdOrderByDisplayOrderAscNameAsc(fallback.getId())).thenReturn(List.of(existing));
        service.preserveItems(source, List.of(moved, another));
        assertThat(fallback.getDisplayOrder()).isEqualTo(Integer.MAX_VALUE);
        assertThat(fallback.isActive()).isTrue();
        assertThat(moved.getName()).isEqualTo("soup (2)");
        assertThat(another.getName()).isEqualTo("Soup (3)");
        assertThat(moved.getId()).isEqualTo(originalId);
        assertThat(moved.getBasePrice()).isEqualByComparingTo("12.50");
        assertThat(moved.getSection()).isSameAs(fallback);
        assertThat(existing.getDisplayOrder()).isZero();
        assertThat(moved.getDisplayOrder()).isEqualTo(1);
        assertThat(another.getDisplayOrder()).isEqualTo(2);
        verify(sections, never()).saveAndFlush(any());
    }

    @Test
    void createsFallbackLastEvenWithSparseSectionPositions() {
        Menu menu = menu("Dinner");
        MenuSection source = section(menu, "Mains", 9000);
        when(sections.findByMenuIdOrderByDisplayOrderAscNameAsc(menu.getId())).thenReturn(List.of(source));
        when(sections.saveAndFlush(any())).thenAnswer(call -> { MenuSection s = call.getArgument(0); s.setId(UUID.randomUUID()); return s; });
        MenuItem moved = item(source, "Soup", 0);
        service.preserveItems(source, List.of(moved));
        assertThat(moved.getSection().getName()).isEqualTo("Uncategorized");
        assertThat(moved.getSection().getDisplayOrder()).isGreaterThan(source.getDisplayOrder());
    }

    @Test
    void handlesLongSectionNamesAndNamesReservedEarlierInTheBatch() {
        Menu source = menu("Lunch");
        Menu target = menu("Uncategorized");
        String longName = "A".repeat(150);
        MenuSection existing = section(target, longName, 0);
        MenuSection moved = section(source, longName, 0);
        MenuSection second = section(source, "A".repeat(146) + " (2)", 1);
        when(menus.findByRestaurantIdAndCode(source.getRestaurant().getId(), "UNCATEGORIZED")).thenReturn(Optional.of(target));
        when(sections.findByMenuIdOrderByDisplayOrderAscNameAsc(target.getId())).thenReturn(List.of(existing));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> service.preserveSections(source, List.of(moved, second), UUID.randomUUID()));
        assertThat(moved.getName()).hasSize(150).endsWith(" (2)");
        assertThat(second.getName()).hasSize(150).endsWith(" (3)");
        assertThat(moved.getMenu()).isSameAs(target);
        assertThat(second.getMenu()).isSameAs(target);
        assertThat(moved.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void protectsNonemptyFallbackButAllowsEmptyFallbackRemoval() {
        Menu fallbackMenu = menu("Uncategorized");
        MenuSection fallback = section(fallbackMenu, "uncategorized", 0);
        assertThatThrownBy(() -> service.preserveItems(fallback, List.of(item(fallback, "Soup", 0))))
                .isInstanceOf(MenuSectionDeletionRequiresConfirmationException.class);
        assertThatThrownBy(() -> service.preserveSections(fallbackMenu, List.of(fallback), UUID.randomUUID()))
                .isInstanceOf(MenuDeletionRequiresConfirmationException.class);
        service.preserveItems(fallback, List.of());
        service.preserveSections(fallbackMenu, List.of(), UUID.randomUUID());
        verifyNoInteractions(menus, sections, items);
    }

    @Test
    void reservesSuffixSpaceForLongAndUnicodeNames() {
        String name = "🍕".repeat(150);
        Set<String> used = new HashSet<>(Set.of(MenuNames.key(name)));
        String result = MenuNames.reserveUnique(name, used);
        assertThat(result.codePointCount(0, result.length())).isEqualTo(150);
        assertThat(result).endsWith(" (2)");
        assertThat(MenuNames.sectionOrder("Uncategorized", 0)).isEqualTo(Integer.MAX_VALUE);
        assertThat(MenuNames.sectionOrder("Mains", Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE - 1);
    }

    private Menu menu(String name) {
        Restaurant restaurant = new Restaurant(); restaurant.setId(UUID.randomUUID());
        Menu menu = new Menu(); menu.setId(UUID.randomUUID()); menu.setRestaurant(restaurant);
        menu.setName(name); menu.setCode(name.toUpperCase(Locale.ROOT)); return menu;
    }
    private MenuSection section(Menu menu, String name, int order) {
        MenuSection section = new MenuSection(); section.setId(UUID.randomUUID()); section.setMenu(menu);
        section.setName(name); section.setDisplayOrder(order); return section;
    }
    private MenuItem item(MenuSection section, String name, int order) {
        MenuItem item = new MenuItem(); item.setId(UUID.randomUUID()); item.setSection(section);
        item.setName(name); item.setDisplayOrder(order); item.setBasePrice(new BigDecimal("12.50")); return item;
    }
}
