package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchCodeAlreadyExistsException;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchStatusRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.mapper.BranchMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.BranchAdminService;
import pos.pos.restaurant.service.BranchValidationService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchAdminService")
class BranchAdminServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private BranchValidationService branchValidationService;

    @Spy
    private BranchMapper branchMapper = new BranchMapper();

    @InjectMocks
    private BranchAdminService branchAdminService;

    @Test
    @DisplayName("createBranch should create an active branch with normalized code")
    void shouldCreateBranch() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        CreateBranchRequest request = CreateBranchRequest.builder()
                .name("Downtown")
                .code("down town")
                .email("branch@pos.local")
                .managerUserId(MANAGER_ID)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchValidationService.normalizeAndValidateFields("down town", "Downtown", RESTAURANT_ID, null))
                .willReturn(new BranchValidationService.NormalizedBranchFields("DOWN_TOWN"));
        given(branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID)).willReturn(MANAGER_ID);
        given(branchRepository.save(any(Branch.class))).willAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(BRANCH_ID);
            branch.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            branch.setUpdatedAt(branch.getCreatedAt());
            return branch;
        });

        BranchResponse response = branchAdminService.createBranch(authentication, RESTAURANT_ID, request);

        assertThat(response.getId()).isEqualTo(BRANCH_ID);
        assertThat(response.getCode()).isEqualTo("DOWN_TOWN");
        assertThat(response.getStatus()).isEqualTo(BranchStatus.ACTIVE);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("createBranch should reject duplicate normalized codes")
    void shouldRejectDuplicateBranchCode() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        CreateBranchRequest request = CreateBranchRequest.builder()
                .name("Downtown")
                .code("DOWN_TOWN")
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchValidationService.normalizeAndValidateFields("DOWN_TOWN", "Downtown", RESTAURANT_ID, null))
                .willThrow(new BranchCodeAlreadyExistsException());

        assertThatThrownBy(() -> branchAdminService.createBranch(authentication, RESTAURANT_ID, request))
                .isInstanceOf(BranchCodeAlreadyExistsException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("getBranches should return a paged response with correct sort and filter")
    void shouldReturnPagedBranches() {
        Authentication authentication = authentication();
        Branch branch = branch();

        given(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(branchRepository.searchRestaurantBranches(
                eq(RESTAURANT_ID),
                eq(true),
                eq(BranchStatus.ACTIVE),
                eq(MANAGER_ID),
                eq("%downtown%"),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(branch)));

        PageResponse<BranchResponse> response = branchAdminService.getBranches(
                authentication,
                RESTAURANT_ID,
                "downtown",
                true,
                "ACTIVE",
                MANAGER_ID,
                0,
                10,
                "name",
                "asc"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(branchRepository).should().searchRestaurantBranches(
                eq(RESTAURANT_ID),
                eq(true),
                eq(BranchStatus.ACTIVE),
                eq(MANAGER_ID),
                eq("%downtown%"),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection().name()).isEqualTo("ASC");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(BRANCH_ID);
    }

    @Test
    @DisplayName("getBranches should reject unsupported sortBy values")
    void shouldRejectUnsupportedBranchSortBy() {
        Authentication authentication = authentication();
        given(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());

        assertThatThrownBy(() -> branchAdminService.getBranches(
                authentication,
                RESTAURANT_ID,
                null, null, null, null,
                0, 20,
                "invalidField",
                "desc"
        ))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid sortBy value");
    }

    @Test
    @DisplayName("getBranch should return the branch for an accessible actor")
    void shouldReturnBranch() {
        Authentication authentication = authentication();
        Branch branch = branch();

        given(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID))
                .willReturn(branch);

        BranchResponse response = branchAdminService.getBranch(authentication, RESTAURANT_ID, BRANCH_ID);

        assertThat(response.getId()).isEqualTo(BRANCH_ID);
        assertThat(response.getName()).isEqualTo("Downtown");
    }

    @Test
    @DisplayName("updateBranch should update fields on the entity")
    void shouldUpdateBranch() {
        Authentication authentication = authentication();
        Branch branch = branch();
        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .name("Uptown")
                .code("UPTOWN")
                .isActive(true)
                .status(BranchStatus.ACTIVE)
                .managerUserId(MANAGER_ID)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID))
                .willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchValidationService.normalizeAndValidateFields("UPTOWN", "Uptown", RESTAURANT_ID, BRANCH_ID))
                .willReturn(new BranchValidationService.NormalizedBranchFields("UPTOWN"));
        given(branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID)).willReturn(MANAGER_ID);
        given(branchRepository.save(branch)).willReturn(branch);

        BranchResponse response = branchAdminService.updateBranch(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(branch.getName()).isEqualTo("Uptown");
        assertThat(branch.getCode()).isEqualTo("UPTOWN");
        assertThat(response.getId()).isEqualTo(BRANCH_ID);
        verify(branchValidationService).validateStatusConsistency(true, BranchStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateBranchStatus should surface validation failures")
    void shouldValidateBranchStatusConsistency() {
        Authentication authentication = authentication();
        Branch branch = branch();
        UpdateBranchStatusRequest request = new UpdateBranchStatusRequest();
        request.setIsActive(true);
        request.setStatus(BranchStatus.TEMPORARILY_CLOSED);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        org.mockito.Mockito.doThrow(new AuthException(
                "Non-active branch statuses must have isActive=false",
                HttpStatus.BAD_REQUEST
        )).when(branchValidationService).validateStatusConsistency(true, BranchStatus.TEMPORARILY_CLOSED);

        assertThatThrownBy(() -> branchAdminService.updateBranchStatus(authentication, RESTAURANT_ID, BRANCH_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Non-active branch statuses must have isActive=false");

        verify(branchValidationService).validateStatusConsistency(true, BranchStatus.TEMPORARILY_CLOSED);
    }

    @Test
    @DisplayName("updateBranchStatus should update status and isActive")
    void shouldUpdateBranchStatus() {
        Authentication authentication = authentication();
        Branch branch = branch();
        UpdateBranchStatusRequest request = new UpdateBranchStatusRequest();
        request.setIsActive(false);
        request.setStatus(BranchStatus.TEMPORARILY_CLOSED);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchRepository.save(branch)).willReturn(branch);

        BranchResponse response = branchAdminService.updateBranchStatus(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(branch.isActive()).isFalse();
        assertThat(branch.getStatus()).isEqualTo(BranchStatus.TEMPORARILY_CLOSED);
        assertThat(response.getId()).isEqualTo(BRANCH_ID);
    }

    @Test
    @DisplayName("deleteBranch should archive and soft-delete the branch")
    void shouldSoftDeleteBranch() {
        Authentication authentication = authentication();
        Branch branch = branch();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);

        branchAdminService.deleteBranch(authentication, RESTAURANT_ID, BRANCH_ID);

        assertThat(branch.isActive()).isFalse();
        assertThat(branch.getStatus()).isEqualTo(BranchStatus.ARCHIVED);
        assertThat(branch.getDeletedAt()).isNotNull();
        assertThat(branch.getUpdatedBy()).isEqualTo(ACTOR_ID);
        verify(branchRepository).save(branch);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
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

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        return restaurant;
    }

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant());
        branch.setName("Downtown");
        branch.setCode("DOWN_TOWN");
        branch.setActive(true);
        branch.setStatus(BranchStatus.ACTIVE);
        return branch;
    }
}
