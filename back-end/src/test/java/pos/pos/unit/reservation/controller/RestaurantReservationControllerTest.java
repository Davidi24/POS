package pos.pos.unit.reservation.controller;

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
import pos.pos.reservation.controller.RestaurantReservationController;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.service.ReservationService;
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
@DisplayName("RestaurantReservationController")
class RestaurantReservationControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000812");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000813");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000814");

    @Mock
    private ReservationService reservationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantReservationController(reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("reservation.owner@pos.local")
                        .username("reservation.owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST reservation should return 201")
    void shouldCreateReservation() throws Exception {
        String request = """
                {
                  "branchId":"%s",
                  "partySize":4,
                  "reservationStart":"2026-05-10T18:00:00Z",
                  "reservationEnd":"2026-05-10T20:00:00Z",
                  "contactName":"Alex Stone",
                  "contactPhone":"+49123456789"
                }
                """.formatted(BRANCH_ID);

        given(reservationService.createReservation(eq(authentication), eq(RESTAURANT_ID), any()))
                .willReturn(ReservationResponse.builder()
                        .id(RESERVATION_ID)
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .reservationCode("RES_ABC12345")
                        .status(ReservationStatus.PENDING)
                        .partySize(4)
                        .reservationStart(OffsetDateTime.parse("2026-05-10T18:00:00Z"))
                        .reservationEnd(OffsetDateTime.parse("2026-05-10T20:00:00Z"))
                        .contactName("Alex Stone")
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/reservations", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.reservationCode").value("RES_ABC12345"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
