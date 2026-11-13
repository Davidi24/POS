package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.dto.request.MenuWaiterViewRequest;
import pos.pos.menu.dto.response.MenuWaiterViewResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuWaiterView;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuWaiterViewRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Which menus waiters see, in Menu and at the till (Menu → "Menus shown"): the menus marked shown_to_waiters. With
// "special menus only", just the special (event) menus while one is on; on other days the marked menus, so the till
// is never empty. Managers still see and manage every menu.
@Service
@RequiredArgsConstructor
public class MenuWaiterViewService {

    private final RestaurantScopeService restaurantScopeService;
    private final MenuRepository menuRepository;
    private final MenuWaiterViewRepository viewRepository;

    @Transactional(readOnly = true)
    public MenuWaiterViewResponse getView(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return toResponse(restaurantId, specialMenusOnly(restaurantId));
    }

    @Transactional
    public MenuWaiterViewResponse saveView(Authentication authentication, MenuWaiterViewRequest request) {
        UUID restaurantId = request.getRestaurantId();
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        boolean specialOnly = Boolean.TRUE.equals(request.getSpecialMenusOnly());
        Set<UUID> shown = new HashSet<>(request.getShownMenuIds());
        List<Menu> menus = menuRepository.findAllByRestaurantId(restaurantId);
        Set<UUID> known = new HashSet<>(menus.stream().map(Menu::getId).toList());
        if (!known.containsAll(shown)) {
            throw new AuthException("Choose this restaurant's menus", HttpStatus.BAD_REQUEST);
        }
        // Needed even with "special menus only": they're what waiters see on days without a special menu.
        if (menus.stream().noneMatch(menu -> menu.isActive() && shown.contains(menu.getId()))) {
            throw new AuthException("Pick at least one active menu for waiters", HttpStatus.BAD_REQUEST);
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        for (Menu menu : menus) {
            boolean show = shown.contains(menu.getId());
            if (menu.isShownToWaiters() != show) {
                menu.setShownToWaiters(show);
                menu.setUpdatedBy(actorId);
                menuRepository.save(menu);
            }
        }
        MenuWaiterView view = viewRepository.findById(restaurantId).orElseGet(() -> {
            MenuWaiterView created = new MenuWaiterView();
            created.setRestaurantId(restaurantId);
            return created;
        });
        view.setSpecialMenusOnly(specialOnly);
        view.setUpdatedBy(actorId);
        viewRepository.save(view);
        return toResponse(restaurantId, specialOnly);
    }

    // True when no other active menu of the restaurant shows in POS (hiding this one would leave the till empty).
    public boolean isLastShownMenu(Menu menu) {
        return menuRepository.findAllByRestaurantId(menu.getRestaurant().getId()).stream()
                .noneMatch(other -> !other.getId().equals(menu.getId()) && other.isActive() && other.isShownToWaiters());
    }

    public boolean specialMenusOnly(UUID restaurantId) {
        return viewRepository.findById(restaurantId).map(MenuWaiterView::isSpecialMenusOnly).orElse(false);
    }

    // Of the active menus that are on now, the ones waiters see.
    public static List<Menu> forWaiters(List<Menu> onNow, boolean specialMenusOnly) {
        if (specialMenusOnly) {
            List<Menu> special = onNow.stream().filter(Menu::isSpecial).toList();
            if (!special.isEmpty()) {
                return special;
            }
        }
        return onNow.stream().filter(Menu::isShownToWaiters).toList();
    }

    private MenuWaiterViewResponse toResponse(UUID restaurantId, boolean specialOnly) {
        List<UUID> shown = menuRepository.findAllByRestaurantId(restaurantId).stream()
                .filter(Menu::isShownToWaiters)
                .map(Menu::getId)
                .toList();
        return MenuWaiterViewResponse.builder().specialMenusOnly(specialOnly).shownMenuIds(shown).build();
    }
}
