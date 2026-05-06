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
import pos.pos.menu.controller.OptionGroupController;
import pos.pos.menu.dto.CreateOptionGroupRequest;
import pos.pos.menu.dto.OptionGroupResponse;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.dto.UpdateOptionGroupRequest;
import pos.pos.menu.service.OptionGroupService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OptionGroupController")
class OptionGroupControllerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000461");
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000462");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000463");

    private final StubOptionGroupService optionGroupService = new StubOptionGroupService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new OptionGroupController(optionGroupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("group-admin@pos.local")
                        .username("group-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("GET /option-groups should return option groups")
    void shouldReturnOptionGroups() throws Exception {
        optionGroupService.listResponse = List.of(
                OptionGroupResponse.builder()
                        .id(GROUP_ID)
                        .restaurantId(RESTAURANT_ID)
                        .type(OptionGroupTypeResponse.builder().id(TYPE_ID).name("Single Select").build())
                        .name("Sauces")
                        .build()
        );

        mockMvc.perform(get("/option-groups")
                        .principal(authentication)
                        .param("restaurantId", RESTAURANT_ID.toString())
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Sauces"));
    }

    @Test
    @DisplayName("POST /option-groups should return 201 with the created group")
    void shouldCreateOptionGroup() throws Exception {
        CreateOptionGroupRequest request = CreateOptionGroupRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .typeId(TYPE_ID)
                .name("Sauces")
                .build();

        optionGroupService.createResponse = OptionGroupResponse.builder()
                .id(GROUP_ID)
                .restaurantId(RESTAURANT_ID)
                .type(OptionGroupTypeResponse.builder().id(TYPE_ID).name("Single Select").build())
                .name("Sauces")
                .active(true)
                .displayOrder(0)
                .build();

        mockMvc.perform(post("/option-groups")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.name").value("Sauces"));
    }

    @Test
    @DisplayName("PUT /option-groups/{groupId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateOptionGroupRequest request = UpdateOptionGroupRequest.builder()
                .active(true)
                .displayOrder(0)
                .required(false)
                .build();

                mockMvc.perform(put("/option-groups/{groupId}", GROUP_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name: Name is required")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("typeId: typeId is required")));
    }

    static class StubOptionGroupService extends OptionGroupService {

        private List<OptionGroupResponse> listResponse;
        private OptionGroupResponse createResponse;

        StubOptionGroupService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public List<OptionGroupResponse> getOptionGroups(
                Authentication authentication,
                UUID restaurantId,
                UUID typeId,
                Boolean active,
                String search,
                boolean includeItems
        ) {
            return listResponse;
        }

        @Override
        public OptionGroupResponse createOptionGroup(Authentication authentication, CreateOptionGroupRequest request) {
            return createResponse;
        }
    }
}
