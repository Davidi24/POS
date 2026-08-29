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
import pos.pos.menu.controller.MenuSectionController;
import pos.pos.menu.dto.CreateMenuSectionRequest;
import pos.pos.menu.dto.MenuItemSummaryResponse;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.dto.UpdateMenuSectionRequest;
import pos.pos.menu.dto.UpdateMenuSectionStatusRequest;
import pos.pos.menu.service.MenuSectionService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MenuSectionController")
class MenuSectionControllerTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000371");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000372");

    private final StubMenuSectionService menuSectionService = new StubMenuSectionService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MenuSectionController(menuSectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("section-admin@pos.local")
                        .username("section-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /menus/{menuId}/sections should return 201 with the created section")
    void shouldCreateSection() throws Exception {
        CreateMenuSectionRequest request = CreateMenuSectionRequest.builder()
                .name("Mains")
                .description("Main dishes")
                .build();

        menuSectionService.createResponse = MenuSectionSummaryResponse.builder()
                .id(SECTION_ID)
                .name("Mains")
                .description("Main dishes")
                .active(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(post("/menus/{menuId}/sections", MENU_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SECTION_ID.toString()))
                .andExpect(jsonPath("$.name").value("Mains"));
    }

    @Test
    @DisplayName("GET /menus/{menuId}/sections/{sectionId} should return nested items when requested")
    void shouldReturnSectionDetail() throws Exception {
        menuSectionService.detailResponse = MenuSectionSummaryResponse.builder()
                .id(SECTION_ID)
                .name("Mains")
                .items(List.of(
                        MenuItemSummaryResponse.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000373"))
                                .name("House Burger")
                                .build()
                ))
                .build();

        mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}", MENU_ID, SECTION_ID)
                        .principal(authentication)
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mains"))
                .andExpect(jsonPath("$.items[0].name").value("House Burger"));
    }

    @Test
    @DisplayName("PUT /menus/{menuId}/sections/{sectionId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateMenuSectionRequest request = UpdateMenuSectionRequest.builder()
                .active(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}", MENU_ID, SECTION_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Name is required"));
    }

    @Test
    @DisplayName("PATCH /menus/{menuId}/sections/{sectionId}/status should return the updated section")
    void shouldUpdateSectionStatus() throws Exception {
        UpdateMenuSectionStatusRequest request = new UpdateMenuSectionStatusRequest();
        request.setActive(false);

        menuSectionService.statusResponse = MenuSectionSummaryResponse.builder()
                .id(SECTION_ID)
                .active(false)
                .build();

        mockMvc.perform(patch("/menus/{menuId}/sections/{sectionId}/status", MENU_ID, SECTION_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    static class StubMenuSectionService extends MenuSectionService {

        private MenuSectionSummaryResponse createResponse;
        private MenuSectionSummaryResponse detailResponse;
        private MenuSectionSummaryResponse statusResponse;

        StubMenuSectionService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public MenuSectionSummaryResponse createSection(Authentication authentication, UUID menuId, CreateMenuSectionRequest request) {
            return createResponse;
        }

        @Override
        public MenuSectionSummaryResponse getSection(Authentication authentication, UUID menuId, UUID sectionId, boolean includeItems) {
            return detailResponse;
        }

        @Override
        public MenuSectionSummaryResponse updateSectionStatus(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UpdateMenuSectionStatusRequest request
        ) {
            return statusResponse;
        }
    }
}
