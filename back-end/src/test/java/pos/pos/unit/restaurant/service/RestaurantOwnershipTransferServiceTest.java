package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.entity.UserSession;
import pos.pos.exception.auth.AuthException;
import pos.pos.mail.EmailKind;
import pos.pos.mail.EmailOutboxService;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantOwnerTransferResponse;
import pos.pos.restaurant.dto.TransferRestaurantOwnerRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantOwnerProvisioningService;
import pos.pos.restaurant.service.RestaurantOwnershipTransferService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RestaurantOwnershipTransferService")
class RestaurantOwnershipTransferServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OTHER_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID PREVIOUS_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID NEW_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID OWNER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID MANAGER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Mock private JdbcTemplate jdbc;
    @Mock private RestaurantScopeService restaurantScopeService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private RestaurantOwnerProvisioningService provisioningService;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RoleHierarchyService roleHierarchyService;
    @Mock private ActorScopeService actorScopeService;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private AuthPasswordResetTokenRepository passwordLinks;
    @Mock private EmailOutboxService outbox;
    @Mock private SettingsAuditService auditService;

    private RestaurantOwnershipTransferService service;
    private Authentication authentication;
    private Restaurant restaurant;
    private User previousOwner;
    private User newOwner;

    @BeforeEach
    void setUp() {
        service = new RestaurantOwnershipTransferService(jdbc, "public", restaurantScopeService, restaurantRepository, branchRepository,
                provisioningService, userRepository, userRoleRepository, roleRepository, roleHierarchyService, actorScopeService,
                sessionRepository, passwordLinks, outbox, auditService);
        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder().id(ACTOR_ID).email("admin@pos.local").username("admin").active(true).build(), null, List.of());
        restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Burger House");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setActive(true);
        restaurant.setVersion(4);
        restaurant.setOwnerId(PREVIOUS_OWNER_ID);
        previousOwner = person(PREVIOUS_OWNER_ID, RESTAURANT_ID, "Old");
        newOwner = person(NEW_OWNER_ID, RESTAURANT_ID, "New");

        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(true);
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantScopeService.requireExistingRestaurant(RESTAURANT_ID)).willReturn(restaurant);
        given(userRepository.findByIdAndDeletedAtIsNull(PREVIOUS_OWNER_ID)).willReturn(Optional.of(previousOwner));
        given(userRepository.findActiveByIdForUpdate(PREVIOUS_OWNER_ID)).willReturn(Optional.of(previousOwner));
        given(userRepository.findActiveByIdForUpdate(NEW_OWNER_ID)).willReturn(Optional.of(newOwner));
        given(roleRepository.findByCode("OWNER")).willReturn(Optional.of(Role.builder().id(OWNER_ROLE_ID).code("OWNER").build()));
        given(roleRepository.findActiveRoleCodesByUserId(any())).willReturn(List.of("MANAGER"));
        given(userRoleRepository.findByUserId(PREVIOUS_OWNER_ID)).willReturn(List.of(
                UserRole.builder().id(UUID.randomUUID()).userId(PREVIOUS_OWNER_ID).roleId(OWNER_ROLE_ID).build(),
                UserRole.builder().id(UUID.randomUUID()).userId(PREVIOUS_OWNER_ID).roleId(MANAGER_ROLE_ID).build()));
        given(sessionRepository.findActiveSessionsByUserId(eq(PREVIOUS_OWNER_ID), any()))
                .willReturn(List.of(new UserSession(), new UserSession()));
        given(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).willAnswer(invocation -> List.of(
                new RestaurantOwnerTransferResponse(UUID.randomUUID(), RESTAURANT_ID, PREVIOUS_OWNER_ID, "Old Owner", restaurant.getOwnerId(),
                        "New Owner", false, true, 2, null, ACTOR_ID, "Super Admin", OffsetDateTime.now())));
    }

    @Test
    @DisplayName("giving the restaurant to a staff member makes them owner and fully removes the former owner's access")
    void shouldTransferToExistingStaff() {
        RestaurantOwnerTransferResponse response = service.transfer(authentication, RESTAURANT_ID,
                new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, "  Sold the business "));

        assertThat(restaurant.getOwnerId()).isEqualTo(NEW_OWNER_ID);
        assertThat(restaurant.getUpdatedBy()).isEqualTo(ACTOR_ID);
        assertThat(response.newOwnerId()).isEqualTo(NEW_OWNER_ID);
        // The new owner gets the OWNER role.
        ArgumentCaptor<UserRole> granted = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(granted.capture());
        assertThat(granted.getValue().getUserId()).isEqualTo(NEW_OWNER_ID);
        assertThat(granted.getValue().getRoleId()).isEqualTo(OWNER_ROLE_ID);
        // The former owner: only the OWNER role goes, the account is switched off and they are signed out everywhere.
        ArgumentCaptor<UserRole> removed = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).delete(removed.capture());
        assertThat(removed.getValue().getRoleId()).isEqualTo(OWNER_ROLE_ID);
        assertThat(previousOwner.isActive()).isFalse();
        assertThat(previousOwner.getStatus()).isEqualTo("INACTIVE");
        assertThat(previousOwner.getDefaultBranchId()).isNull();
        verify(sessionRepository).revokeAllActiveSessionsByUserId(eq(PREVIOUS_OWNER_ID), any(), eq("OWNERSHIP_TRANSFERRED"));
        verify(passwordLinks).supersedeOpenLinks(eq(PREVIOUS_OWNER_ID), any());
        verify(outbox).cancelAccountEmails(eq(PREVIOUS_OWNER_ID), anyString());
        verify(branchRepository).clearManager(eq(RESTAURANT_ID), eq(PREVIOUS_OWNER_ID), eq(ACTOR_ID), any());
        // Recorded: the transfer row (with the trimmed reason and 2 sessions), the audit line and an email to the former owner.
        ArgumentCaptor<Object[]> row = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("insert into"), row.capture());
        assertThat(values(row)).containsSubsequence(RESTAURANT_ID, PREVIOUS_OWNER_ID, NEW_OWNER_ID, false, true, 2, "Sold the business", ACTOR_ID);
        verify(auditService).log(eq(restaurant), eq(null), eq("RESTAURANT"), eq(RESTAURANT_ID), eq("OWNER_TRANSFER"),
                contains("from Old Owner to New Owner"), eq(ACTOR_ID));
        ArgumentCaptor<EmailOutboxService.OutgoingEmail> email = ArgumentCaptor.forClass(EmailOutboxService.OutgoingEmail.class);
        verify(outbox).enqueue(email.capture());
        assertThat(email.getValue().kind()).isEqualTo(EmailKind.OWNERSHIP_TRANSFERRED);
        assertThat(email.getValue().to()).isEqualTo(previousOwner.getEmail());
        verify(restaurantRepository).saveAndFlush(restaurant);
    }

    @Test
    @DisplayName("giving the restaurant to a new person invites them through the usual owner invitation")
    void shouldTransferToInvitedPerson() {
        CreateRestaurantOwnerRequest invitee = CreateRestaurantOwnerRequest.builder()
                .email("new@owner.example").username("new.owner").firstName("New").lastName("Owner").build();
        given(provisioningService.createAndInviteOwner(invitee, RESTAURANT_ID, ACTOR_ID, "Burger House")).willReturn(newOwner);

        service.transfer(authentication, RESTAURANT_ID, new TransferRestaurantOwnerRequest(null, invitee, 4L, null));

        assertThat(restaurant.getOwnerId()).isEqualTo(NEW_OWNER_ID);
        verify(provisioningService).createAndInviteOwner(invitee, RESTAURANT_ID, ACTOR_ID, "Burger House");
        verify(userRoleRepository, never()).save(any());
        ArgumentCaptor<Object[]> row = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("insert into"), row.capture());
        assertThat(values(row)).containsSubsequence(NEW_OWNER_ID, true, true);
        assertThat(previousOwner.isActive()).isFalse();
    }

    @Test
    @DisplayName("only the Super Admin transfers ownership; Co-Super-Admins and owners get 403 and nothing changes")
    void shouldRefuseOthers() {
        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(false);

        assertThatThrownBy(() -> service.transfer(authentication, RESTAURANT_ID, new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null)))
                .isInstanceOfSatisfying(AuthException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(restaurantScopeService).requireAccessibleRestaurant(authentication, RESTAURANT_ID);
        verify(restaurantScopeService, never()).requireExistingRestaurant(any());
        verifyNoInteractions(sessionRepository, userRoleRepository, provisioningService);
    }

    @Test
    @DisplayName("exactly one new owner must be chosen")
    void shouldNeedExactlyOneNewOwner() {
        CreateRestaurantOwnerRequest invitee = CreateRestaurantOwnerRequest.builder().email("x@y.z").username("xyz").firstName("X").lastName("Y").build();

        assertStatus(new TransferRestaurantOwnerRequest(null, null, 4L, null), HttpStatus.BAD_REQUEST);
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, invitee, 4L, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a stale version, an unapproved restaurant or one without an owner is a conflict")
    void shouldRefuseConflicts() {
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 3L, null), HttpStatus.CONFLICT);

        restaurant.setStatus(RestaurantStatus.PENDING);
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null), HttpStatus.CONFLICT);

        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setOwnerId(null);
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null), HttpStatus.CONFLICT);
        verify(sessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), any());
    }

    @Test
    @DisplayName("the new owner must be someone else, active, from this restaurant, not a platform admin and not an owner elsewhere")
    void shouldValidateExistingNewOwner() {
        assertStatus(new TransferRestaurantOwnerRequest(PREVIOUS_OWNER_ID, null, 4L, null), HttpStatus.BAD_REQUEST);

        UUID inactive = UUID.randomUUID();
        given(userRepository.findActiveByIdForUpdate(inactive)).willReturn(Optional.empty());
        assertStatus(new TransferRestaurantOwnerRequest(inactive, null, 4L, null), HttpStatus.BAD_REQUEST);

        given(roleRepository.findActiveRoleCodesByUserId(NEW_OWNER_ID)).willReturn(List.of("CO_SUPER_ADMIN"));
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null), HttpStatus.BAD_REQUEST);

        given(roleRepository.findActiveRoleCodesByUserId(NEW_OWNER_ID)).willReturn(List.of("MANAGER"));
        newOwner.setRestaurantId(OTHER_RESTAURANT_ID);
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null), HttpStatus.CONFLICT);

        newOwner.setRestaurantId(RESTAURANT_ID);
        given(restaurantRepository.existsByOwnerIdAndIdNotAndDeletedAtIsNull(NEW_OWNER_ID, RESTAURANT_ID)).willReturn(true);
        assertStatus(new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null), HttpStatus.CONFLICT);

        assertThat(restaurant.getOwnerId()).isEqualTo(PREVIOUS_OWNER_ID);
        assertThat(previousOwner.isActive()).isTrue();
        verify(sessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), any());
    }

    @Test
    @DisplayName("a former owner who is a platform admin only stops owning the restaurant; their account stays as it is")
    void shouldKeepPlatformAdminFormerOwner() {
        given(roleRepository.findActiveRoleCodesByUserId(PREVIOUS_OWNER_ID)).willReturn(List.of("SUPER_ADMIN"));

        service.transfer(authentication, RESTAURANT_ID, new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null));

        assertThat(restaurant.getOwnerId()).isEqualTo(NEW_OWNER_ID);
        assertThat(previousOwner.isActive()).isTrue();
        verify(sessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), any());
        verify(userRoleRepository, never()).delete(any());
        verify(outbox, never()).enqueue(any());
        ArgumentCaptor<Object[]> row = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("insert into"), row.capture());
        assertThat(values(row)).containsSubsequence(false, false, 0);
    }

    @Test
    @DisplayName("a former owner who still owns another restaurant keeps their account")
    void shouldKeepOwnerOfAnotherRestaurant() {
        given(restaurantRepository.existsByOwnerIdAndIdNotAndDeletedAtIsNull(PREVIOUS_OWNER_ID, RESTAURANT_ID)).willReturn(true);

        service.transfer(authentication, RESTAURANT_ID, new TransferRestaurantOwnerRequest(NEW_OWNER_ID, null, 4L, null));

        assertThat(previousOwner.isActive()).isTrue();
        verify(sessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), any());
    }

    private void assertStatus(TransferRestaurantOwnerRequest request, HttpStatus status) {
        assertThatThrownBy(() -> service.transfer(authentication, RESTAURANT_ID, request))
                .isInstanceOfSatisfying(AuthException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
    }

    // The captured insert parameters, whether Mockito hands back the varargs array or its elements.
    private static List<Object> values(ArgumentCaptor<Object[]> captor) {
        List<Object> all = new java.util.ArrayList<>();
        for (Object value : captor.getAllValues()) {
            if (value instanceof Object[] array) all.addAll(java.util.Arrays.asList(array)); else all.add(value);
        }
        return all;
    }

    private static User person(UUID id, UUID restaurantId, String firstName) {
        return User.builder()
                .id(id)
                .email(firstName.toLowerCase() + "@owner.example")
                .username(firstName.toLowerCase() + ".owner")
                .firstName(firstName)
                .lastName("Owner")
                .status("ACTIVE")
                .isActive(true)
                .restaurantId(restaurantId)
                .defaultBranchId(UUID.randomUUID())
                .build();
    }
}
