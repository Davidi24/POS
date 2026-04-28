package pos.pos.unit.restaurant.controller;

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
import pos.pos.restaurant.controller.RestaurantAddressController;
import pos.pos.restaurant.controller.RestaurantBrandingController;
import pos.pos.restaurant.controller.RestaurantContactController;
import pos.pos.restaurant.controller.RestaurantTaxProfileController;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.dto.UpsertRestaurantBrandingRequest;
import pos.pos.restaurant.enums.AddressType;
import pos.pos.restaurant.enums.ContactType;
import pos.pos.restaurant.service.RestaurantAddressService;
import pos.pos.restaurant.service.RestaurantBrandingService;
import pos.pos.restaurant.service.RestaurantContactService;
import pos.pos.restaurant.service.RestaurantTaxProfileService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Restaurant resource controllers")
class RestaurantResourceControllersTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CONTACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID TAX_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Mock
    private RestaurantBrandingService restaurantBrandingService;

    @Mock
    private RestaurantAddressService restaurantAddressService;

    @Mock
    private RestaurantContactService restaurantContactService;

    @Mock
    private RestaurantTaxProfileService restaurantTaxProfileService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        RestaurantBrandingController brandingController = new RestaurantBrandingController(restaurantBrandingService);
        RestaurantAddressController addressController = new RestaurantAddressController(restaurantAddressService);
        RestaurantContactController contactController = new RestaurantContactController(restaurantContactService);
        RestaurantTaxProfileController taxProfileController = new RestaurantTaxProfileController(restaurantTaxProfileService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        brandingController,
                        addressController,
                        contactController,
                        taxProfileController
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("GET branding should return branding details")
    void shouldGetBranding() throws Exception {
        given(restaurantBrandingService.getBranding(eq(authentication), eq(RESTAURANT_ID)))
                .willReturn(RestaurantBrandingResponse.builder()
                        .id(UUID.fromString("00000000-0000-0000-0000-000000000020"))
                        .logoUrl("https://cdn.pos.local/logo.png")
                        .primaryColor("#112233")
                        .build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branding", RESTAURANT_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#112233"));
    }

    @Test
    @DisplayName("PUT branding should validate hex colors")
    void shouldValidateBrandingColor() throws Exception {
        UpsertRestaurantBrandingRequest request = UpsertRestaurantBrandingRequest.builder()
                .primaryColor("blue")
                .build();

        mockMvc.perform(put("/restaurants/{restaurantId}/branding", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("primaryColor: primaryColor must be a valid hex color"));

        verifyNoInteractions(restaurantBrandingService);
    }

    @Test
    @DisplayName("POST address should return 201")
    void shouldCreateAddress() throws Exception {
        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .isPrimary(true)
                .build();

        given(restaurantAddressService.createAddress(eq(authentication), eq(RESTAURANT_ID), any(UpsertAddressRequest.class)))
                .willReturn(AddressResponse.builder()
                        .id(ADDRESS_ID)
                        .addressType(AddressType.PHYSICAL)
                        .country("Albania")
                        .city("Tirana")
                        .streetLine1("Main Street")
                        .isPrimary(true)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/addresses", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$.isPrimary").value(true));
    }

    @Test
    @DisplayName("PATCH contact primary should return updated contact")
    void shouldMakePrimaryContact() throws Exception {
        given(restaurantContactService.makePrimary(eq(authentication), eq(RESTAURANT_ID), eq(CONTACT_ID)))
                .willReturn(ContactResponse.builder()
                        .id(CONTACT_ID)
                        .contactType(ContactType.MANAGER)
                        .fullName("Manager Name")
                        .isPrimary(true)
                        .build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/contacts/{contactId}/primary", RESTAURANT_ID, CONTACT_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONTACT_ID.toString()))
                .andExpect(jsonPath("$.isPrimary").value(true));
    }

    @Test
    @DisplayName("PATCH tax profile default should return updated tax profile")
    void shouldMakeDefaultTaxProfile() throws Exception {
        given(restaurantTaxProfileService.makeDefault(eq(authentication), eq(RESTAURANT_ID), eq(TAX_PROFILE_ID)))
                .willReturn(RestaurantTaxProfileResponse.builder()
                        .id(TAX_PROFILE_ID)
                        .country("Albania")
                        .isDefault(true)
                        .build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/tax-profiles/{taxProfileId}/default", RESTAURANT_ID, TAX_PROFILE_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TAX_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.isDefault").value(true));
    }
}
