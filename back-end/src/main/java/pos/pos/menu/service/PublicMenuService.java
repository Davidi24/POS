package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

import java.time.LocalDate;
import java.time.ZoneId;
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

    public List<PublicMenuResponse> getMenus(UUID restaurantId) {
        Restaurant restaurant = findPublicRestaurant(restaurantId);
        LocalDate today = LocalDate.now(ZoneId.of(restaurant.getTimezone()));
        return menuRepository.findPublicMenusByRestaurantId(restaurant.getId()).stream()
                .filter(menu -> menu.isAvailableOn(today))
                .map(publicMenuMapper::toMenuResponse)
                .toList();
    }

    public PublicMenuResponse getMenu(UUID restaurantId, UUID menuId, boolean includeSections, boolean includeItems) {
        Restaurant restaurant = findPublicRestaurant(restaurantId);
        Menu menu = menuRepository.findPublicMenuByRestaurantIdAndId(restaurantId, menuId)
                .orElseThrow(MenuNotFoundException::new);
        LocalDate today = LocalDate.now(ZoneId.of(restaurant.getTimezone()));
        if (!menu.isAvailableOn(today)) {
            throw new MenuNotFoundException();
        }
        if (!includeSections && !includeItems) {
            return publicMenuMapper.toMenuResponse(menu);
        }

        List<MenuSection> sections = menuSectionRepository.findByMenuIdAndActiveTrueOrderByDisplayOrderAscNameAsc(menuId);
        Map<UUID, List<MenuItem>> itemsBySectionId = includeItems
                ? menuItemRepository.findByMenuIdAndAvailableTrueOrdered(menuId).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getSection().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ))
                : Map.of();

        return publicMenuMapper.toMenuResponse(menu, sections, itemsBySectionId);
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
