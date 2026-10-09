package pos.pos.unit.kds.controller;

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
import pos.pos.kds.controller.KdsBoardController;
import pos.pos.kds.controller.KdsStationController;
import pos.pos.kds.controller.OrderKdsController;
import pos.pos.kds.dto.KdsStationBoardResponse;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.kds.service.KdsStationCommandService;
import pos.pos.kds.service.KdsStationQueryService;
import pos.pos.kds.service.KdsTicketQueryService;
import pos.pos.kds.service.KdsTicketWorkflowService;
import pos.pos.security.principal.AuthenticatedUser;

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
@DisplayName("KDS controllers")
class KdsControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000001003");
    private static final UUID STATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000001005");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000001006");

    @Mock
    private KdsStationQueryService kdsStationQueryService;
    @Mock
    private KdsStationCommandService kdsStationCommandService;
    @Mock
    private KdsTicketQueryService kdsTicketQueryService;
    @Mock
    private KdsTicketWorkflowService kdsTicketWorkflowService;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new KdsStationController(kdsStationQueryService, kdsStationCommandService),
                        new KdsBoardController(kdsTicketQueryService, kdsTicketWorkflowService),
                        new OrderKdsController(kdsTicketQueryService, kdsTicketWorkflowService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("kds.manager@pos.local")
                        .username("kds.manager")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST KDS station should return 201")
    void shouldCreateKdsStation() throws Exception {
        String request = """
                {
                  "code":"HOT_LINE",
                  "name":"Hot Line",
                  "stationType":"GRILL",
                  "displayOrder":1,
                  "deviceId":"%s",
                  "routings":[
                    {
                      "menuItemId":"%s",
                      "displayOrder":0,
                      "priority":"RUSH"
                    }
                  ]
                }
                """.formatted(DEVICE_ID, ORDER_ID);

        given(kdsStationCommandService.createStation(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any()))
                .willReturn(KdsStationResponse.builder()
                        .id(STATION_ID)
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .deviceId(DEVICE_ID)
                        .code("HOT_LINE")
                        .name("Hot Line")
                        .stationType(KdsStationType.GRILL)
                        .displayOrder(1)
                        .active(true)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/kds/stations", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(STATION_ID.toString()))
                .andExpect(jsonPath("$.code").value("HOT_LINE"))
                .andExpect(jsonPath("$.stationType").value("GRILL"));
    }

    @Test
    @DisplayName("GET KDS display should return the station board")
    void shouldGetKdsDisplay() throws Exception {
        given(kdsTicketQueryService.getDisplay(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), eq(DEVICE_ID), eq(false)))
                .willReturn(KdsStationBoardResponse.builder()
                        .stationId(STATION_ID)
                        .stationCode("HOT_LINE")
                        .stationName("Hot Line")
                        .deviceId(DEVICE_ID)
                        .activeTicketCount(2)
                        .tickets(List.of(KdsTicketResponse.builder().orderId(ORDER_ID).orderNumber("ORD-KDS001").build()))
                        .build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/kds/display", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .param("deviceId", DEVICE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                .andExpect(jsonPath("$.tickets[0].orderNumber").value("ORD-KDS001"));
    }
}
