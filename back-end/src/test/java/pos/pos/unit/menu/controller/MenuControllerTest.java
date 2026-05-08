package pos.pos.unit.menu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.menu.controller.MenuController;
import pos.pos.menu.dto.CreateMenuRequest;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.MenuRestaurantSummaryResponse;
import pos.pos.menu.dto.UpdateMenuRequest;
import pos.pos.menu.dto.UpdateMenuStatusRequest;
import pos.pos.menu.service.MenuService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MenuController")
class MenuControllerTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000351");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000352");

    private final StubMenuService menuService = new StubMenuService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MenuController(menuService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("menu-admin@pos.local")
                        .username("menu-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /menus should return 201 with the created menu")
    void shouldCreateMenu() throws Exception {
        CreateMenuRequest request = CreateMenuRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .name("Lunch Specials")
                .description("Day menu")
                .build();

        menuService.createResponse = MenuResponse.builder()
                .id(MENU_ID)
                .code("LUNCH_SPECIALS")
                .name("Lunch Specials")
                .restaurant(MenuRestaurantSummaryResponse.builder().id(RESTAURANT_ID).name("Demo Kitchen").build())
                .build();

        mockMvc.perform(post("/menus")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MENU_ID.toString()))
                .andExpect(jsonPath("$.code").value("LUNCH_SPECIALS"));
    }

    @Test
    @DisplayName("GET /menus/{menuId} should return the expanded menu")
    void shouldReturnMenuDetail() throws Exception {
        menuService.detailResponse = MenuResponse.builder()
                .id(MENU_ID)
                .code("BREAKFAST")
                .name("Breakfast")
                .build();

        mockMvc.perform(get("/menus/{menuId}", MENU_ID)
                        .principal(authentication)
                        .param("includeSections", "true")
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BREAKFAST"));
    }

    @Test
    @DisplayName("PUT /menus/{menuId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateMenuRequest request = UpdateMenuRequest.builder()
                .active(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(put("/menus/{menuId}", MENU_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Name is required"));
    }

    @Test
    @DisplayName("PATCH /menus/{menuId}/status should return the updated menu")
    void shouldUpdateMenuStatus() throws Exception {
        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest();
        request.setActive(false);

        menuService.statusResponse = MenuResponse.builder()
                .id(MENU_ID)
                .active(false)
                .build();

        mockMvc.perform(patch("/menus/{menuId}/status", MENU_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    static class StubMenuService extends MenuService {

        private MenuResponse createResponse;
        private MenuResponse detailResponse;
        private MenuResponse statusResponse;

        StubMenuService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public MenuResponse createMenu(Authentication authentication, CreateMenuRequest request) {
            return createResponse;
        }

        @Override
        public MenuResponse getMenu(Authentication authentication, UUID menuId, boolean includeSections, boolean includeItems) {
            return detailResponse;
        }

        @Override
        public MenuResponse updateMenuStatus(Authentication authentication, UUID menuId, UpdateMenuStatusRequest request) {
            return statusResponse;
        }
    }
}
