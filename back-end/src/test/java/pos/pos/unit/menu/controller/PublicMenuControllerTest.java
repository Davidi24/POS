package pos.pos.unit.menu.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.menu.controller.PublicMenuController;
import pos.pos.menu.dto.PublicMenuItemResponse;
import pos.pos.menu.dto.PublicMenuResponse;
import pos.pos.menu.dto.PublicMenuSectionResponse;
import pos.pos.menu.service.PublicMenuService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PublicMenuController")
class PublicMenuControllerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000361");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000362");

    private final StubPublicMenuService publicMenuService = new StubPublicMenuService();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMenuController(publicMenuService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /public/restaurants/{restaurantId}/menus should return active menu summaries")
    void shouldReturnPublicMenus() throws Exception {
        publicMenuService.listResponse = List.of(
                PublicMenuResponse.builder()
                        .id(MENU_ID)
                        .code("BREAKFAST")
                        .name("Breakfast")
                        .build()
        );

        mockMvc.perform(get("/public/restaurants/{restaurantId}/menus", RESTAURANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(MENU_ID.toString()))
                .andExpect(jsonPath("$[0].code").value("BREAKFAST"));
    }

    @Test
    @DisplayName("GET /public/restaurants/{restaurantId}/menus/{menuId} should return nested sections and items")
    void shouldReturnExpandedPublicMenu() throws Exception {
        publicMenuService.detailResponse = PublicMenuResponse.builder()
                .id(MENU_ID)
                .code("BREAKFAST")
                .name("Breakfast")
                .sections(List.of(
                        PublicMenuSectionResponse.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000363"))
                                .name("Mains")
                                .items(List.of(
                                        PublicMenuItemResponse.builder()
                                                .id(UUID.fromString("00000000-0000-0000-0000-000000000364"))
                                                .name("House Burger")
                                                .basePrice(new BigDecimal("12.50"))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(get("/public/restaurants/{restaurantId}/menus/{menuId}", RESTAURANT_ID, MENU_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].name").value("Mains"))
                .andExpect(jsonPath("$.sections[0].items[0].name").value("House Burger"))
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    static class StubPublicMenuService extends PublicMenuService {

        private List<PublicMenuResponse> listResponse = List.of();
        private PublicMenuResponse detailResponse;

        StubPublicMenuService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<PublicMenuResponse> getMenus(UUID restaurantId) {
            return listResponse;
        }

        @Override
        public PublicMenuResponse getMenu(UUID restaurantId, UUID menuId, boolean includeSections, boolean includeItems) {
            return detailResponse;
        }
    }
}
