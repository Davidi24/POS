package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.repository.KdsStationRoutingRepository;
import pos.pos.menu.dto.response.MenuItemSummaryResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.security.scope.ActorScopeService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// "Import existing": puts COPIES of dishes from other menus into a section (agreed with the owner: a copy has its own
// price, so changing one never changes the other). Variants, option groups and kitchen routing come along; the copy
// isn't shown online until staff choose to.
@Service
@RequiredArgsConstructor
public class MenuItemImportService {

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final KdsStationRoutingRepository kdsStationRoutingRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;

    @Transactional
    public List<MenuItemSummaryResponse> importItems(Authentication authentication, UUID menuId, UUID sectionId, List<UUID> itemIds) {
        Menu menu = menuRepository.findByIdAndRestaurantDeletedAtIsNull(menuId).orElseThrow(MenuNotFoundException::new);
        menuPolicy.assertCanManage(actorScopeService.resolve(authentication), menu);
        if (menu.getRestaurant().getStatus() == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
        MenuSection section = menuSectionRepository.findById(sectionId).orElseThrow(MenuSectionNotFoundException::new);
        if (!section.getMenu().getId().equals(menu.getId())) {
            throw new MenuSectionMenuMismatchException();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        Map<UUID, MenuItem> sources = menuItemRepository.findAllForImportByIdIn(ids).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        if (sources.size() != ids.size()) {
            throw new AuthException("Some of these items no longer exist", HttpStatus.NOT_FOUND);
        }
        UUID restaurantId = menu.getRestaurant().getId();
        for (MenuItem source : sources.values()) {
            if (!Objects.equals(source.getSection().getMenu().getRestaurant().getId(), restaurantId)) {
                throw new AuthException("Items can only be imported from this restaurant's menus", HttpStatus.BAD_REQUEST);
            }
        }

        int order = section.getItems() == null ? 0 : section.getItems().stream()
                .map(MenuItem::getDisplayOrder).filter(Objects::nonNull).max(Integer::compare).orElse(-1) + 1;
        Map<UUID, List<MenuVariant>> variantsBySourceId = menuVariantRepository.findByMenuItemIdInOrdered(ids).stream()
                .collect(Collectors.groupingBy(variant -> variant.getMenuItem().getId()));
        Map<UUID, List<MenuItemOptionGroup>> optionGroupsBySourceId = menuItemOptionGroupRepository.findByMenuItemIdInOrdered(ids).stream()
                .collect(Collectors.groupingBy(group -> group.getMenuItem().getId()));
        Map<UUID, List<KdsStationRouting>> routingsBySourceId = kdsStationRoutingRepository.findAllByMenuItemIdInOrdered(ids).stream()
                .collect(Collectors.groupingBy(routing -> routing.getMenuItem().getId()));
        List<MenuItemSummaryResponse> imported = new ArrayList<>();
        for (UUID id : ids) {
            MenuItem source = sources.get(id);
            MenuItem copy = new MenuItem();
            copy.setSection(section);
            copy.setName(source.getName());
            copy.setDescription(source.getDescription());
            copy.setBasePrice(source.getBasePrice());
            copy.setImageUrl(source.getImageUrl());
            copy.setAvailable(source.isAvailable());
            copy.setSendToKitchen(source.isSendToKitchen());
            copy.setIngredients(List.copyOf(source.getIngredients()));
            copy.setOrderBeforeHours(source.getOrderBeforeHours());
            copy.setOccasionCodes(source.getOccasionCodes());
            copy.setDisplayOrder(order++);
            MenuItem saved = menuItemRepository.saveAndFlush(copy);

            List<MenuVariant> variants = new ArrayList<>();
            for (MenuVariant variant : variantsBySourceId.getOrDefault(id, List.of())) {
                MenuVariant variantCopy = new MenuVariant();
                variantCopy.setMenuItem(saved);
                variantCopy.setName(variant.getName());
                variantCopy.setPriceDelta(variant.getPriceDelta());
                variantCopy.setDefault(variant.isDefault());
                variantCopy.setActive(variant.isActive());
                variantCopy.setDisplayOrder(variant.getDisplayOrder());
                variants.add(menuVariantRepository.save(variantCopy));
            }
            List<MenuItemOptionGroup> groups = new ArrayList<>();
            for (MenuItemOptionGroup group : optionGroupsBySourceId.getOrDefault(id, List.of())) {
                MenuItemOptionGroup groupCopy = new MenuItemOptionGroup();
                groupCopy.setMenuItem(saved);
                groupCopy.setOptionGroup(group.getOptionGroup());
                groupCopy.setDisplayOrder(group.getDisplayOrder());
                groupCopy.setMinSelectOverride(group.getMinSelectOverride());
                groupCopy.setMaxSelectOverride(group.getMaxSelectOverride());
                groupCopy.setRequiredOverride(group.getRequiredOverride());
                groups.add(menuItemOptionGroupRepository.save(groupCopy));
            }
            for (KdsStationRouting routing : routingsBySourceId.getOrDefault(id, List.of())) {
                KdsStationRouting routingCopy = new KdsStationRouting();
                routingCopy.setStation(routing.getStation());
                routingCopy.setMenuItem(saved);
                routingCopy.setDisplayOrder(routing.getDisplayOrder());
                routingCopy.setPriority(routing.getPriority());
                routingCopy.setCourseLabel(routing.getCourseLabel());
                routingCopy.setActive(routing.isActive());
                kdsStationRoutingRepository.save(routingCopy);
            }
            imported.add(menuMapper.toMenuItemResponse(saved, variants, groups));
        }
        return imported;
    }
}
