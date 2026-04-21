package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.auth.enums.SmsOtpPurpose;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "auth_sms_otp_codes",
        indexes = {
                @Index(name = "idx_auth_sms_otp_codes_user_purpose", columnList = "user_id, purpose"),
                @Index(name = "idx_auth_sms_otp_codes_expires_at", columnList = "expires_at"),
                @Index(name = "idx_auth_sms_otp_codes_used_at", columnList = "used_at")
        }
)
@Check(constraints = "expires_at > created_at AND failed_attempts >= 0")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuthSmsOtpCode {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private SmsOtpPurpose purpose;

    // this is used when someone gets its code we save the phone number he send the code
    // so if he changes the phone number and tries to make the request it can not work
    @Column(name = "phone_number_snapshot", nullable = false, length = 50)
    private String phoneNumberSnapshot;

    @Column(name = "code_hash", nullable = false, columnDefinition = "text")
    private String codeHash;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "timestamptz")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
