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
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    @DisplayName("POST /restaurants/registrations should return 201")
    void shouldRegisterRestaurant() throws Exception {
        RestaurantRegistrationRequest request = request();

        given(restaurantRegistrationService.registerRestaurant(any(RestaurantRegistrationRequest.class)))
                .willReturn(response(RestaurantStatus.PENDING, false, null));

        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /restaurants/registrations/{restaurantId}/review should return 200")
    void shouldReviewRegistration() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        given(restaurantRegistrationService.reviewRegistration(eq(authentication), eq(RESTAURANT_ID), any(ReviewRestaurantRegistrationRequest.class)))
                .willReturn(response(RestaurantStatus.ACTIVE, true, UUID.fromString("00000000-0000-0000-0000-000000000011")));

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    private RestaurantRegistrationRequest request() {
        return RestaurantRegistrationRequest.builder()
                .name("Burger House")
                .legalName("Burger House LLC")
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

    private RestaurantResponse response(RestaurantStatus status, boolean active, UUID ownerUserId) {
        return RestaurantResponse.builder()
                .id(RESTAURANT_ID)
                .name("Burger House")
                .legalName("Burger House LLC")
                .code("BH001")
                .slug("burger-house")
                .currency("USD")
                .timezone("America/New_York")
                .status(status)
                .isActive(active)
                .ownerUserId(ownerUserId)
                .createdAt(OffsetDateTime.parse("2026-04-23T10:15:30Z"))
                .updatedAt(OffsetDateTime.parse("2026-04-23T10:15:30Z"))
                .build();
    }
}
