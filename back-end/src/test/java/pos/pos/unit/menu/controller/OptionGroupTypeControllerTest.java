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
import pos.pos.menu.controller.OptionGroupTypeController;
import pos.pos.menu.dto.CreateOptionGroupTypeRequest;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.dto.UpdateOptionGroupTypeRequest;
import pos.pos.menu.service.OptionGroupTypeService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OptionGroupTypeController")
class OptionGroupTypeControllerTest {

    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000431");

    private final StubOptionGroupTypeService optionGroupTypeService = new StubOptionGroupTypeService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new OptionGroupTypeController(optionGroupTypeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("type-admin@pos.local")
                        .username("type-admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("GET /option-group-types should return option group types")
    void shouldReturnOptionGroupTypes() throws Exception {
        optionGroupTypeService.listResponse = List.of(
                OptionGroupTypeResponse.builder()
                        .id(TYPE_ID)
                        .code("SINGLE_SELECT")
                        .name("Single Select")
                        .build()
        );

        mockMvc.perform(get("/option-group-types")
                        .principal(authentication)
                        .param("search", "single"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SINGLE_SELECT"));
    }

    @Test
    @DisplayName("POST /option-group-types should return 201 with the created type")
    void shouldCreateOptionGroupType() throws Exception {
        CreateOptionGroupTypeRequest request = CreateOptionGroupTypeRequest.builder()
                .name("Multi Select")
                .build();

        optionGroupTypeService.createResponse = OptionGroupTypeResponse.builder()
                .id(TYPE_ID)
                .code("MULTI_SELECT")
                .name("Multi Select")
                .build();

        mockMvc.perform(post("/option-group-types")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TYPE_ID.toString()))
                .andExpect(jsonPath("$.code").value("MULTI_SELECT"));
    }

    @Test
    @DisplayName("PUT /option-group-types/{typeId} should validate the request body")
    void shouldValidateUpdateBody() throws Exception {
        UpdateOptionGroupTypeRequest request = new UpdateOptionGroupTypeRequest();

        mockMvc.perform(put("/option-group-types/{typeId}", TYPE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Name is required"));
    }

    static class StubOptionGroupTypeService extends OptionGroupTypeService {

        private List<OptionGroupTypeResponse> listResponse;
        private OptionGroupTypeResponse createResponse;

        StubOptionGroupTypeService() {
            super(null, null, null);
        }

        @Override
        public List<OptionGroupTypeResponse> getTypes(Authentication authentication, String search) {
            return listResponse;
        }

        @Override
        public OptionGroupTypeResponse createType(Authentication authentication, CreateOptionGroupTypeRequest request) {
            return createResponse;
        }
    }
}
