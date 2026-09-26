package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.menu.dto.response.OnlineMenuResponse;
import pos.pos.menu.dto.response.OnlineMenuSectionResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.OnlineMenuSection;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.OnlineMenuSectionRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// The restaurant's online menu: its own sections, filled with dishes that point at them (dishes are never copied).
// A dish is visible online on a date when it's available and its staff menu and section are active on that date.
@Service
@RequiredArgsConstructor
public class OnlineMenuService {

    private final OnlineMenuSectionRepository onlineMenuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantScopeService restaurantScopeService;

    // Where "Show in online menu" puts a dish: the given section, or the one with this name (any case), created if missing.
    public OnlineMenuSection resolveSection(Restaurant restaurant, UUID sectionId, String sectionName) {
        if (sectionId != null) {
            return onlineMenuSectionRepository.findByIdAndRestaurant_Id(sectionId, restaurant.getId())
                    .orElseThrow(() -> new AuthException("Online section not found", HttpStatus.BAD_REQUEST));
        }
        String name = NormalizationUtils.normalize(sectionName);
        if (name == null) {
            throw new AuthException("Choose an online section for this dish", HttpStatus.BAD_REQUEST);
        }
        return onlineMenuSectionRepository.findFirstByRestaurant_IdAndNameIgnoreCase(restaurant.getId(), name)
                .orElseGet(() -> {
                    OnlineMenuSection section = new OnlineMenuSection();
                    section.setRestaurant(restaurant);
                    section.setName(name);
                    section.setDisplayOrder(onlineMenuSectionRepository.findMaxDisplayOrder(restaurant.getId()) + 1);
                    return onlineMenuSectionRepository.save(section);
                });
    }

    // Puts a dish in an online section (at its end when it's new there), or takes it offline with null.
    // A section left without dishes is removed, so the online menu never carries empty sections.
    public void place(MenuItem item, OnlineMenuSection section) {
        OnlineMenuSection previous = item.getOnlineSection();
        if (section != null && (previous == null || !Objects.equals(previous.getId(), section.getId()))) {
            item.setOnlineDisplayOrder(section.getId() == null ? 0 : menuItemRepository.findMaxOnlineDisplayOrder(section.getId()) + 1);
        }
        item.setOnlineSection(section);
        if (previous != null && (section == null || !Objects.equals(previous.getId(), section.getId()))) {
            removeIfEmpty(previous, item);
        }
    }

    // After a dish left `section` (moved, taken offline or deleted): drop the section if nothing else is in it.
    public void removeIfEmpty(OnlineMenuSection section, MenuItem leaving) {
        boolean othersLeft = menuItemRepository.findByOnlineSection_Id(section.getId()).stream()
                .anyMatch(other -> !Objects.equals(other.getId(), leaving.getId()));
        if (!othersLeft) {
            onlineMenuSectionRepository.delete(section);
        }
    }

    // After dishes were deleted in bulk (a whole staff section or menu): drop online sections nothing points at anymore.
    public void removeEmptySections(UUID restaurantId) {
        onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(restaurantId).stream()
                .filter(section -> menuItemRepository.countByOnlineSection_Id(section.getId()) == 0)
                .forEach(onlineMenuSectionRepository::delete);
    }

