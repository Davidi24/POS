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
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.restaurant.controller.BranchAddressController;
import pos.pos.restaurant.controller.BranchAdminController;
import pos.pos.restaurant.controller.BranchContactController;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.enums.AddressType;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.ContactType;
import pos.pos.restaurant.service.BranchAddressService;
import pos.pos.restaurant.service.BranchAdminService;
import pos.pos.restaurant.service.BranchContactService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Branch controllers")
class BranchControllersTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID CONTACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Mock
    private BranchAdminService branchAdminService;

    @Mock
    private BranchAddressService branchAddressService;

    @Mock
    private BranchContactService branchContactService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        BranchAdminController branchAdminController = new BranchAdminController(branchAdminService);
        BranchAddressController branchAddressController = new BranchAddressController(branchAddressService);
        BranchContactController branchContactController = new BranchContactController(branchContactService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(branchAdminController, branchAddressController, branchContactController)
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
    @DisplayName("GET branches should return paged branches")
    void shouldGetBranches() throws Exception {
        given(branchAdminService.getBranches(
                eq(authentication), eq(RESTAURANT_ID), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20), eq("createdAt"), eq("desc")
        )).willReturn(PageResponse.<BranchResponse>builder()
                .items(List.of(BranchResponse.builder().id(BRANCH_ID).name("Downtown").status(BranchStatus.ACTIVE).isActive(true).build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branches", RESTAURANT_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(BRANCH_ID.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Downtown"));
    }

    @Test
    @DisplayName("POST branch should return 201")
    void shouldCreateBranch() throws Exception {
        CreateBranchRequest request = CreateBranchRequest.builder()
                .name("Downtown")
                .code("downtown")
                .build();

        given(branchAdminService.createBranch(eq(authentication), eq(RESTAURANT_ID), any(CreateBranchRequest.class)))
                .willReturn(BranchResponse.builder()
                        .id(BRANCH_ID)
                        .name("Downtown")
                        .code("DOWNTOWN")
                        .status(BranchStatus.ACTIVE)
                        .isActive(true)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches", RESTAURANT_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BRANCH_ID.toString()))
                .andExpect(jsonPath("$.code").value("DOWNTOWN"));
    }

    @Test
    @DisplayName("POST branch address should return 201")
    void shouldCreateBranchAddress() throws Exception {
        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .build();

        given(branchAddressService.createAddress(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any(UpsertAddressRequest.class)))
                .willReturn(AddressResponse.builder()
                        .id(ADDRESS_ID)
                        .addressType(AddressType.PHYSICAL)
                        .country("Albania")
                        .city("Tirana")
                        .streetLine1("Main Street")
                        .isPrimary(false)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/addresses", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    @DisplayName("PATCH branch contact primary should return updated contact")
    void shouldMakePrimaryBranchContact() throws Exception {
        given(branchContactService.makePrimary(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), eq(CONTACT_ID)))
                .willReturn(ContactResponse.builder()
                        .id(CONTACT_ID)
                        .contactType(ContactType.MANAGER)
                        .fullName("Manager Name")
                        .isPrimary(true)
                        .build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/contacts/{contactId}/primary", RESTAURANT_ID, BRANCH_ID, CONTACT_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONTACT_ID.toString()))
                .andExpect(jsonPath("$.isPrimary").value(true));
    }
}
