package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.ContactType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertContactRequest {

    @NotNull(message = "contactType is required")
    private ContactType contactType;

    @NotBlank(message = "fullName is required")
    @Size(max = 150, message = "fullName must be at most 150 characters")
    private String fullName;

    @Email(message = "email must be a valid email")
    @Size(max = 150, message = "email must be at most 150 characters")
    private String email;

    @Size(max = 50, message = "phone must be at most 50 characters")
    private String phone;

    private Boolean isPrimary;

    @Size(max = 100, message = "jobTitle must be at most 100 characters")
    private String jobTitle;
}
