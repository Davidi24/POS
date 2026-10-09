package pos.pos.notification.service;

import org.junit.jupiter.api.Test;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.entity.InventoryCountLine;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.entity.PaymentTransaction;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.entity.ShiftBreak;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEntityResolverTest {

    private final NotificationEntityResolver resolver = new NotificationEntityResolver();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void resolvesChildEntitiesThroughTheirRestaurantScopedParent() {
        Payment payment = new Payment();
        payment.setRestaurant(restaurant());
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);

        Shift shift = new Shift();
        shift.setRestaurant(restaurant());
        ShiftBreak shiftBreak = new ShiftBreak();
        shiftBreak.setShift(shift);

        InventoryCount count = new InventoryCount();
        count.setRestaurant(restaurant());
        InventoryCountLine countLine = new InventoryCountLine();
        countLine.setInventoryCount(count);

        Recipe recipe = new Recipe();
        recipe.setRestaurant(restaurant());
        RecipeComponent component = new RecipeComponent();
        component.setRecipe(recipe);

        assertResolved(transaction, NotificationTopic.PAYMENT, "PAYMENT_TRANSACTION");
        assertResolved(shiftBreak, NotificationTopic.SHIFT, "SHIFT_BREAK");
        assertResolved(countLine, NotificationTopic.INVENTORY, "INVENTORY_COUNT_LINE");
        assertResolved(component, NotificationTopic.RECIPE, "RECIPE_COMPONENT");
    }

    private void assertResolved(Object entity, NotificationTopic topic, String referenceType) {
        var resolved = resolver.resolveEntityChange(entity, NotificationMutationType.UPSERT);

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().topic()).isEqualTo(topic);
        assertThat(resolved.orElseThrow().restaurantId()).isEqualTo(restaurantId);
        assertThat(resolved.orElseThrow().referenceType()).isEqualTo(referenceType);
        assertThat(resolved.orElseThrow().eventCode()).isEqualTo(topic + "_" + referenceType + "_UPSERT");
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        return restaurant;
    }
}
