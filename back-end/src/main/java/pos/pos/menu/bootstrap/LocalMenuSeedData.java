package pos.pos.menu.bootstrap;

import pos.pos.menu.entity.OptionGroupType;
import pos.pos.restaurant.bootstrap.LocalRestaurantSeedRunner;

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
                                                new ItemSpec("BIS-TRUFFLE-FRIES", "Truffle Fries", "Crispy fries with parmesan, parsley, and black garlic aioli.", money("6.90"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("BIS-BURRATA", "Burrata Plate", "Tomatoes, basil oil, toasted sourdough, and sea salt.", money("9.50"), null, true, 1, List.of(), List.of())
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
                                                        )
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
                                                        )
                                                )
                                        )
                                ),
                                new SectionSpec(
                                        "Desserts",
                                        "Simple dessert section for menu API testing.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-TIRAMISU", "Tiramisu", "Mascarpone cream, coffee sponge, and cocoa.", money("6.40"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("BIS-CHOC-MOUSSE", "Chocolate Mousse", "Dark chocolate mousse with sea salt and berries.", money("5.90"), null, true, 1, List.of(), List.of())
                                        )
                                )
                        )
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
                                                new ItemSpec("BIS-SPRITZ", "Garden Spritz", "Elderflower, citrus, sparkling wine, and mint.", money("8.50"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("BIS-NEGRONI", "Smoked Negroni", "Gin, vermouth, bitter aperitif, and orange zest.", money("9.50"), null, true, 1, List.of(), List.of())
                                        )
                                ),
                                new SectionSpec(
                                        "Zero Proof",
                                        "Non-alcoholic drinks.",
                                        1,
                                        true,
                                        List.of(
                                                new ItemSpec("BIS-HIBISCUS-FIZZ", "Hibiscus Fizz", "Hibiscus cordial, lemon, and sparkling water.", money("5.50"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("BIS-CUCUMBER-TONIC", "Cucumber Tonic", "Cucumber, lime, rosemary, and tonic.", money("5.20"), null, true, 1, List.of(), List.of())
                                        )
                                )
                        )
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
                                                )),
                                                new ItemSpec("PIZ-HOT-HONEY", "Hot Honey Pepperoni", "Pepperoni, mozzarella, hot honey, and chili flakes.", money("13.40"), null, true, 1, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Pizza Size", 0, 1, 1, true),
                                                        new ItemOptionLinkSpec("Pizza Toppings", 1, 0, 6, false)
                                                ))
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
                                                )),
                                                new ItemSpec("PIZ-CHICKEN-BITES", "Crispy Chicken Bites", "Buttermilk chicken bites with parmesan and herbs.", money("7.60"), null, true, 1, List.of(
                                                        new VariantSpec("Regular", "PIZ-CHICKEN-BITES-R", money("0.00"), true, true, 0),
                                                        new VariantSpec("Large", "PIZ-CHICKEN-BITES-L", money("3.00"), false, true, 1)
                                                ), List.of(
                                                        new ItemOptionLinkSpec("Dip Sauce", 0, 0, 1, false)
                                                ))
                                        )
                                ),
                                new SectionSpec(
                                        "Desserts",
                                        "Fast dessert section for testing nested responses.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("PIZ-TIRAMISU-CUP", "Tiramisu Cup", "Single-serve tiramisu in a chilled cup.", money("4.90"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("PIZ-NUTELLA-CALZONE", "Nutella Calzone", "Mini dessert calzone with hazelnut spread.", money("6.80"), null, true, 1, List.of(), List.of())
                                        )
                                )
                        )
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
                                                )),
                                                new ItemSpec("CAF-MATCHA", "Strawberry Matcha", "Ceremonial matcha, strawberry puree, and milk.", money("5.50"), null, true, 1, List.of(), List.of(
                                                        new ItemOptionLinkSpec("Milk Choice", 0, 1, 1, true)
                                                ))
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
                                                )),
                                                new ItemSpec("CAF-GRANOLA", "Berry Granola Bowl", "Greek yogurt, mixed berries, local honey, and toasted granola.", money("7.90"), null, true, 1, List.of(), List.of())
                                        )
                                ),
                                new SectionSpec(
                                        "Bakery",
                                        "Quick grab-and-go items.",
                                        2,
                                        true,
                                        List.of(
                                                new ItemSpec("CAF-CROISSANT", "Butter Croissant", "Classic laminated croissant.", money("2.90"), null, true, 0, List.of(), List.of()),
                                                new ItemSpec("CAF-BANANA-BREAD", "Banana Bread", "Toasted banana bread with whipped butter.", money("3.60"), null, true, 1, List.of(), List.of())
                                        )
                                )
                        )
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
            List<SectionSpec> sections
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
            List<ItemOptionLinkSpec> optionGroups
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
