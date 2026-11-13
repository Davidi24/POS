package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.restaurant.RestaurantRegistrationNotPendingException;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantApplicationReceiptResponse;
import pos.pos.restaurant.dto.RestaurantApplicationResponse;
import pos.pos.restaurant.dto.RestaurantApplicationStatus;
import pos.pos.restaurant.dto.RestaurantApplicationStatusResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantCardRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantApplicationCodes;
import pos.pos.restaurant.service.RestaurantApplicationMailService;
import pos.pos.restaurant.service.RestaurantOwnerProvisioningService;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.security.service.PublicRequestRateLimiter;
import pos.pos.user.entity.User;
import pos.pos.user.service.UserIdentityService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantRegistrationService")
class RestaurantRegistrationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String IP = "203.0.113.7";

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private RestaurantCardRepository restaurantCardRepository;

    @Mock
    private RestaurantValidationService restaurantValidationService;

    @Mock
    private RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private ActorScopeService actorScopeService;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private PublicRequestRateLimiter rateLimiter;

    @Mock
    private RestaurantApplicationMailService applicationMail;

    @Spy
    private RestaurantApplicationCodes applicationCodes = new RestaurantApplicationCodes("unit-test-application-pepper");

    @Spy
    private RestaurantMapper restaurantMapper = new RestaurantMapper();

    @InjectMocks
    private RestaurantRegistrationService restaurantRegistrationService;

    @BeforeEach
    void enable() {
        ReflectionTestUtils.setField(restaurantRegistrationService, "publicRegistrationEnabled", true);
    }

    // ---- Applying ----

    @Test
    @DisplayName("an application waits as a PENDING restaurant with only the hash of its private code, which goes to the applicant")
    void shouldRegisterPendingApplication() {
        RestaurantRegistrationRequest request = request();
        givenUniqueIdentity();
        given(restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields("Burger House", "America/New_York"))
                .willReturn(new RestaurantValidationService.NormalizedRestaurantFields("BURGER_HOUSE", "burger-house"));
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> {
            Restaurant restaurant = invocation.getArgument(0);
            restaurant.setId(RESTAURANT_ID);
            return restaurant;
        });

        RestaurantApplicationReceiptResponse receipt = restaurantRegistrationService.registerRestaurant(request, IP);

        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(saved.capture());
        Restaurant restaurant = saved.getValue();
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(restaurant.isActive()).isFalse();
        assertThat(restaurant.getOwnerId()).isNull();
        assertThat(restaurant.getEmail()).isEqualTo("hello@burger.house");
        assertThat(restaurant.getPhone()).isEqualTo("+1234567890");
        assertThat(restaurant.getApplicationSubmittedAt()).isNotNull();

        assertThat(receipt.id()).isEqualTo(RESTAURANT_ID);
        assertThat(receipt.status()).isEqualTo(RestaurantApplicationStatus.PENDING);
        assertThat(receipt.statusCode()).matches("[0-9A-Z]{5}(-[0-9A-Z]{5}){3}");
        // Only the hash is stored, never the code itself.
        assertThat(restaurant.getApplicationCodeHash()).isEqualTo(applicationCodes.hash(receipt.statusCode()));
        assertThat(restaurant.getApplicationCodeHash()).doesNotContain(receipt.statusCode());
        verify(applicationMail).received(restaurant, receipt.statusCode());
        verify(rateLimiter).check(eq("APPLY_IP"), eq(IP), eq(5), eq(Duration.ofMinutes(60)), anyString());
        verify(rateLimiter).check(eq("APPLY_EMAIL"), eq("owner@burger.house"), eq(3), eq(Duration.ofHours(24)), anyString());
    }

    @Test
    @DisplayName("too many applications from one place are refused before anything is checked or saved")
    void shouldRefuseRateLimitedApplication() {
        willThrow(new TooManyRequestsException("Too many"))
                .given(rateLimiter).check(eq("APPLY_IP"), eq(IP), anyInt(), any(), anyString());

        assertThatThrownBy(() -> restaurantRegistrationService.registerRestaurant(request(), IP))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(userIdentityService, restaurantValidationService, applicationMail);
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    @DisplayName("applications are refused with 403 when they are switched off")
    void shouldRefuseWhenDisabled() {
        ReflectionTestUtils.setField(restaurantRegistrationService, "publicRegistrationEnabled", false);

        assertThatThrownBy(() -> restaurantRegistrationService.registerRestaurant(request(), IP))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(rateLimiter, restaurantRepository, userIdentityService);
    }

    @Test
    @DisplayName("an unknown currency is refused before the application is saved")
    void shouldRefuseUnknownCurrency() {
        RestaurantRegistrationRequest request = request();
        request.setCurrency("XYZ");

        assertThatThrownBy(() -> restaurantRegistrationService.registerRestaurant(request, IP))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("ISO code")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second waiting application for the same owner email is a conflict")
    void shouldRefuseDuplicatePendingEmail() {
        givenUniqueIdentity();
        given(restaurantRepository.existsByStatusAndPendingOwnerEmailAndDeletedAtIsNull(RestaurantStatus.PENDING, "owner@burger.house"))
                .willReturn(true);

        assertThatThrownBy(() -> restaurantRegistrationService.registerRestaurant(request(), IP))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already waiting for review")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second waiting application for the same username is a conflict")
    void shouldRefuseDuplicatePendingUsername() {
        givenUniqueIdentity();
        given(restaurantRepository.existsByStatusAndPendingOwnerUsernameAndDeletedAtIsNull(RestaurantStatus.PENDING, "burger.owner"))
                .willReturn(true);

        assertThatThrownBy(() -> restaurantRegistrationService.registerRestaurant(request(), IP))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("username")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- Checking on an application ----

    @Test
    @DisplayName("a rejected application shows its reason to whoever has the code")
    void shouldShowRejectionReason() {
        String code = applicationCodes.newCode();
        Restaurant restaurant = pendingRestaurant();
        restaurant.setStatus(RestaurantStatus.REJECTED);
        restaurant.setRejectionReason("We need a business licence");
        restaurant.setReviewedAt(OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        given(restaurantRepository.findByApplicationCodeHash(applicationCodes.hash(code))).willReturn(Optional.of(restaurant));

        // Typed in lower case without dashes, it is still the same code.
        RestaurantApplicationStatusResponse status = restaurantRegistrationService.getRegistrationStatus(
                code.toLowerCase().replace("-", " "), IP);

        assertThat(status.status()).isEqualTo(RestaurantApplicationStatus.REJECTED);
        assertThat(status.rejectionReason()).isEqualTo("We need a business licence");
        assertThat(status.reviewedAt()).isEqualTo(OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        verify(rateLimiter).check(eq("APPLY_STATUS_IP"), eq(IP), eq(30), eq(Duration.ofMinutes(10)), anyString());
    }

    @Test
    @DisplayName("a waiting or approved application shows no reason, and an approved one any later status reads as APPROVED")
    void shouldMapStatuses() {
        String code = applicationCodes.newCode();
        Restaurant restaurant = pendingRestaurant();
        restaurant.setRejectionReason("old reason");
        given(restaurantRepository.findByApplicationCodeHash(applicationCodes.hash(code))).willReturn(Optional.of(restaurant));

        assertThat(restaurantRegistrationService.getRegistrationStatus(code, IP).status()).isEqualTo(RestaurantApplicationStatus.PENDING);
        assertThat(restaurantRegistrationService.getRegistrationStatus(code, IP).rejectionReason()).isNull();
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        RestaurantApplicationStatusResponse approved = restaurantRegistrationService.getRegistrationStatus(code, IP);
        assertThat(approved.status()).isEqualTo(RestaurantApplicationStatus.APPROVED);
        assertThat(approved.message()).contains("invitation");
    }

    @Test
    @DisplayName("an unknown or malformed code is a 404, and a malformed one isn't even looked up")
    void shouldNotFindUnknownCodes() {
        assertThatThrownBy(() -> restaurantRegistrationService.getRegistrationStatus("not a code", IP))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(restaurantRepository, never()).findByApplicationCodeHash(any());

        String unknown = applicationCodes.newCode();
        given(restaurantRepository.findByApplicationCodeHash(applicationCodes.hash(unknown))).willReturn(Optional.empty());
        assertThatThrownBy(() -> restaurantRegistrationService.getRegistrationStatus(unknown, IP))
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("applicants can still check an application after new ones are switched off")
    void shouldCheckStatusWhenDisabled() {
        ReflectionTestUtils.setField(restaurantRegistrationService, "publicRegistrationEnabled", false);
        String code = applicationCodes.newCode();
        given(restaurantRepository.findByApplicationCodeHash(applicationCodes.hash(code))).willReturn(Optional.of(pendingRestaurant()));

        assertThat(restaurantRegistrationService.getRegistrationStatus(code, IP).status()).isEqualTo(RestaurantApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("too many status checks from one place are refused before any lookup")
    void shouldRateLimitStatusChecks() {
        willThrow(new TooManyRequestsException("Too many"))
                .given(rateLimiter).check(eq("APPLY_STATUS_IP"), eq(IP), anyInt(), any(), anyString());

        assertThatThrownBy(() -> restaurantRegistrationService.getRegistrationStatus(applicationCodes.newCode(), IP))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(restaurantRepository);
    }

    // ---- Reviewing ----

    @Test
    @DisplayName("approving creates the owner and a first branch, puts the restaurant last on the list and records the reviewer")
    void shouldApprovePendingApplication() {
        Authentication authentication = authentication();
        Restaurant restaurant = pendingRestaurant();
        given(restaurantScopeService.requireExistingRestaurant(RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantOwnerProvisioningService.createAndInvitePendingOwner(restaurant, ACTOR_ID, "Burger House"))
                .willReturn(User.builder().id(OWNER_ID).restaurantId(RESTAURANT_ID).build());
        given(restaurantCardRepository.nextDisplayOrder()).willReturn(7);
        given(branchRepository.countByRestaurantIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(0L);

        RestaurantApplicationResponse response = restaurantRegistrationService.reviewRegistration(
                authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.APPROVE, null));

        assertThat(response.status()).isEqualTo(RestaurantApplicationStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(ACTOR_ID);
        assertThat(response.owner().email()).isEqualTo("owner@burger.house");
        assertThat(restaurant.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(restaurant.isActive()).isTrue();
        assertThat(restaurant.getDisplayOrder()).isEqualTo(7);
        assertThat(restaurant.getReviewedAt()).isNotNull();
        assertThat(restaurant.getPendingOwnerEmail()).isNull();
        InOrder order = inOrder(restaurantScopeService, restaurantCardRepository);
        order.verify(restaurantScopeService).assertCanCreateRestaurant(authentication);
        order.verify(restaurantCardRepository).lockDisplayOrder();
        order.verify(restaurantCardRepository).nextDisplayOrder();
        ArgumentCaptor<Branch> branch = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(branch.capture());
        assertThat(branch.getValue().getCode()).isEqualTo("MAIN");
        assertThat(branch.getValue().getRestaurant()).isSameAs(restaurant);
        verify(applicationMail, never()).rejected(any());
    }

    @Test
    @DisplayName("approving an application whose restaurant already has a branch doesn't add another")
    void shouldNotAddSecondBranch() {
        Authentication authentication = authentication();
        Restaurant restaurant = pendingRestaurant();
        given(restaurantScopeService.requireExistingRestaurant(RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantOwnerProvisioningService.createAndInvitePendingOwner(restaurant, ACTOR_ID, "Burger House"))
                .willReturn(User.builder().id(OWNER_ID).build());
        given(branchRepository.countByRestaurantIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(1L);

        restaurantRegistrationService.reviewRegistration(authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.APPROVE, null));

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejecting needs a reason; with one, no account is created and the applicant is emailed the reason")
    void shouldRejectWithReason() {
        Authentication authentication = authentication();
        Restaurant restaurant = pendingRestaurant();
        given(restaurantScopeService.requireExistingRestaurant(RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);

        assertThatThrownBy(() -> restaurantRegistrationService.reviewRegistration(
                authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.REJECT, "   ")))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(restaurantRepository, never()).save(any());

        RestaurantApplicationResponse response = restaurantRegistrationService.reviewRegistration(
                authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.REJECT, "  Missing licence "));

        assertThat(response.status()).isEqualTo(RestaurantApplicationStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Missing licence");
        assertThat(restaurant.getReviewedBy()).isEqualTo(ACTOR_ID);
        verify(restaurantOwnerProvisioningService, never()).createAndInvitePendingOwner(any(), any(), any());
        verify(applicationMail).rejected(restaurant);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("only people who may create restaurants review applications")
    void shouldRefuseReviewWithoutCreatePower() {
        Authentication authentication = authentication();
        willThrow(new AuthException("no", HttpStatus.FORBIDDEN)).given(restaurantScopeService).assertCanCreateRestaurant(authentication);

        assertThatThrownBy(() -> restaurantRegistrationService.reviewRegistration(
                authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.APPROVE, null)))
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(restaurantScopeService, never()).requireExistingRestaurant(any());
    }

    @Test
    @DisplayName("an application can be decided only once (409)")
    void shouldRefuseSecondDecision() {
        Authentication authentication = authentication();
        Restaurant restaurant = pendingRestaurant();
        restaurant.setStatus(RestaurantStatus.REJECTED);
        given(restaurantScopeService.requireExistingRestaurant(RESTAURANT_ID)).willReturn(restaurant);

        assertThatThrownBy(() -> restaurantRegistrationService.reviewRegistration(
                authentication, RESTAURANT_ID, review(RestaurantRegistrationDecision.APPROVE, null)))
                .isInstanceOf(RestaurantRegistrationNotPendingException.class)
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(restaurantOwnerProvisioningService, never()).createAndInvitePendingOwner(any(), any(), any());
    }

    // ---- The review queue ----

    @Test
    @DisplayName("the queue lists waiting applications oldest first, only for platform admins")
    void shouldListWaitingApplicationsOldestFirst() {
        Authentication authentication = authentication();
        given(actorScopeService.resolve(authentication)).willReturn(scope(true));
        given(restaurantRepository.searchApplications(eq(List.of(RestaurantStatus.PENDING)), eq("%burger%"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(pendingRestaurant())));

        var page = restaurantRegistrationService.getApplications(authentication, null, "Burger", 0, 20);

        assertThat(page.getItems()).extracting(RestaurantApplicationResponse::name).containsExactly("Burger House");
        assertThat(page.getItems().get(0).owner().username()).isEqualTo("burger.owner");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(restaurantRepository).searchApplications(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("the queue refuses restaurant staff and unknown status filters")
    void shouldGuardQueue() {
        Authentication authentication = authentication();
        given(actorScopeService.resolve(authentication)).willReturn(scope(false));
        assertThatThrownBy(() -> restaurantRegistrationService.getApplications(authentication, "PENDING", null, 0, 20))
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        given(actorScopeService.resolve(authentication)).willReturn(scope(true));
        assertThatThrownBy(() -> restaurantRegistrationService.getApplications(authentication, "ACTIVE", null, 0, 20))
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(restaurantRepository, never()).searchApplications(any(), any(), any());
    }

    @Test
    @DisplayName("one application is found only while it is waiting or rejected")
    void shouldFindOnlyApplications() {
        Authentication authentication = authentication();
        given(actorScopeService.resolve(authentication)).willReturn(scope(true));
        Restaurant approved = pendingRestaurant();
        approved.setStatus(RestaurantStatus.ACTIVE);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(approved));

        assertThatThrownBy(() -> restaurantRegistrationService.getApplication(authentication, RESTAURANT_ID))
                .isInstanceOf(pos.pos.exception.restaurant.RestaurantNotFoundException.class);
    }

    private void givenUniqueIdentity() {
        given(userIdentityService.normalizeAndAssertUnique("Owner@Burger.House", "burger.owner", "+1987654321"))
                .willReturn(new UserIdentityService.NormalizedUserIdentity("owner@burger.house", "burger.owner", "+1987654321"));
    }

    private static ReviewRestaurantRegistrationRequest review(RestaurantRegistrationDecision decision, String reason) {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(decision);
        request.setRejectionReason(reason);
        return request;
    }

    private static ActorScope scope(boolean platformAdmin) {
        return new ActorScope(User.builder().id(ACTOR_ID).build(), platformAdmin, Set.of(), Set.of());
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
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

    private RestaurantRegistrationRequest request() {
        return RestaurantRegistrationRequest.builder()
                .name("Burger House")
                .legalName("Burger House LLC")
                .description("Burger spot")
                .email("hello@burger.house")
                .phone("+1234567890")
                .website("https://burger.house")
                .currency("USD")
                .timezone("America/New_York")
                .owner(CreateRestaurantOwnerRequest.builder()
                        .email("Owner@Burger.House")
                        .username("burger.owner")
                        .firstName("Burger")
                        .lastName("Owner")
                        .phone("+1987654321")
                        .build())
                .build();
    }

    private Restaurant pendingRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Burger House");
        restaurant.setLegalName("Burger House LLC");
        restaurant.setCode("BH001");
        restaurant.setSlug("burger-house");
        restaurant.setEmail("hello@burger.house");
        restaurant.setPhone("+1234567890");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("America/New_York");
        restaurant.setStatus(RestaurantStatus.PENDING);
        restaurant.setActive(false);
        restaurant.setPendingOwnerEmail("owner@burger.house");
        restaurant.setPendingOwnerUsername("burger.owner");
        restaurant.setPendingOwnerFirstName("Burger");
        restaurant.setPendingOwnerLastName("Owner");
        restaurant.setPendingOwnerPhone("+1987654321");
        return restaurant;
    }
}
