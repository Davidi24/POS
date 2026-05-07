package pos.pos.unit.tables.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.controller.RestaurantTableController;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.service.RestaurantTableService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantTableController")
class RestaurantTableControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000711");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000712");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000713");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000714");

    @Mock
    private RestaurantTableService restaurantTableService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantTableController(restaurantTableService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("tables.owner@pos.local")
                        .username("tables.owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST table should return 201")
    void shouldCreateTable() throws Exception {
        TableRequest request = TableRequest.builder()
                .tableNumber("A1")
                .name("Window")
                .capacity(4)
                .floor("Main")
                .build();

        given(restaurantTableService.createTable(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any(TableRequest.class)))
                .willReturn(TableResponse.builder()
                        .id(TABLE_ID)
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .tableNumber("A1")
                        .name("Window")
                        .capacity(4)
                        .effectiveCapacity(4)
                        .status(TableStatus.AVAILABLE)
                        .active(true)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/tables", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TABLE_ID.toString()))
                .andExpect(jsonPath("$.tableNumber").value("A1"));
    }

    @Test
    @DisplayName("GET tables availability should return availability list")
    void shouldGetTableAvailability() throws Exception {
        given(restaurantTableService.getTableAvailability(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any(), any(), eq(2)))
                .willReturn(List.of(TableAvailabilityResponse.builder()
                        .tableId(TABLE_ID)
                        .tableNumber("A1")
                        .availableForRequestedWindow(false)
                        .blockingReason("RESERVED_FOR_REQUESTED_WINDOW")
                        .build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/tables/availability", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .queryParam("partySize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableId").value(TABLE_ID.toString()))
                .andExpect(jsonPath("$[0].blockingReason").value("RESERVED_FOR_REQUESTED_WINDOW"));
    }
}
