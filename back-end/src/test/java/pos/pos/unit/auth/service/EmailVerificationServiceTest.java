package pos.pos.unit.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.ResendVerificationRequest;
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.entity.AuthEmailVerificationToken;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.service.AuthMailService;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.config.properties.EmailVerificationProperties;
import pos.pos.config.properties.FrontendProperties;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService")
class EmailVerificationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @Mock
    private OpaqueTokenService opaqueTokenService;

    @Mock
    private AuthMailService authMailService;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties emailVerificationProperties = new EmailVerificationProperties();
        emailVerificationProperties.setTokenTtl(Duration.ofHours(24));
        emailVerificationProperties.setRequestCooldown(Duration.ofMinutes(5));
        emailVerificationProperties.setTokenPepper("email-verification-pepper");
        emailVerificationProperties.setVerifyPath("/verify-email");
        emailVerificationProperties.setSubject("Verify your POS email");

        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setBaseUrl("https://web.pos.local");
        frontendProperties.setMobileBaseUrl("pos://verify");
        frontendProperties.setUniversalBaseUrl("https://links.pos.local");

        emailVerificationService = new EmailVerificationService(
                userRepository,
                authEmailVerificationTokenRepository,
                opaqueTokenService,
                authMailService,
                emailVerificationProperties,
                frontendProperties
        );
    }

    @Nested
    @DisplayName("resendVerification()")
    class ResendVerificationTests {

        @Test
        @DisplayName("Should resend verification for active unverified user")
        void shouldResendVerificationForActiveUnverifiedUser() {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("  Cashier@POS.local  ");
            request.setClientTarget(ClientLinkTarget.MOBILE);

            User user = activeUnverifiedUser();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authEmailVerificationTokenRepository.existsByUserIdAndCreatedAtAfter(eq(USER_ID), any(OffsetDateTime.class)))
                    .thenReturn(false);
            when(opaqueTokenService.issue("email-verification-pepper"))
                    .thenReturn(new OpaqueTokenService.IssuedToken("verify-token_123", "hashed-token"));

            emailVerificationService.resendVerification(request);

            verify(userRepository).findByEmailAndDeletedAtIsNull("cashier@pos.local");
            verify(authEmailVerificationTokenRepository).deleteByUserId(USER_ID);

            ArgumentCaptor<AuthEmailVerificationToken> tokenCaptor =
                    ArgumentCaptor.forClass(AuthEmailVerificationToken.class);
            verify(authEmailVerificationTokenRepository).save(tokenCaptor.capture());
            AuthEmailVerificationToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(USER_ID);
            assertThat(savedToken.getTokenHash()).isEqualTo("hashed-token");
            assertThat(savedToken.getExpiresAt()).isNotNull();

            verify(authMailService).sendEmailVerificationEmail(
                    "cashier@pos.local",
                    "Casey",
                    "Verify your POS email",
                    "pos://verify/verify-email?token=verify-token_123",
                    Duration.ofHours(24)
            );
        }

        @Test
        @DisplayName("Should do nothing when user does not exist")
        void shouldDoNothingWhenUserDoesNotExist() {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("missing@pos.local");

            when(userRepository.findByEmailAndDeletedAtIsNull("missing@pos.local")).thenReturn(Optional.empty());

            emailVerificationService.resendVerification(request);

            verify(userRepository).findByEmailAndDeletedAtIsNull("missing@pos.local");
            verifyNoInteractions(authEmailVerificationTokenRepository, opaqueTokenService, authMailService);
        }

        @Test
        @DisplayName("Should do nothing when verification cooldown is active")
        void shouldDoNothingWhenVerificationCooldownIsActive() {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("cashier@pos.local");

            User user = activeUnverifiedUser();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authEmailVerificationTokenRepository.existsByUserIdAndCreatedAtAfter(eq(USER_ID), any(OffsetDateTime.class)))
                    .thenReturn(true);

            emailVerificationService.resendVerification(request);

            verify(authEmailVerificationTokenRepository, never()).deleteByUserId(any());
            verifyNoInteractions(opaqueTokenService, authMailService);
        }
    }

    @Nested
    @DisplayName("verifyEmail()")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should mark user verified and token used")
        void shouldMarkUserVerifiedAndTokenUsed() {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("verify-token");

            AuthEmailVerificationToken token = AuthEmailVerificationToken.builder()
                    .userId(USER_ID)
                    .tokenHash("hashed-token")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                    .build();

            User user = activeUnverifiedUser();

            when(opaqueTokenService.hash("verify-token", "email-verification-pepper")).thenReturn("hashed-token");
            when(authEmailVerificationTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            emailVerificationService.verifyEmail(request);

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            verify(userRepository).save(user);

            ArgumentCaptor<AuthEmailVerificationToken> tokenCaptor =
                    ArgumentCaptor.forClass(AuthEmailVerificationToken.class);
            verify(authEmailVerificationTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should only mark token used when user is already verified")
        void shouldOnlyMarkTokenUsedWhenUserIsAlreadyVerified() {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("verify-token");

            AuthEmailVerificationToken token = AuthEmailVerificationToken.builder()
                    .userId(USER_ID)
                    .tokenHash("hashed-token")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                    .build();

            User user = activeUnverifiedUser();
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

            when(opaqueTokenService.hash("verify-token", "email-verification-pepper")).thenReturn("hashed-token");
            when(authEmailVerificationTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            emailVerificationService.verifyEmail(request);

            verify(userRepository, never()).save(any(User.class));
            verify(authEmailVerificationTokenRepository).save(token);
            assertThat(token.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw when verification token does not exist")
        void shouldThrowWhenVerificationTokenDoesNotExist() {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("missing-token");

            when(opaqueTokenService.hash("missing-token", "email-verification-pepper")).thenReturn("hashed-token");
            when(authEmailVerificationTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailVerificationService.verifyEmail(request))
                    .isInstanceOf(InvalidTokenException.class);

            verify(userRepository, never()).findById(any());
            verify(authEmailVerificationTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when token belongs to inactive user")
        void shouldThrowWhenTokenBelongsToInactiveUser() {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("verify-token");

            AuthEmailVerificationToken token = AuthEmailVerificationToken.builder()
                    .userId(USER_ID)
                    .tokenHash("hashed-token")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                    .build();

            User user = activeUnverifiedUser();
            user.setActive(false);

            when(opaqueTokenService.hash("verify-token", "email-verification-pepper")).thenReturn("hashed-token");
            when(authEmailVerificationTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> emailVerificationService.verifyEmail(request))
                    .isInstanceOf(InvalidTokenException.class);

            verify(userRepository, never()).save(any(User.class));
            verify(authEmailVerificationTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("issueVerificationForUser()")
    class IssueVerificationForUserTests {

        @Test
        @DisplayName("Should issue verification email for active unverified user")
        void shouldIssueVerificationEmailForActiveUnverifiedUser() {
            User user = activeUnverifiedUser();

            when(opaqueTokenService.issue("email-verification-pepper"))
                    .thenReturn(new OpaqueTokenService.IssuedToken("issued-token", "hashed-token"));

            emailVerificationService.issueVerificationForUser(user, ClientLinkTarget.WEB);

            verify(authEmailVerificationTokenRepository).deleteByUserId(USER_ID);
            verify(authEmailVerificationTokenRepository).save(any(AuthEmailVerificationToken.class));
            verify(authMailService).sendEmailVerificationEmail(
                    "cashier@pos.local",
                    "Casey",
                    "Verify your POS email",
                    "https://web.pos.local/verify-email?token=issued-token",
                    Duration.ofHours(24)
            );
        }

        @Test
        @DisplayName("Should do nothing for already verified user")
        void shouldDoNothingForAlreadyVerifiedUser() {
            User user = activeUnverifiedUser();
            user.setEmailVerified(true);

            emailVerificationService.issueVerificationForUser(user, ClientLinkTarget.WEB);

            verifyNoInteractions(authEmailVerificationTokenRepository, opaqueTokenService, authMailService);
        }
    }

    private User activeUnverifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .firstName("Casey")
                .lastName("Cashier")
                .passwordHash("stored-hash")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(false)
                .build();
    }
}
