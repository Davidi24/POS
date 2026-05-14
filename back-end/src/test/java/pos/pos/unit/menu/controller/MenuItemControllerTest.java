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
import pos.pos.menu.controller.MenuItemController;
import pos.pos.menu.dto.CreateMenuItemRequest;
import pos.pos.menu.dto.MenuItemSummaryResponse;
import pos.pos.menu.dto.MenuVariantSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemAvailabilityRequest;
import pos.pos.menu.dto.UpdateMenuItemRequest;
import pos.pos.menu.service.MenuItemService;
import pos.pos.security.principal.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MenuItemController")
class MenuItemControllerTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000391");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000392");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000393");

    private final StubMenuItemService menuItemService = new StubMenuItemService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MenuItemController(menuItemService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("item-admin@pos.local")
                        .username("item-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /menus/{menuId}/sections/{sectionId}/items should return 201 with the created item")
    void shouldCreateItem() throws Exception {
        CreateMenuItemRequest request = CreateMenuItemRequest.builder()
                .name("House Burger")
                .basePrice(new BigDecimal("12.50"))
                .build();

        menuItemService.createResponse = MenuItemSummaryResponse.builder()
                .id(ITEM_ID)
                .name("House Burger")
                .basePrice(new BigDecimal("12.50"))
                .available(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items", MENU_ID, SECTION_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.name").value("House Burger"));
    }

    @Test
    @DisplayName("GET /menus/{menuId}/sections/{sectionId}/items/{itemId} should return item expansions")
    void shouldReturnItemDetail() throws Exception {
        menuItemService.detailResponse = MenuItemSummaryResponse.builder()
                .id(ITEM_ID)
                .name("House Burger")
                .variants(List.of(
                        MenuVariantSummaryResponse.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000394"))
                                .name("Large")
                                .build()
                ))
                .build();

        mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items/{itemId}", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication)
                        .param("includeVariants", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("House Burger"))
                .andExpect(jsonPath("$.variants[0].name").value("Large"));
    }

    @Test
    @DisplayName("PUT /menus/{menuId}/sections/{sectionId}/items/{itemId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateMenuItemRequest request = UpdateMenuItemRequest.builder()
                .available(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name: Name is required")))
                .andExpect(jsonPath("$.message", containsString("basePrice: basePrice is required")));
    }

    @Test
    @DisplayName("PATCH /menus/{menuId}/sections/{sectionId}/items/{itemId}/availability should return the updated item")
    void shouldUpdateItemAvailability() throws Exception {
        UpdateMenuItemAvailabilityRequest request = new UpdateMenuItemAvailabilityRequest();
        request.setAvailable(false);

        menuItemService.availabilityResponse = MenuItemSummaryResponse.builder()
                .id(ITEM_ID)
                .available(false)
                .build();

        mockMvc.perform(patch("/menus/{menuId}/sections/{sectionId}/items/{itemId}/availability", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    static class StubMenuItemService extends MenuItemService {

        private MenuItemSummaryResponse createResponse;
        private MenuItemSummaryResponse detailResponse;
        private MenuItemSummaryResponse availabilityResponse;

        StubMenuItemService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public MenuItemSummaryResponse createItem(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                CreateMenuItemRequest request
        ) {
            return createResponse;
        }

        @Override
        public MenuItemSummaryResponse getItem(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId,
                boolean includeVariants,
                boolean includeOptionGroups
        ) {
            return detailResponse;
        }

        @Override
        public MenuItemSummaryResponse updateItemAvailability(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId,
                UpdateMenuItemAvailabilityRequest request
        ) {
            return availabilityResponse;
        }
    }
}
