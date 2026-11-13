package pos.pos.unit.restaurant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.restaurant.controller.RestaurantRegistrationController;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantApplicationReceiptResponse;
import pos.pos.restaurant.dto.RestaurantApplicationResponse;
import pos.pos.restaurant.dto.RestaurantApplicationStatus;
import pos.pos.restaurant.dto.RestaurantApplicationStatusResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.util.ClientInfoExtractor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantRegistrationController")
class RestaurantRegistrationControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private RestaurantRegistrationService restaurantRegistrationService;

    @Mock
    private ClientInfoExtractor clientInfoExtractor;

    @InjectMocks
    private RestaurantRegistrationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("admin@pos.local")
                        .username("admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /restaurants/registrations answers 201 with the private code, using the client's IP for limits")
    void shouldRegisterRestaurant() throws Exception {
        given(clientInfoExtractor.extractIp(any())).willReturn("203.0.113.9");
        given(restaurantRegistrationService.registerRestaurant(any(RestaurantRegistrationRequest.class), eq("203.0.113.9")))
                .willReturn(new RestaurantApplicationReceiptResponse(RESTAURANT_ID, "Burger House", RestaurantApplicationStatus.PENDING,
                        "7KQ2M-X9D4T-PW3HB-R8N6C", "owner@burger.house", OffsetDateTime.parse("2026-10-24T10:00:00Z")));

        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusCode").value("7KQ2M-X9D4T-PW3HB-R8N6C"))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());
    }

    @Test
    @DisplayName("POST /restaurants/registrations without the restaurant's email or phone is a 400 and nothing is saved")
    void shouldRequireContactDetails() throws Exception {
        RestaurantRegistrationRequest noEmail = request();
        noEmail.setEmail(null);
        RestaurantRegistrationRequest blankPhone = request();
        blankPhone.setPhone("  ");

        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noEmail)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankPhone)))
                .andExpect(status().isBadRequest());
        verify(restaurantRegistrationService, never()).registerRestaurant(any(), any());
    }

    @Test
    @DisplayName("POST /restaurants/registrations/status reads the code from the body and shows the rejection reason")
    void shouldCheckStatus() throws Exception {
        given(clientInfoExtractor.extractIp(any())).willReturn("203.0.113.9");
        given(restaurantRegistrationService.getRegistrationStatus("7KQ2M-X9D4T-PW3HB-R8N6C", "203.0.113.9"))
                .willReturn(new RestaurantApplicationStatusResponse(RESTAURANT_ID, "Burger House", RestaurantApplicationStatus.REJECTED,
                        "Missing licence", OffsetDateTime.parse("2026-10-24T10:00:00Z"), OffsetDateTime.parse("2026-10-25T10:00:00Z"), "Not approved"));

        mockMvc.perform(post("/restaurants/registrations/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"7KQ2M-X9D4T-PW3HB-R8N6C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing licence"));
    }

    @Test
    @DisplayName("POST /restaurants/registrations/status without a code is a 400")
    void shouldRequireCode() throws Exception {
        mockMvc.perform(post("/restaurants/registrations/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(restaurantRegistrationService, never()).getRegistrationStatus(any(), any());
    }

    @Test
    @DisplayName("PATCH /restaurants/registrations/{restaurantId}/review should return 200 with the reviewed application")
    void shouldReviewRegistration() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        given(restaurantRegistrationService.reviewRegistration(eq(authentication), eq(RESTAURANT_ID), any(ReviewRestaurantRegistrationRequest.class)))
                .willReturn(application(RestaurantApplicationStatus.APPROVED));

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.owner.username").value("burger.owner"));
    }

    @Test
    @DisplayName("PATCH .../review with a reason over 500 characters is a 400")
    void shouldLimitRejectionReason() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.REJECT);
        request.setRejectionReason("x".repeat(501));

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private RestaurantRegistrationRequest request() {
        return RestaurantRegistrationRequest.builder()
                .name("Burger House")
                .legalName("Burger House LLC")
                .email("hello@burger.house")
                .phone("+1234567890")
                .currency("USD")
                .timezone("America/New_York")
                .owner(CreateRestaurantOwnerRequest.builder()
                        .email("owner@burger.house")
                        .username("burger.owner")
                        .firstName("Burger")
                        .lastName("Owner")
                        .build())
                .build();
    }

    static RestaurantApplicationResponse application(RestaurantApplicationStatus status) {
        return new RestaurantApplicationResponse(RESTAURANT_ID, "Burger House", "Burger House LLC", null, "hello@burger.house",
                "+1234567890", null, "USD", "America/New_York", status, null, OffsetDateTime.parse("2026-10-24T10:00:00Z"),
                OffsetDateTime.parse("2026-10-25T10:00:00Z"), ACTOR_ID,
                new RestaurantApplicationResponse.Applicant("Burger", "Owner", "owner@burger.house", "burger.owner", null));
    }
}
