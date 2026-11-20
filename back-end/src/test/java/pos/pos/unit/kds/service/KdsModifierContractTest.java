package pos.pos.unit.kds.service;

import org.junit.jupiter.api.Test;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.entity.OrderItemOption;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class KdsModifierContractTest {
    @Test void ticketIncludesOrderedVariantAndModifierSnapshots() {
        var line = new OrderLineItem(); line.setVariantNameSnapshot("Large"); line.setOptionsPerUnit(false);
        var option = new OrderItemOption(); option.setOptionNameSnapshot("No peanuts"); option.setQuantity(2); option.setNotes("Allergy");
        line.setOptions(List.of(option));
        var item = new KdsTicketItem(); item.setOrderLineItem(line);
        var response = new KdsMapper().toTicketItemResponse(item);
        assertThat(response.getVariantNameSnapshot()).isEqualTo("Large"); assertThat(response.isOptionsPerUnit()).isFalse();
        assertThat(response.getModifiers()).singleElement().satisfies(modifier -> {
            assertThat(modifier.name()).isEqualTo("No peanuts"); assertThat(modifier.quantity()).isEqualTo(2); assertThat(modifier.notes()).isEqualTo("Allergy");
        });
    }
    @Test void missingLegacyLineHasAnEmptyModifierList() {
        var response = new KdsMapper().toTicketItemResponse(new KdsTicketItem());
        assertThat(response.getModifiers()).isEmpty(); assertThat(response.isOptionsPerUnit()).isTrue();
    }
}