    // New order of the dishes inside one online section; must list every dish in it once.
    @Transactional
    public OnlineMenuResponse reorderSectionItems(Authentication authentication, UUID restaurantId, UUID sectionId, List<UUID> itemIds) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        requireSection(restaurantId, sectionId);
        List<MenuItem> items = menuItemRepository.findByOnlineSection_Id(sectionId);
        if (itemIds.size() != items.size() || !new HashSet<>(itemIds).equals(new HashSet<>(items.stream().map(MenuItem::getId).toList()))) {
            throw new AuthException("itemIds must list every dish in this online section exactly once", HttpStatus.BAD_REQUEST);
        }
        Map<UUID, MenuItem> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        for (int position = 0; position < itemIds.size(); position++) {
            byId.get(itemIds.get(position)).setOnlineDisplayOrder(position);
        }
        menuItemRepository.saveAll(items);
        return getPreview(authentication, restaurantId);
    }

    @Transactional(readOnly = true)
    public List<OnlineMenuSectionResponse> getSections(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(restaurantId).stream()
                .map(section -> OnlineMenuSectionResponse.builder()
                        .id(section.getId())
                        .name(section.getName())
                        .displayOrder(section.getDisplayOrder())
                        .itemCount(menuItemRepository.countByOnlineSection_Id(section.getId()))
                        .build())
                .toList();
    }

    // Staff preview for today: every section (empty ones too) and every placed dish, marking the ones customers can't see now.
    @Transactional(readOnly = true)
    public OnlineMenuResponse getPreview(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return build(restaurant, today(restaurant), true);
    }

    // What the website shows: only visible dishes, and only sections that have some.
    @Transactional(readOnly = true)
    public OnlineMenuResponse getPublic(UUID restaurantId, LocalDate date) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .filter(found -> found.isActive() && found.getStatus() == RestaurantStatus.ACTIVE)
                .orElseThrow(RestaurantNotFoundException::new);
        return build(restaurant, date == null ? today(restaurant) : date, false);
    }

    @Transactional
    public OnlineMenuSectionResponse renameSection(Authentication authentication, UUID restaurantId, UUID sectionId, String name) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        OnlineMenuSection section = requireSection(restaurantId, sectionId);
        String normalized = NormalizationUtils.normalize(name);
        onlineMenuSectionRepository.findFirstByRestaurant_IdAndNameIgnoreCase(restaurantId, normalized)
                .filter(other -> !Objects.equals(other.getId(), sectionId))
                .ifPresent(other -> {
                    throw new AuthException("The online menu already has a section called " + other.getName(), HttpStatus.BAD_REQUEST);
                });
        section.setName(normalized);
        section.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        OnlineMenuSection saved = onlineMenuSectionRepository.save(section);
        return OnlineMenuSectionResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .displayOrder(saved.getDisplayOrder())
                .itemCount(menuItemRepository.countByOnlineSection_Id(saved.getId()))
                .build();
    }

    @Transactional
    public List<OnlineMenuSectionResponse> reorderSections(Authentication authentication, UUID restaurantId, List<UUID> sectionIds) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        List<OnlineMenuSection> sections = onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(restaurantId);
        if (sectionIds.size() != sections.size() || !new HashSet<>(sectionIds).equals(new HashSet<>(sections.stream().map(OnlineMenuSection::getId).toList()))) {
            throw new AuthException("sectionIds must list every online section exactly once", HttpStatus.BAD_REQUEST);
        }
        Map<UUID, OnlineMenuSection> byId = new LinkedHashMap<>();
        sections.forEach(section -> byId.put(section.getId(), section));
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        for (int position = 0; position < sectionIds.size(); position++) {
            OnlineMenuSection section = byId.get(sectionIds.get(position));
            section.setDisplayOrder(position);
            section.setUpdatedBy(actorId);
        }
        onlineMenuSectionRepository.saveAll(sections);
        return getSections(authentication, restaurantId);
    }

    // Only empty sections can go; dishes are moved or taken offline from their own form.
    @Transactional
    public void deleteSection(Authentication authentication, UUID restaurantId, UUID sectionId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        OnlineMenuSection section = requireSection(restaurantId, sectionId);
        if (menuItemRepository.countByOnlineSection_Id(sectionId) > 0) {
            throw new AuthException("Move its dishes to another online section or take them offline first", HttpStatus.CONFLICT);
        }
        onlineMenuSectionRepository.delete(section);
    }

    private OnlineMenuResponse build(Restaurant restaurant, LocalDate date, boolean staffPreview) {
        Map<UUID, List<OnlineMenuResponse.Item>> itemsBySection = new LinkedHashMap<>();
        for (MenuItem item : menuItemRepository.findOnlineByRestaurantId(restaurant.getId())) {
            String hiddenReason = hiddenReason(item, date);
            if (hiddenReason != null && !staffPreview) {
                continue;
            }
            itemsBySection.computeIfAbsent(item.getOnlineSection().getId(), id -> new ArrayList<>()).add(toItem(item, hiddenReason));
        }

        List<OnlineMenuResponse.Section> sections = new ArrayList<>();
        for (OnlineMenuSection section : onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(restaurant.getId())) {
            List<OnlineMenuResponse.Item> items = itemsBySection.getOrDefault(section.getId(), List.of());
            if (items.isEmpty() && !staffPreview) {
                continue;
            }
            sections.add(OnlineMenuResponse.Section.builder()
                    .id(section.getId())
                    .name(section.getName())
                    .displayOrder(section.getDisplayOrder())
                    .items(items)
                    .build());
        }
        return OnlineMenuResponse.builder().restaurantId(restaurant.getId()).date(date).sections(sections).build();
    }

    private String hiddenReason(MenuItem item, LocalDate date) {
        Menu menu = item.getSection().getMenu();
        if (!item.isAvailable()) {
            return "Sold out";
        }
        if (!menu.isActive() || !item.getSection().isActive()) {
            return "Its menu or section is switched off";
        }
        if (!menu.isAvailableOn(date)) {
            return "Its menu isn't offered on this date";
        }
        return null;
    }

    private OnlineMenuResponse.Item toItem(MenuItem item, String hiddenReason) {
        return OnlineMenuResponse.Item.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .basePrice(item.getBasePrice())
                .imageUrl(item.getImageUrl())
                .ingredients(List.copyOf(item.getIngredients()))
                .available(item.isAvailable())
                .sendToKitchen(item.isSendToKitchen())
                .displayOrder(item.getOnlineDisplayOrder())
                .menuId(item.getSection().getMenu().getId())
                .menuName(item.getSection().getMenu().getName())
                .menuSectionId(item.getSection().getId())
                .menuSectionName(item.getSection().getName())
                .visible(hiddenReason == null)
                .hiddenReason(hiddenReason)
                .build();
    }

    private OnlineMenuSection requireSection(UUID restaurantId, UUID sectionId) {
        return onlineMenuSectionRepository.findByIdAndRestaurant_Id(sectionId, restaurantId)
                .orElseThrow(() -> new AuthException("Online section not found", HttpStatus.NOT_FOUND));
    }

    private static LocalDate today(Restaurant restaurant) {
        try {
            return LocalDate.now(restaurant.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(restaurant.getTimezone()));
        } catch (RuntimeException invalidZone) {
            return LocalDate.now(ZoneOffset.UTC);
        }
    }
}
