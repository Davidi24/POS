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
import pos.pos.menu.controller.MenuItemOptionGroupController;
import pos.pos.menu.dto.CreateMenuItemOptionGroupRequest;
import pos.pos.menu.dto.MenuItemOptionGroupSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemOptionGroupRequest;
import pos.pos.menu.service.MenuItemOptionGroupService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MenuItemOptionGroupController")
class MenuItemOptionGroupControllerTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID LINK_ID = UUID.fromString("00000000-0000-0000-0000-000000000504");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000505");

    private final StubMenuItemOptionGroupService menuItemOptionGroupService = new StubMenuItemOptionGroupService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MenuItemOptionGroupController(menuItemOptionGroupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("link-admin@pos.local")
                        .username("link-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("GET /menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups should return links")
    void shouldReturnOptionGroupLinks() throws Exception {
        menuItemOptionGroupService.listResponse = List.of(
                MenuItemOptionGroupSummaryResponse.builder()
                        .linkId(LINK_ID)
                        .optionGroupId(GROUP_ID)
                        .name("Sauces")
                        .displayOrder(1)
                        .build()
        );

        mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linkId").value(LINK_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Sauces"));
    }

    @Test
    @DisplayName("POST /menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups should return 201 with the created link")
    void shouldCreateOptionGroupLink() throws Exception {
        CreateMenuItemOptionGroupRequest request = CreateMenuItemOptionGroupRequest.builder()
                .optionGroupId(GROUP_ID)
                .displayOrder(1)
                .build();

        menuItemOptionGroupService.createResponse = MenuItemOptionGroupSummaryResponse.builder()
                .linkId(LINK_ID)
                .optionGroupId(GROUP_ID)
                .name("Sauces")
                .displayOrder(1)
                .build();

        mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups", MENU_ID, SECTION_ID, ITEM_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkId").value(LINK_ID.toString()))
                .andExpect(jsonPath("$.optionGroupId").value(GROUP_ID.toString()));
    }

    @Test
    @DisplayName("PUT /menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups/{linkId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateMenuItemOptionGroupRequest request = UpdateMenuItemOptionGroupRequest.builder()
                .minSelectOverride(1)
                .build();

        mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups/{linkId}",
                        MENU_ID, SECTION_ID, ITEM_ID, LINK_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("displayOrder: displayOrder is required"));
    }

    static class StubMenuItemOptionGroupService extends MenuItemOptionGroupService {

        private List<MenuItemOptionGroupSummaryResponse> listResponse;
        private MenuItemOptionGroupSummaryResponse createResponse;

        StubMenuItemOptionGroupService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public List<MenuItemOptionGroupSummaryResponse> getOptionGroups(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId
        ) {
            return listResponse;
        }

        @Override
        public MenuItemOptionGroupSummaryResponse createOptionGroupLink(
                Authentication authentication,
                UUID menuId,
                UUID sectionId,
                UUID itemId,
                CreateMenuItemOptionGroupRequest request
        ) {
            return createResponse;
        }
    }
}
