package pos.pos.unit.order.controller;

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
import pos.pos.order.controller.BranchOrderController;
import pos.pos.order.controller.PublicOrderController;
import pos.pos.order.controller.RestaurantOrderController;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.service.OrderPublicService;
import pos.pos.order.service.OrderService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order controllers")
class OrderControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000912");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000913");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000914");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000915");

    @Mock
    private OrderService orderService;
    @Mock
    private OrderPublicService orderPublicService;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RestaurantOrderController(orderService),
                        new BranchOrderController(orderService),
                        new PublicOrderController(orderPublicService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("orders.owner@pos.local")
                        .username("orders.owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST branch order should return 201")
    void shouldCreateBranchOrder() throws Exception {
        String request = """
                {
                  "guestCount":3,
                  "orderType":"DINE_IN",
                  "source":"POS",
                  "notes":"Dinner table order",
                  "items":[
                    {
                      "menuItemId":"%s",
                      "quantity":2
                    }
                  ]
                }
                """.formatted(TABLE_ID);

        given(orderService.createBranchOrder(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any()))
                .willReturn(OrderResponse.builder()
                        .id(ORDER_ID)
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .orderNumber("ORD-TEST001")
                        .currency("USD")
                        .orderType(OrderType.DINE_IN)
                        .source(OrderSource.POS)
                        .status(OrderStatus.OPEN)
                        .paymentStatus(OrderPaymentStatus.UNPAID)
                        .guestCount(3)
                        .openedAt(OffsetDateTime.parse("2026-05-12T12:00:00Z"))
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/orders", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-TEST001"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
