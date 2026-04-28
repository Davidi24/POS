package pos.pos.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.ContactType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {

    private UUID id;
    private ContactType contactType;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isPrimary;
    private String jobTitle;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
