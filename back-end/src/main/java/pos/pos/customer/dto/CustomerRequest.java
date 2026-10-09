package pos.pos.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

    @Size(max = 50, message = "code must be at most 50 characters")
    private String code;

    @Size(max = 100, message = "firstName must be at most 100 characters")
    private String firstName;

    @Size(max = 100, message = "lastName must be at most 100 characters")
    private String lastName;

    @Size(max = 150, message = "email must be at most 150 characters")
    @jakarta.validation.constraints.Email(message = "email must be a valid email address")
    private String email;

    @Size(max = 50, message = "phone must be at most 50 characters")
    private String phone;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;

    private Boolean active;
}
