package pos.pos.menu.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// The online menu as customers see it on a given date (staff preview also lists dishes that are hidden right now).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineMenuResponse {

    private UUID restaurantId;
    private LocalDate date;
    private List<Section> sections;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private UUID id;
        private String name;
        private Integer displayOrder;
        private List<Item> items;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID id;
        private String sku;
        private String name;
        private String description;
        private BigDecimal basePrice;
        private String imageUrl;
        private List<String> ingredients;
        private Boolean available;
        private Boolean sendToKitchen;
        // Position inside its online section.
        private Integer displayOrder;
        // Where the dish lives in the staff menus.
        private UUID menuId;
        private String menuName;
        private UUID menuSectionId;
        private String menuSectionName;
        // False when customers can't see it right now (e.g. sold out); only the staff preview returns those.
        private Boolean visible;
        private String hiddenReason;
    }
}
