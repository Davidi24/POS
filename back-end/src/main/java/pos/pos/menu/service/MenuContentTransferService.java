package pos.pos.menu.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.menu.MenuDeletionRequiresConfirmationException;
import pos.pos.exception.menu.MenuSectionDeletionRequiresConfirmationException;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.util.MenuNames;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Preserves content inside the caller's deletion transaction; never copies or merges products. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MenuContentTransferService {
    private final MenuRepository menus;
    private final MenuSectionRepository sections;
    private final MenuItemRepository items;
    private final EntityManager entityManager;

    public void lock(Menu menu) {
        // Serialize deletions for one restaurant, including creation of its fallback menu.
        entityManager.lock(menu.getRestaurant(), LockModeType.PESSIMISTIC_WRITE);
    }

    public void preserveItems(MenuSection source, List<MenuItem> moving) {
        if (moving.isEmpty()) return;
        if (MenuNames.isUncategorized(source.getName())) {
            throw new MenuSectionDeletionRequiresConfirmationException();
        }
        Menu menu = source.getMenu();
        MenuSection target = sections.findByMenuIdOrderByDisplayOrderAscNameAsc(menu.getId()).stream()
                .filter(section -> MenuNames.isUncategorized(section.getName()))
                .findFirst().orElseGet(() -> {
                    MenuSection created = new MenuSection();
                    created.setMenu(menu);
                    created.setName(MenuNames.UNCATEGORIZED);
                    created.setActive(true);
                    created.setDisplayOrder(MenuNames.LAST);
                    return sections.saveAndFlush(created);
                });
        List<MenuItem> existing = items.findBySectionIdOrderByDisplayOrderAscNameAsc(target.getId());
        Set<String> used = new HashSet<>();
        int order = 0;
        for (MenuItem item : existing) {
            used.add(MenuNames.key(item.getName()));
            item.setDisplayOrder(order++);
        }
        target.setActive(true);
        target.setDisplayOrder(MenuNames.LAST);
        for (MenuItem item : moving) {
            item.setName(MenuNames.reserveUnique(item.getName(), used));
            item.setDisplayOrder(order++);
            item.setSection(target);
        }
        items.saveAll(moving);
    }

    public void preserveSections(Menu source, List<MenuSection> moving, UUID actorId) {
        if (moving.isEmpty()) return;
        if ("UNCATEGORIZED".equals(source.getCode())) {
            throw new MenuDeletionRequiresConfirmationException();
        }
        Menu target = menus.findByRestaurantIdAndCode(source.getRestaurant().getId(), "UNCATEGORIZED")
                .orElseGet(() -> {
                    Menu created = new Menu();
                    created.setRestaurant(source.getRestaurant());
                    created.setCode("UNCATEGORIZED");
                    created.setName(MenuNames.UNCATEGORIZED);
                    created.setActive(true);
                    created.setDisplayOrder(MenuNames.LAST);
                    created.setCreatedBy(actorId);
                    created.setUpdatedBy(actorId);
                    return menus.saveAndFlush(created);
                });
        // Fetch every destination name BEFORE mutating managed entities: a query can auto-flush
        // an already moved section and hit the database's unique constraint before it is renamed.
        List<MenuSection> existing = sections.findByMenuIdOrderByDisplayOrderAscNameAsc(target.getId());
        Set<String> used = new HashSet<>();
        int order = 0;
        for (MenuSection section : existing) {
            used.add(MenuNames.key(section.getName()));
            section.setDisplayOrder(MenuNames.sectionOrder(section.getName(), order++));
        }
        target.setActive(true);
        target.setDisplayOrder(MenuNames.LAST);
        target.setUpdatedBy(actorId);
        for (MenuSection section : moving) {
            section.setName(MenuNames.reserveUnique(section.getName(), used));
            section.setDisplayOrder(MenuNames.sectionOrder(section.getName(), order++));
            section.setMenu(target);
        }
        sections.saveAll(moving);
    }
}
