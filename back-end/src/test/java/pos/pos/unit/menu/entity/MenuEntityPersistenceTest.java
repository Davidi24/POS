package pos.pos.unit.menu.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.support.AbstractTestProfilePostgresTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MenuEntityPersistenceTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should persist the menu graph with normalized values and audit fields")
    void shouldPersistMenuGraphWithNormalizedValuesAndAuditFields() {
        Restaurant restaurant = restaurant();
        entityManager.persist(restaurant);

        OptionGroupType optionGroupType = new OptionGroupType();
        optionGroupType.setName(" Single Select ");
        entityManager.persist(optionGroupType);

        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(" lunch specials ");
        menu.setName(" Lunch Specials ");
        menu.setDescription(" Midday menu ");
        entityManager.persist(menu);

        MenuSection section = new MenuSection();
        section.setMenu(menu);
        section.setName(" Burgers ");
        section.setDescription(" Signature burgers ");
        entityManager.persist(section);

        MenuItem item = new MenuItem();
        item.setSection(section);
        item.setSku(" brg-001 ");
        item.setName(" House Burger ");
        item.setDescription(" Double patty ");
        item.setBasePrice(new BigDecimal("12.50"));
        item.setImageUrl(" https://cdn.example.test/burger.png ");
        entityManager.persist(item);

        MenuVariant variant = new MenuVariant();
        variant.setMenuItem(item);
        variant.setName(" Large ");
        variant.setSku(" brg-001-l ");
        variant.setPriceDelta(new BigDecimal("2.00"));
        entityManager.persist(variant);

        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRestaurant(restaurant);
        optionGroup.setType(optionGroupType);
        optionGroup.setName(" Add Ons ");
        optionGroup.setDescription(" Optional extras ");
        optionGroup.setMinSelect(0);
        optionGroup.setMaxSelect(3);
        entityManager.persist(optionGroup);

        OptionItem optionItem = new OptionItem();
        optionItem.setOptionGroup(optionGroup);
        optionItem.setCode(" extra cheese ");
        optionItem.setName(" Extra Cheese ");
        optionItem.setPriceDelta(new BigDecimal("1.50"));
        entityManager.persist(optionItem);

        MenuItemOptionGroup link = new MenuItemOptionGroup();
        link.setMenuItem(item);
        link.setOptionGroup(optionGroup);
        link.setDisplayOrder(1);
        link.setMinSelectOverride(0);
        link.setMaxSelectOverride(2);
        entityManager.persist(link);

        entityManager.flush();
        entityManager.clear();

        Menu storedMenu = entityManager.find(Menu.class, menu.getId());
        MenuItem storedItem = entityManager.find(MenuItem.class, item.getId());
        MenuVariant storedVariant = entityManager.find(MenuVariant.class, variant.getId());
        OptionGroupType storedType = entityManager.find(OptionGroupType.class, optionGroupType.getId());
        OptionGroup storedGroup = entityManager.find(OptionGroup.class, optionGroup.getId());
        OptionItem storedOptionItem = entityManager.find(OptionItem.class, optionItem.getId());
        MenuItemOptionGroup storedLink = entityManager.find(MenuItemOptionGroup.class, link.getId());

        assertThat(storedMenu.getCode()).isEqualTo("LUNCH_SPECIALS");
        assertThat(storedMenu.getName()).isEqualTo("Lunch Specials");
        assertThat(storedMenu.getDescription()).isEqualTo("Midday menu");
        assertThat(storedMenu.getCreatedAt()).isNotNull();
        assertThat(storedMenu.getUpdatedAt()).isNotNull();

        assertThat(storedItem.getSku()).isEqualTo("BRG-001");
        assertThat(storedItem.getName()).isEqualTo("House Burger");
        assertThat(storedItem.getImageUrl()).isEqualTo("https://cdn.example.test/burger.png");

        assertThat(storedVariant.getSku()).isEqualTo("BRG-001-L");
        assertThat(storedVariant.isDefault()).isFalse();

        assertThat(storedType.getCode()).isEqualTo("SINGLE_SELECT");
        assertThat(storedType.getName()).isEqualTo("Single Select");

        assertThat(storedGroup.getName()).isEqualTo("Add Ons");
        assertThat(storedGroup.getMinSelect()).isZero();
        assertThat(storedGroup.getMaxSelect()).isEqualTo(3);

        assertThat(storedOptionItem.getCode()).isEqualTo("EXTRA_CHEESE");
        assertThat(storedOptionItem.getName()).isEqualTo("Extra Cheese");

        assertThat(storedLink.getDisplayOrder()).isEqualTo(1);
        assertThat(storedLink.getMaxSelectOverride()).isEqualTo(2);
        assertThat(storedLink.getCreatedAt()).isNotNull();
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(" Demo Kitchen ");
        restaurant.setLegalName(" Demo Kitchen LLC ");
        restaurant.setCode(" demo_kitchen ");
        restaurant.setSlug(" demo-kitchen ");
        restaurant.setDescription(" Neighborhood restaurant ");
        restaurant.setCurrency(" usd ");
        restaurant.setTimezone(" Europe/Berlin ");
        return restaurant;
    }
}
