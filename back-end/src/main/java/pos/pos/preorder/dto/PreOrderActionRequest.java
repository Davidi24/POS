package pos.pos.preorder.dto;

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
public class PreOrderActionRequest {

    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;
}
