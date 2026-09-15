package pos.pos.menu.bootstrap;

import pos.pos.menu.entity.OptionGroupType;
import pos.pos.restaurant.bootstrap.LocalRestaurantSeedRunner;

import java.time.LocalTime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LocalMenuSeedData {

    private LocalMenuSeedData() {
    }

    static List<RestaurantSeedSpec> restaurants(Map<String, OptionGroupType> types) {
        return List.of(
                new RestaurantSeedSpec(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_ID,
                        "Bistro",
                        bistroMenus(),
                        bistroOptionGroups(types)
                ),
                new RestaurantSeedSpec(
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID,
                        "Pizza",
                        pizzaMenus(),
                        pizzaOptionGroups(types)
                ),
                new RestaurantSeedSpec(
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID,
                        "Cafe",
                        cafeMenus(),
                        cafeOptionGroups(types)
                )
        );
    }

    private static List<OptionGroupSpec> bistroOptionGroups(Map<String, OptionGroupType> types) {
        return List.of(
                new OptionGroupSpec(
                        types.get("single"),
                        "Burger Bun",
                        "Choose the bun for signature burgers.",
                        1,
                        1,
                        true,
                        0,
                        List.of(
                                new OptionItemSpec("BRIOCHE", "Brioche", money("0.00"), true, 0),
                                new OptionItemSpec("SESAME", "Sesame", money("0.50"), true, 1),
                                new OptionItemSpec("LETTUCE_WRAP", "Lettuce Wrap", money("0.75"), true, 2)
                        )
                ),
                new OptionGroupSpec(
                        types.get("multi"),
                        "Burger Add-ons",
                        "Extra toppings for burgers and sandwiches.",
                        0,
                        4,
                        false,
                        1,
                        List.of(
                                new OptionItemSpec("CHEDDAR", "Cheddar", money("1.20"), true, 0),
                                new OptionItemSpec("BACON", "Smoked Bacon", money("1.80"), true, 1),
                                new OptionItemSpec("CARAM_ONION", "Caramelized Onion", money("0.90"), true, 2),
                                new OptionItemSpec("AVOCADO", "Avocado", money("1.60"), true, 3)
                        )
                ),
                new OptionGroupSpec(
                        types.get("single"),
                        "Steak Temperature",
                        "Temperature preference for grilled proteins.",
                        1,
                        1,
                        true,
                        2,
                        List.of(
                                new OptionItemSpec("MEDIUM_RARE", "Medium Rare", money("0.00"), true, 0),
                                new OptionItemSpec("MEDIUM", "Medium", money("0.00"), true, 1),
                                new OptionItemSpec("WELL_DONE", "Well Done", money("0.00"), true, 2)
                        )
                )
        );
    }

    private static List<OptionGroupSpec> pizzaOptionGroups(Map<String, OptionGroupType> types) {
        return List.of(
                new OptionGroupSpec(
                        types.get("single"),
                        "Pizza Size",
                        "Default size selection for pizza builds.",
                        1,
                        1,
                        true,
                        0,
                        List.of(
                                new OptionItemSpec("SIZE_10", "10 inch", money("0.00"), true, 0),
                                new OptionItemSpec("SIZE_12", "12 inch", money("3.50"), true, 1),
                                new OptionItemSpec("SIZE_16", "16 inch", money("7.00"), true, 2)
                        )
                ),
                new OptionGroupSpec(
                        types.get("multi"),
                        "Pizza Toppings",
                        "Extra toppings for pizzas.",
                        0,
                        6,
                        false,
                        1,
                        List.of(
                                new OptionItemSpec("PEPPERONI", "Pepperoni", money("1.50"), true, 0),
                                new OptionItemSpec("MUSHROOM", "Mushrooms", money("1.20"), true, 1),
                                new OptionItemSpec("OLIVES", "Black Olives", money("1.00"), true, 2),
                                new OptionItemSpec("JALAPENO", "Jalapenos", money("1.10"), true, 3),
                                new OptionItemSpec("BURRATA", "Burrata", money("2.50"), true, 4)
                        )
                ),
                new OptionGroupSpec(
                        types.get("single"),
                        "Dip Sauce",
                        "Side dip for crusts and bites.",
                        0,
                        1,
                        false,
                        2,
                        List.of(
                                new OptionItemSpec("RANCH", "Ranch", money("0.80"), true, 0),
                                new OptionItemSpec("GARLIC", "Garlic Aioli", money("0.90"), true, 1),
                                new OptionItemSpec("CHILI_HONEY", "Chili Honey", money("1.10"), true, 2)
                        )
                )
        );
    }

    private static List<OptionGroupSpec> cafeOptionGroups(Map<String, OptionGroupType> types) {
        return List.of(
                new OptionGroupSpec(
                        types.get("single"),
                        "Milk Choice",
                        "Milk base for espresso drinks.",
                        1,
                        1,
                        true,
                        0,
                        List.of(
                                new OptionItemSpec("WHOLE", "Whole Milk", money("0.00"), true, 0),
                                new OptionItemSpec("OAT", "Oat Milk", money("0.70"), true, 1),
                                new OptionItemSpec("ALMOND", "Almond Milk", money("0.70"), true, 2)
                        )
                ),
                new OptionGroupSpec(
                        types.get("multi"),
                        "Syrup Shot",
                        "Flavor additions for coffee and matcha drinks.",
                        0,
                        3,
                        false,
                        1,
                        List.of(
                                new OptionItemSpec("VANILLA", "Vanilla", money("0.50"), true, 0),
                                new OptionItemSpec("HAZELNUT", "Hazelnut", money("0.50"), true, 1),
                                new OptionItemSpec("CARAMEL", "Salted Caramel", money("0.60"), true, 2)
                        )
                ),
                new OptionGroupSpec(
                        types.get("single"),
                        "Toast Side",
                        "Side pairing for brunch toast items.",
                        0,
                        1,
                        false,
                        2,
                        List.of(
                                new OptionItemSpec("GREENS", "Dressed Greens", money("0.00"), true, 0),
                                new OptionItemSpec("FRUIT", "Seasonal Fruit", money("1.20"), true, 1),
                                new OptionItemSpec("CHIPS", "House Chips", money("1.50"), true, 2)
                        )
                )
        );
    }

    private static List<MenuSpec> bistroMenus() {
        return List.of(
                new MenuSpec(
                        "BISTRO_ALL_DAY",
                        "All Day Menu",
                        "Mock all-day bistro menu for local API checks.",
                        true,
                        0,
                        List.of(
                                new SectionSpec(
                                        "Starters",
                                        "Warm plates and small bites.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-TRUFFLE-FRIES", "Truffle Fries", "Crispy fries with parmesan, parsley, and black garlic aioli.", money("6.90"), null, true, 0, List.of(), List.of(), List.of("Potato", "Truffle Oil", "Parmesan", "Parsley", "Black Garlic Aioli")),
                                                new ItemSpec("BIS-BURRATA", "Burrata Plate", "Tomatoes, basil oil, toasted sourdough, and sea salt.", money("9.50"), null, true, 1, List.of(), List.of(), List.of("Burrata", "Heirloom Tomato", "Basil Oil", "Sourdough", "Sea Salt"))
                                        )
                                ),
                                new SectionSpec(
                                        "Mains",
                                        "Popular mains with variants and option groups.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec(
                                                        "BIS-SIGNATURE-BURGER",
                                                        "Signature Burger",
                                                        "Char-grilled beef patty, aged cheddar, pickles, and house sauce.",
                                                        money("13.90"),
                                                        null,
                                                        true,
                                                        0,
                                                        List.of(
                                                                new VariantSpec("Single Patty", "BIS-SIGNATURE-BURGER-S", money("0.00"), true, true, 0),
                                                                new VariantSpec("Double Patty", "BIS-SIGNATURE-BURGER-D", money("4.20"), false, true, 1)
                                                        ),
                                                        List.of(
                                                                new ItemOptionLinkSpec("Burger Bun", 0, 1, 1, true),
                                                                new ItemOptionLinkSpec("Burger Add-ons", 1, 0, 4, false)
                                                        ),
                                                        List.of("Beef Patty", "Aged Cheddar", "Brioche Bun", "Pickles", "House Sauce", "Lettuce")
                                                ),
                                                new ItemSpec(
                                                        "BIS-RIBEYE",
                                                        "Grilled Ribeye",
                                                        "Served with herb butter, roasted potatoes, and jus.",
                                                        money("24.00"),
                                                        null,
                                                        true,
                                                        1,
                                                        List.of(
                                                                new VariantSpec("250 g", "BIS-RIBEYE-250", money("0.00"), true, true, 0),
                                                                new VariantSpec("350 g", "BIS-RIBEYE-350", money("7.50"), false, true, 1)
                                                        ),
                                                        List.of(
                                                                new ItemOptionLinkSpec("Steak Temperature", 0, 1, 1, true)
                                                        ),
                                                        List.of("Ribeye Steak", "Herb Butter", "Roasted Potatoes", "Beef Jus", "Rosemary")
                                                )
                                        )
                                ),
                                new SectionSpec(
                                        "Desserts",
                                        "Simple dessert section for menu API testing.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-TIRAMISU", "Tiramisu", "Mascarpone cream, coffee sponge, and cocoa.", money("6.40"), null, true, 0, List.of(), List.of(), List.of("Mascarpone", "Ladyfingers", "Espresso", "Cocoa Powder", "Egg Yolk")),
                                                new ItemSpec("BIS-CHOC-MOUSSE", "Chocolate Mousse", "Dark chocolate mousse with sea salt and berries.", money("5.90"), null, true, 1, List.of(), List.of(), List.of("Dark Chocolate", "Heavy Cream", "Egg White", "Sea Salt", "Mixed Berries"))
                                        )
                                )
                        )
                ,
                        null,
                        null,
                        "#B88945"
                ),
                new MenuSpec(
                        "BISTRO_DRINKS",
                        "Drinks Menu",
                        "Cocktails, wine, and zero-proof mocktails.",
                        true,
                        1,
                        List.of(
                                new SectionSpec(
                                        "Cocktails",
                                        "House cocktails.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-SPRITZ", "Garden Spritz", "Elderflower, citrus, sparkling wine, and mint.", money("8.50"), null, true, 0, List.of(), List.of(), List.of("Elderflower Liqueur", "Sparkling Wine", "Citrus", "Mint", "Soda Water")),
                                                new ItemSpec("BIS-NEGRONI", "Smoked Negroni", "Gin, vermouth, bitter aperitif, and orange zest.", money("9.50"), null, true, 1, List.of(), List.of(), List.of("Gin", "Sweet Vermouth", "Bitter Aperitif", "Orange Zest"))
                                        )
                                ),
                                new SectionSpec(
                                        "Zero Proof",
                                        "Non-alcoholic drinks.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-HIBISCUS-FIZZ", "Hibiscus Fizz", "Hibiscus cordial, lemon, and sparkling water.", money("5.50"), null, true, 0, List.of(), List.of(), List.of("Hibiscus Cordial", "Lemon", "Sparkling Water", "Mint")),
                                                new ItemSpec("BIS-CUCUMBER-TONIC", "Cucumber Tonic", "Cucumber, lime, rosemary, and tonic.", money("5.20"), null, true, 1, List.of(), List.of(), List.of("Cucumber", "Lime", "Rosemary", "Tonic Water"))
                                        )
                                )
                        )
                ,
                        null,
                        null,
                        "#315F7B"
                ),
                new MenuSpec(
                        "BISTRO_LUNCH",
                        "Lunch Menu",
                        "Weekday lunch specials and lighter plates.",
                        true,
                        2,
                        List.of(
                                new SectionSpec(
                                        "Soups & Salads",
                                        "Light starters for midday.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-CAESAR", "Chicken Caesar Salad", "Grilled chicken, romaine, parmesan, and garlic croutons.", money("11.50"), null, true, 0, List.of(), List.of(), List.of("Grilled Chicken", "Romaine Lettuce", "Parmesan", "Garlic Croutons", "Caesar Dressing")),
                                                new ItemSpec("BIS-TOMATO-SOUP", "Roasted Tomato Soup", "Basil oil and a grilled cheese crouton.", money("7.20"), null, true, 1, List.of(), List.of(), List.of("Roasted Tomato", "Basil Oil", "Cream", "Grilled Cheese Crouton", "Garlic"))
                                        )
                                ),
                                new SectionSpec(
                                        "Sandwiches",
                                        "Handhelds served with house fries.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-CLUB", "Turkey Club", "Roast turkey, bacon, lettuce, tomato, and herb mayo on sourdough.", money("10.90"), null, true, 0, List.of(), List.of(), List.of("Roast Turkey", "Bacon", "Lettuce", "Tomato", "Herb Mayo", "Sourdough")),
                                                new ItemSpec("BIS-CHICKEN-WRAP", "Grilled Chicken Wrap", "Grilled chicken, greens, avocado, and chipotle sauce.", money("9.80"), null, true, 1, List.of(), List.of(), List.of("Grilled Chicken", "Mixed Greens", "Avocado", "Chipotle Sauce", "Flour Tortilla"))
                                        )
                                )
                        )
                ,
                        LocalTime.of(11, 0),
                        LocalTime.of(15, 0),
                        "#B88945"
                ),
                new MenuSpec(
                        "BISTRO_DINNER",
                        "Dinner Menu",
                        "Evening plates with heartier mains.",
                        true,
                        3,
                        List.of(
                                new SectionSpec(
                                        "Starters",
                                        "Warm plates to open the evening.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-CRAB-CAKE", "Crab Cake", "Pan-seared crab cake with lemon aioli.", money("12.50"), null, true, 0, List.of(), List.of(), List.of("Lump Crab Meat", "Breadcrumbs", "Egg", "Lemon Aioli", "Chives")),
                                                new ItemSpec("BIS-BEET-SALAD", "Roasted Beet Salad", "Goat cheese, candied walnuts, and citrus vinaigrette.", money("8.90"), null, true, 1, List.of(), List.of(), List.of("Roasted Beet", "Goat Cheese", "Candied Walnuts", "Arugula", "Citrus Vinaigrette"))
                                        )
                                ),
                                new SectionSpec(
                                        "Mains",
                                        "Heartier evening plates.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-SEA-BASS", "Pan-Seared Sea Bass", "Served with saffron risotto and seasonal vegetables.", money("22.50"), null, true, 0, List.of(), List.of(), List.of("Sea Bass", "Saffron Risotto", "Seasonal Vegetables", "White Wine Butter Sauce")),
                                                new ItemSpec("BIS-SHORT-RIB", "Braised Short Rib", "Slow braised short rib with mashed potatoes and red wine jus.", money("25.00"), null, true, 1, List.of(), List.of(), List.of("Beef Short Rib", "Mashed Potatoes", "Red Wine Jus", "Carrot", "Thyme"))
                                        )
                                )
                        )
                ,
                        LocalTime.of(17, 0),
                        LocalTime.of(23, 0),
                        "#4F6431"
                )
        );
    }

    private static List<MenuSpec> pizzaMenus() {
        return List.of(
                new MenuSpec(
                        "PIZZA_MAIN",
                        "Main Menu",
                        "Mock pizza menu with sizes, toppings, and sides.",
                        true,
                        0,
                        List.of(
                                new SectionSpec(
                                        "Pizzas",
                                        "Signature pies.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("PIZ-MARGHERITA", "Margherita", "San Marzano tomato, fior di latte, basil, and olive oil.", money("10.90"), null, true, 0, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Pizza Size", 0, 1, 1, true),
                                                        new ItemOptionLinkSpec("Pizza Toppings", 1, 0, 6, false)
                                                ), List.of("Pizza Dough", "San Marzano Tomato", "Fior di Latte Mozzarella", "Basil", "Olive Oil")),
                                                new ItemSpec("PIZ-HOT-HONEY", "Hot Honey Pepperoni", "Pepperoni, mozzarella, hot honey, and chili flakes.", money("13.40"), null, true, 1, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Pizza Size", 0, 1, 1, true),
                                                        new ItemOptionLinkSpec("Pizza Toppings", 1, 0, 6, false)
                                                ), List.of("Pizza Dough", "Pepperoni", "Mozzarella", "Hot Honey", "Chili Flakes"))
                                        )
                                ),
                                new SectionSpec(
                                        "Sides",
                                        "Sharable sides and snacks.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("PIZ-GARLIC-KNOTS", "Garlic Knots", "Warm knots brushed with garlic butter and pecorino.", money("5.20"), null, true, 0, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Dip Sauce", 0, 0, 1, false)
                                                ), List.of("Pizza Dough", "Garlic Butter", "Pecorino", "Parsley")),
                                                new ItemSpec("PIZ-CHICKEN-BITES", "Crispy Chicken Bites", "Buttermilk chicken bites with parmesan and herbs.", money("7.60"), null, true, 1, List.of(
                                                        new VariantSpec("Regular", "PIZ-CHICKEN-BITES-R", money("0.00"), true, true, 0),
                                                        new VariantSpec("Large", "PIZ-CHICKEN-BITES-L", money("3.00"), false, true, 1)
                                                ), List.of(
                                                        new ItemOptionLinkSpec("Dip Sauce", 0, 0, 1, false)
                                                ), List.of("Chicken Breast", "Buttermilk", "Breadcrumbs", "Parmesan", "Herbs"))
                                        )
                                ),
                                new SectionSpec(
                                        "Desserts",
                                        "Fast dessert section for testing nested responses.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("PIZ-TIRAMISU-CUP", "Tiramisu Cup", "Single-serve tiramisu in a chilled cup.", money("4.90"), null, true, 0, List.of(), List.of(), List.of("Mascarpone", "Ladyfingers", "Espresso", "Cocoa Powder")),
                                                new ItemSpec("PIZ-NUTELLA-CALZONE", "Nutella Calzone", "Mini dessert calzone with hazelnut spread.", money("6.80"), null, true, 1, List.of(), List.of(), List.of("Pizza Dough", "Hazelnut Spread", "Powdered Sugar"))
                                        )
                                )
                        )
                ,
                        null,
                        null,
                        "#B88945"
                )
        );
    }

    private static List<MenuSpec> cafeMenus() {
        return List.of(
                new MenuSpec(
                        "CAFE_BRUNCH",
                        "Brunch Menu",
                        "Cafe brunch menu with toast, bowls, and coffee drinks.",
                        true,
                        0,
                        List.of(
                                new SectionSpec(
                                        "Coffee",
                                        "Espresso and milk-based drinks.",
                                        0,
                                        true,
                                        List.of(
                                                new ItemSpec("CAF-LATTE", "Vanilla Latte", "Double espresso, vanilla syrup, and textured milk.", money("4.80"), null, true, 0, List.of(
                                                        new VariantSpec("8 oz", "CAF-LATTE-8", money("0.00"), true, true, 0),
                                                        new VariantSpec("12 oz", "CAF-LATTE-12", money("0.90"), false, true, 1)
                                                ), List.of(
                                                        new ItemOptionLinkSpec("Milk Choice", 0, 1, 1, true),
                                                        new ItemOptionLinkSpec("Syrup Shot", 1, 0, 3, false)
                                                ), List.of("Espresso", "Vanilla Syrup", "Steamed Milk")),
                                                new ItemSpec("CAF-MATCHA", "Strawberry Matcha", "Ceremonial matcha, strawberry puree, and milk.", money("5.50"), null, true, 1, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Milk Choice", 0, 1, 1, true)
                                                ), List.of("Ceremonial Matcha", "Strawberry Puree", "Milk"))
                                        )
                                ),
                                new SectionSpec(
                                        "Brunch Plates",
                                        "Toast, bowls, and pastry sets.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("CAF-AVO-TOAST", "Avocado Toast", "Sourdough, whipped feta, avocado, and chili oil.", money("9.80"), null, true, 0, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Toast Side", 0, 0, 1, false)
                                                ), List.of("Sourdough", "Whipped Feta", "Avocado", "Chili Oil", "Lemon")),
                                                new ItemSpec("CAF-GRANOLA", "Berry Granola Bowl", "Greek yogurt, mixed berries, local honey, and toasted granola.", money("7.90"), null, true, 1, List.of(), List.of(), List.of("Greek Yogurt", "Mixed Berries", "Local Honey", "Toasted Granola"))
                                        )
                                ),
                                new SectionSpec(
                                        "Bakery",
                                        "Quick grab-and-go items.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("CAF-CROISSANT", "Butter Croissant", "Classic laminated croissant.", money("2.90"), null, true, 0, List.of(), List.of(), List.of("Flour", "Butter", "Yeast", "Milk")),
                                                new ItemSpec("CAF-BANANA-BREAD", "Banana Bread", "Toasted banana bread with whipped butter.", money("3.60"), null, true, 1, List.of(), List.of(), List.of("Banana", "Flour", "Egg", "Whipped Butter", "Brown Sugar"))
                                        )
                                )
                        )
                ,
                        LocalTime.of(9, 0),
                        LocalTime.of(14, 0),
                        "#B88945"
                )
        );
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    record RestaurantSeedSpec(
            UUID restaurantId,
            String label,
            List<MenuSpec> menus,
            List<OptionGroupSpec> optionGroups
    ) {
    }

    record MenuSpec(
            String code,
            String name,
            String description,
            boolean active,
            int displayOrder,
            List<SectionSpec> sections,
            LocalTime availableFrom,
            LocalTime availableUntil,
            String color
    ) {
    }

    record SectionSpec(
            String name,
            String description,
            int displayOrder,
            boolean active,
            List<ItemSpec> items
    ) {
    }

    record ItemSpec(
            String sku,
            String name,
            String description,
            BigDecimal basePrice,
            String imageUrl,
            boolean available,
            int displayOrder,
            List<VariantSpec> variants,
            List<ItemOptionLinkSpec> optionGroups,
            List<String> ingredients
    ) {
    }

    record VariantSpec(
            String name,
            String sku,
            BigDecimal priceDelta,
            boolean isDefault,
            boolean active,
            int displayOrder
    ) {
    }

    record ItemOptionLinkSpec(
            String optionGroupName,
            int displayOrder,
            Integer minSelectOverride,
            Integer maxSelectOverride,
            Boolean requiredOverride
    ) {
    }

    record OptionGroupSpec(
            OptionGroupType type,
            String name,
            String description,
            Integer minSelect,
            Integer maxSelect,
            boolean required,
            int displayOrder,
            List<OptionItemSpec> items
    ) {
    }

    record OptionItemSpec(
            String code,
            String name,
            BigDecimal priceDelta,
            boolean available,
            int displayOrder
    ) {
    }
}
