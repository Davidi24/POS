package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.menu.dto.response.PublicMenuResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.mapper.PublicMenuMapper;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicMenuService {

    private final RestaurantRepository restaurantRepository;
    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final PublicMenuMapper publicMenuMapper;

    // Public menus follow the restaurant-local date and daily hours, not the server clock.
    @Transactional(readOnly = true)
    public List<PublicMenuResponse> getMenus(UUID restaurantId) {
        Restaurant restaurant = findPublicRestaurant(restaurantId);
        LocalDateTime now = restaurantNow(restaurant);
        return menuRepository.findPublicMenusByRestaurantId(restaurant.getId()).stream()
                .filter(menu -> menu.isAvailableAt(now))
                .map(publicMenuMapper::toMenuResponse)
                .toList();
    }

    // Read-only transaction: the mapper reads lazy collections (e.g. ingredients) and open-in-view is off.
    @Transactional(readOnly = true)
    public PublicMenuResponse getMenu(UUID restaurantId, UUID menuId, boolean includeSections, boolean includeItems) {
        Restaurant restaurant = findPublicRestaurant(restaurantId);
        Menu menu = menuRepository.findPublicMenuByRestaurantIdAndId(restaurantId, menuId)
                .orElseThrow(MenuNotFoundException::new);
        LocalDateTime now = restaurantNow(restaurant);
        if (!menu.isAvailableAt(now)) {
            throw new MenuNotFoundException();
        }
        if (!includeSections && !includeItems) {
            return publicMenuMapper.toMenuResponse(menu);
        }

        List<MenuSection> sections = menuSectionRepository.findByMenuIdAndActiveTrueOrderByDisplayOrderAscNameAsc(menuId);
        // Customers only see dishes staff switched on for the online menu.
        Map<UUID, List<MenuItem>> itemsBySectionId = includeItems
                ? menuItemRepository.findByMenuIdAndAvailableTrueOrdered(menuId).stream()
                .filter(MenuItem::isShowOnline)
                .collect(Collectors.groupingBy(
                        item -> item.getSection().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ))
                : Map.of();

        if (includeItems) {
            sections = sections.stream().filter(section -> itemsBySectionId.containsKey(section.getId())).toList();
        }
        return publicMenuMapper.toMenuResponse(menu, sections, itemsBySectionId);
    }

    private LocalDateTime restaurantNow(Restaurant restaurant) {
        try {
            return LocalDateTime.now(ZoneId.of(restaurant.getTimezone() == null ? "UTC" : restaurant.getTimezone()));
        } catch (RuntimeException invalidTimezone) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    private Restaurant findPublicRestaurant(UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
        if (!restaurant.isActive() || restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new RestaurantNotFoundException();
        }
        return restaurant;
    }
}
