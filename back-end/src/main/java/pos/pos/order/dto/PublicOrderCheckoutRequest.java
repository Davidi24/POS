package pos.pos.order.dto;

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
public class PublicOrderCheckoutRequest {

    @Size(max = 1000, message = "note must be at most 1000 characters")
    private String note;
    private Boolean closeOrder;
}
