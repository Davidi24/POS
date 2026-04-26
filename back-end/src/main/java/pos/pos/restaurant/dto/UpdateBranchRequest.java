package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.BranchStatus;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBranchRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotBlank(message = "code is required")
    @Size(max = 100, message = "code must be at most 100 characters")
    private String code;

    private String description;

    @Email(message = "email must be a valid email")
    @Size(max = 150, message = "email must be at most 150 characters")
    private String email;

    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,50}$", message = "phone must be a valid phone number")
    private String phone;

    private UUID managerUserId;

    @NotNull(message = "isActive is required")
    private Boolean isActive;

    @NotNull(message = "status is required")
    private BranchStatus status;
}
