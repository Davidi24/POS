package pos.pos.menu.bootstrap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.RestaurantRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(110)
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.bootstrap.sample-menus.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LocalMenuSeedRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LocalMenuSeedRunner.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionGroupTypeRepository optionGroupTypeRepository;
    private final OptionItemRepository optionItemRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, OptionGroupType> types = seedOptionGroupTypes();

        for (LocalMenuSeedData.RestaurantSeedSpec restaurantSeed : LocalMenuSeedData.restaurants(types)) {
            seedRestaurantMenu(restaurantSeed);
        }
    }

    private void seedRestaurantMenu(LocalMenuSeedData.RestaurantSeedSpec restaurantSeed) {
        UUID restaurantId = restaurantSeed.restaurantId();
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElse(null);

        if (restaurant == null) {
            logger.warn("Skipping local sample menu seed for {} restaurant. Restaurant {} was not found.", restaurantSeed.label(), restaurantId);
            return;
        }

        Map<String, OptionGroup> groupsByName = new LinkedHashMap<>();
        for (LocalMenuSeedData.OptionGroupSpec spec : restaurantSeed.optionGroups()) {
            OptionGroup group = upsertOptionGroup(restaurant, spec);
            groupsByName.put(spec.name(), group);
            for (LocalMenuSeedData.OptionItemSpec itemSpec : spec.items()) {
                upsertOptionItem(group, itemSpec);
            }
        }

        for (LocalMenuSeedData.MenuSpec menuSpec : restaurantSeed.menus()) {
            Menu menu = upsertMenu(restaurant, menuSpec);
            for (LocalMenuSeedData.SectionSpec sectionSpec : menuSpec.sections()) {
                MenuSection section = upsertSection(menu, sectionSpec);
                for (LocalMenuSeedData.ItemSpec itemSpec : sectionSpec.items()) {
                    MenuItem item = upsertItem(section, itemSpec);
                    for (LocalMenuSeedData.VariantSpec variantSpec : itemSpec.variants()) {
                        upsertVariant(item, variantSpec);
                    }
                    for (LocalMenuSeedData.ItemOptionLinkSpec linkSpec : itemSpec.optionGroups()) {
                        OptionGroup optionGroup = groupsByName.get(linkSpec.optionGroupName());
                        if (optionGroup == null) {
                            throw new IllegalStateException("Missing option group seed named " + linkSpec.optionGroupName());
                        }
                        upsertItemOptionGroup(item, optionGroup, linkSpec);
                    }
                }
            }
        }

        logger.info("Local sample menu data ready for {} restaurant: {} ({})", restaurantSeed.label(), restaurant.getName(), restaurant.getId());
    }

    private Map<String, OptionGroupType> seedOptionGroupTypes() {
        Map<String, OptionGroupType> types = new LinkedHashMap<>();
        types.put("single", upsertOptionGroupType(
                "SINGLE_SELECT",
                "Single Select",
                "Exactly one option is expected unless min and max are overridden at the item level."
        ));
        types.put("multi", upsertOptionGroupType(
                "MULTI_SELECT",
                "Multi Select",
                "Multiple options can be selected within the configured limits."
        ));
        return types;
    }

    private OptionGroupType upsertOptionGroupType(String code, String name, String description) {
        OptionGroupType type = optionGroupTypeRepository.findAll().stream()
                .filter(existing -> code.equals(existing.getCode()) || name.equals(existing.getName()))
                .findFirst()
                .orElseGet(OptionGroupType::new);

        type.setCode(code);
        type.setName(name);
        type.setDescription(description);
        return optionGroupTypeRepository.save(type);
    }

    private OptionGroup upsertOptionGroup(Restaurant restaurant, LocalMenuSeedData.OptionGroupSpec spec) {
        OptionGroup group = optionGroupRepository.findAll().stream()
                .filter(existing -> existing.getRestaurant() != null
                        && restaurant.getId().equals(existing.getRestaurant().getId())
                        && spec.name().equals(existing.getName()))
                .findFirst()
                .orElseGet(OptionGroup::new);

        group.setRestaurant(restaurant);
        group.setType(spec.type());
        group.setName(spec.name());
        group.setDescription(spec.description());
        group.setMinSelect(spec.minSelect());
        group.setMaxSelect(spec.maxSelect());
        group.setRequired(spec.required());
        group.setDisplayOrder(spec.displayOrder());
        group.setActive(true);
        return optionGroupRepository.save(group);
    }

    private OptionItem upsertOptionItem(OptionGroup group, LocalMenuSeedData.OptionItemSpec spec) {
        OptionItem item = optionItemRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(group.getId()).stream()
                .filter(existing -> spec.name().equals(existing.getName()))
                .findFirst()
                .orElseGet(OptionItem::new);

        item.setOptionGroup(group);
        item.setCode(spec.code());
        item.setName(spec.name());
        item.setPriceDelta(spec.priceDelta());
        item.setAvailable(spec.available());
        item.setDisplayOrder(spec.displayOrder());
        return optionItemRepository.save(item);
    }

    private Menu upsertMenu(Restaurant restaurant, LocalMenuSeedData.MenuSpec spec) {
        Menu menu = menuRepository.findAll().stream()
                .filter(existing -> existing.getRestaurant() != null
                        && restaurant.getId().equals(existing.getRestaurant().getId())
                        && spec.code().equals(existing.getCode()))
                .findFirst()
                .orElseGet(Menu::new);

        menu.setRestaurant(restaurant);
        menu.setCode(spec.code());
        menu.setName(spec.name());
        menu.setDescription(spec.description());
        menu.setActive(spec.active());
        menu.setDisplayOrder(spec.displayOrder());
        return menuRepository.save(menu);
    }

    private MenuSection upsertSection(Menu menu, LocalMenuSeedData.SectionSpec spec) {
        MenuSection section = menuSectionRepository.findByMenuIdOrderByDisplayOrderAscNameAsc(menu.getId()).stream()
                .filter(existing -> spec.name().equals(existing.getName()))
                .findFirst()
                .orElseGet(MenuSection::new);

        section.setMenu(menu);
        section.setName(spec.name());
        section.setDescription(spec.description());
        section.setDisplayOrder(spec.displayOrder());
        section.setActive(spec.active());
        return menuSectionRepository.save(section);
    }

    private MenuItem upsertItem(MenuSection section, LocalMenuSeedData.ItemSpec spec) {
        MenuItem item = menuItemRepository.findBySectionIdOrderByDisplayOrderAscNameAsc(section.getId()).stream()
                .filter(existing -> spec.name().equals(existing.getName()))
                .findFirst()
                .orElseGet(MenuItem::new);

        item.setSection(section);
        item.setSku(spec.sku());
        item.setName(spec.name());
        item.setDescription(spec.description());
        item.setBasePrice(spec.basePrice());
        item.setImageUrl(spec.imageUrl());
        item.setAvailable(spec.available());
        item.setDisplayOrder(spec.displayOrder());
        return menuItemRepository.save(item);
    }

    private MenuVariant upsertVariant(MenuItem menuItem, LocalMenuSeedData.VariantSpec spec) {
        MenuVariant variant = menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(menuItem.getId()).stream()
                .filter(existing -> spec.name().equals(existing.getName()))
                .findFirst()
                .orElseGet(MenuVariant::new);

        variant.setMenuItem(menuItem);
        variant.setName(spec.name());
        variant.setSku(spec.sku());
        variant.setPriceDelta(spec.priceDelta());
        variant.setDefault(spec.isDefault());
        variant.setActive(spec.active());
        variant.setDisplayOrder(spec.displayOrder());
        return menuVariantRepository.save(variant);
    }

    private MenuItemOptionGroup upsertItemOptionGroup(
            MenuItem menuItem,
            OptionGroup optionGroup,
            LocalMenuSeedData.ItemOptionLinkSpec spec
    ) {
        Optional<MenuItemOptionGroup> existingLink = menuItemOptionGroupRepository.findByMenuItemIdOrdered(menuItem.getId()).stream()
                .filter(existing -> existing.getOptionGroup() != null
                        && optionGroup.getId().equals(existing.getOptionGroup().getId()))
                .findFirst();

        MenuItemOptionGroup link = existingLink.orElseGet(MenuItemOptionGroup::new);
        link.setMenuItem(menuItem);
        link.setOptionGroup(optionGroup);
        link.setDisplayOrder(spec.displayOrder());
        link.setMinSelectOverride(spec.minSelectOverride());
        link.setMaxSelectOverride(spec.maxSelectOverride());
        link.setRequiredOverride(spec.requiredOverride());
        return menuItemOptionGroupRepository.save(link);
    }
}
