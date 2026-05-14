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
import pos.pos.menu.controller.OptionItemController;
import pos.pos.menu.dto.CreateOptionItemRequest;
import pos.pos.menu.dto.OptionItemResponse;
import pos.pos.menu.dto.UpdateOptionItemRequest;
import pos.pos.menu.service.OptionItemService;
import pos.pos.security.principal.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OptionItemController")
class OptionItemControllerTest {

    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000471");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000472");

    private final StubOptionItemService optionItemService = new StubOptionItemService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new OptionItemController(optionItemService))
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
    @DisplayName("GET /option-groups/{groupId}/items should return items")
    void shouldReturnOptionItems() throws Exception {
        optionItemService.listResponse = List.of(
                OptionItemResponse.builder()
                        .id(ITEM_ID)
                        .optionGroupId(GROUP_ID)
                        .code("BACON")
                        .name("Bacon")
                        .build()
        );

        mockMvc.perform(get("/option-groups/{groupId}/items", GROUP_ID)
                        .principal(authentication)
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BACON"));
    }

    @Test
    @DisplayName("POST /option-groups/{groupId}/items should return 201 with the created item")
    void shouldCreateOptionItem() throws Exception {
        CreateOptionItemRequest request = CreateOptionItemRequest.builder()
                .name("Bacon")
                .priceDelta(new BigDecimal("1.50"))
                .build();

        optionItemService.createResponse = OptionItemResponse.builder()
                .id(ITEM_ID)
                .optionGroupId(GROUP_ID)
                .code("BACON")
                .name("Bacon")
                .build();

        mockMvc.perform(post("/option-groups/{groupId}/items", GROUP_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.code").value("BACON"));
    }

    @Test
    @DisplayName("PUT /option-groups/{groupId}/items/{itemId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateOptionItemRequest request = UpdateOptionItemRequest.builder()
                .available(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(put("/option-groups/{groupId}/items/{itemId}", GROUP_ID, ITEM_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Name is required"));
    }

    static class StubOptionItemService extends OptionItemService {

        private List<OptionItemResponse> listResponse;
        private OptionItemResponse createResponse;

        StubOptionItemService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<OptionItemResponse> getOptionItems(Authentication authentication, UUID groupId, Boolean available) {
            return listResponse;
        }

        @Override
        public OptionItemResponse createOptionItem(Authentication authentication, UUID groupId, CreateOptionItemRequest request) {
            return createResponse;
        }
    }
}
