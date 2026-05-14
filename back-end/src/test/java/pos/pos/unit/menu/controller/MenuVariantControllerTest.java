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
import pos.pos.menu.controller.MenuVariantController;
import pos.pos.menu.dto.CreateMenuVariantRequest;
import pos.pos.menu.dto.MenuVariantSummaryResponse;
import pos.pos.menu.dto.UpdateMenuVariantRequest;
import pos.pos.menu.service.MenuVariantService;
import pos.pos.security.principal.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MenuVariantController")
class MenuVariantControllerTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000421");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000422");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000423");
    private static final UUID VARIANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000424");

    private final StubMenuVariantService menuVariantService = new StubMenuVariantService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MenuVariantController(menuVariantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("variant-admin@pos.local")
                        .username("variant-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /menus/{menuId}/sections/{sectionId}/items/{itemId}/variants should return 201 with the created variant")
    void shouldCreateVariant() throws Exception {
        CreateMenuVariantRequest request = CreateMenuVariantRequest.builder()
                .name("Large")
                .priceDelta(new BigDecimal("2.50"))
                .isDefault(true)
                .build();

        menuVariantService.createResponse = MenuVariantSummaryResponse.builder()
                .id(VARIANT_ID)
                .name("Large")
                .priceDelta(new BigDecimal("2.50"))
                .isDefault(true)
                .active(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(VARIANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Large"))
                .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    @DisplayName("GET /menus/{menuId}/sections/{sectionId}/items/{itemId}/variants should return variants")
    void shouldReturnVariants() throws Exception {
        menuVariantService.listResponse = List.of(
                MenuVariantSummaryResponse.builder()
                        .id(VARIANT_ID)
                        .name("Large")
                        .build()
        );

        mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Large"));
    }

    @Test
    @DisplayName("PUT /menus/{menuId}/sections/{sectionId}/items/{itemId}/variants/{variantId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateMenuVariantRequest request = UpdateMenuVariantRequest.builder()
                .priceDelta(new BigDecimal("1.00"))
                .build();

        mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants/{variantId}", MENU_ID, SECTION_ID, ITEM_ID, VARIANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name: Name is required")))
                .andExpect(jsonPath("$.message", containsString("active: active is required")))
                .andExpect(jsonPath("$.message", containsString("isDefault: default is required")))
                .andExpect(jsonPath("$.message", containsString("displayOrder: displayOrder is required")));
    }

    static class StubMenuVariantService extends MenuVariantService {

        private List<MenuVariantSummaryResponse> listResponse;
        private MenuVariantSummaryResponse createResponse;

        StubMenuVariantService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public List<MenuVariantSummaryResponse> getVariants(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId
        ) {
            return listResponse;
        }

        @Override
        public MenuVariantSummaryResponse createVariant(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId,
                CreateMenuVariantRequest request
        ) {
            return createResponse;
        }
    }
}
