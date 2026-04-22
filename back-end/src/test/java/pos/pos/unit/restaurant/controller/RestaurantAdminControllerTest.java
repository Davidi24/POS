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
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.exception.restaurant.RestaurantManagementNotAllowedException;
import pos.pos.restaurant.controller.RestaurantAdminController;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantAdminService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantAdminController")
class RestaurantAdminControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock
    private RestaurantAdminService restaurantAdminService;

    @InjectMocks
    private RestaurantAdminController controller;

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

        AuthenticatedUser actor = AuthenticatedUser.builder()
                .id(ACTOR_ID)
                .email("owner@pos.local")
                .username("owner")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    @Test
    @DisplayName("GET /restaurants should return paged results")
    void shouldReturnPagedRestaurants() throws Exception {
        RestaurantResponse response = restaurantResponse();
        PageResponse<RestaurantResponse> page = PageResponse.<RestaurantResponse>builder()
                .items(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        given(restaurantAdminService.getRestaurants(
                eq(authentication), eq("pos"), eq(true), eq("ACTIVE"), eq(OWNER_ID), eq(0), eq(20), eq("createdAt"), eq("desc")
        )).willReturn(page);

        mockMvc.perform(get("/restaurants")
                        .principal(authentication)
                        .param("search", "pos")
                        .param("active", "true")
                        .param("status", "ACTIVE")
                        .param("ownerUserId", OWNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.items[0].name").value("POS Main"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /restaurants should return 201")
    void shouldCreateRestaurant() throws Exception {
        CreateRestaurantRequest request = CreateRestaurantRequest.builder()
                .name("POS Main")
                .legalName("POS Main LLC")
                .currency("USD")
                .timezone("Europe/Berlin")
                .ownerUserId(OWNER_ID)
                .build();

        given(restaurantAdminService.createRestaurant(eq(authentication), any(CreateRestaurantRequest.class)))
                .willReturn(restaurantResponse());

        mockMvc.perform(post("/restaurants")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(OWNER_ID.toString()));
    }

    @Test
    @DisplayName("GET /restaurants/{restaurantId} should return 403 when target is not manageable")
    void shouldReturn403WhenTargetIsNotManageable() throws Exception {
        given(restaurantAdminService.getRestaurant(eq(authentication), eq(RESTAURANT_ID)))
                .willThrow(new RestaurantManagementNotAllowedException());

        mockMvc.perform(get("/restaurants/{restaurantId}", RESTAURANT_ID)
                        .principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to manage this restaurant"));
    }

    @Test
    @DisplayName("PUT /restaurants/{restaurantId} should validate the request body")
    void shouldValidateUpdateRestaurantBody() throws Exception {
        UpdateRestaurantRequest request = UpdateRestaurantRequest.builder()
                .name("")
                .legalName("POS Main LLC")
                .code("POS_MAIN")
                .slug("pos-main")
                .currency("USD")
                .timezone("Europe/Berlin")
                .isActive(true)
                .status(RestaurantStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/restaurants/{restaurantId}", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: name is required"));

        verifyNoInteractions(restaurantAdminService);
    }

    @Test
    @DisplayName("PATCH /restaurants/{restaurantId}/status should return updated restaurant")
    void shouldUpdateRestaurantStatus() throws Exception {
        UpdateRestaurantStatusRequest request = new UpdateRestaurantStatusRequest();
        request.setIsActive(false);
        request.setStatus(RestaurantStatus.SUSPENDED);

        RestaurantResponse response = restaurantResponse();
        response.setIsActive(false);
        response.setStatus(RestaurantStatus.SUSPENDED);

        given(restaurantAdminService.updateRestaurantStatus(eq(authentication), eq(RESTAURANT_ID), any(UpdateRestaurantStatusRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/restaurants/{restaurantId}/status", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("DELETE /restaurants/{restaurantId} should return 204")
    void shouldDeleteRestaurant() throws Exception {
        mockMvc.perform(delete("/restaurants/{restaurantId}", RESTAURANT_ID)
                        .principal(authentication))
                .andExpect(status().isNoContent());

        verify(restaurantAdminService).deleteRestaurant(authentication, RESTAURANT_ID);
    }

    private RestaurantResponse restaurantResponse() {
        return RestaurantResponse.builder()
                .id(RESTAURANT_ID)
                .name("POS Main")
                .legalName("POS Main LLC")
                .code("POS_MAIN")
                .slug("pos-main")
                .currency("USD")
                .timezone("Europe/Berlin")
                .isActive(true)
                .status(RestaurantStatus.ACTIVE)
                .ownerUserId(OWNER_ID)
                .createdAt(OffsetDateTime.parse("2026-04-22T10:15:30Z"))
                .updatedAt(OffsetDateTime.parse("2026-04-22T10:15:30Z"))
                .build();
    }
}
