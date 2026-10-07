package pos.pos.sales;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/sales/mine")
public class MySalesController {
    private final MySalesService service;
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public MySalesResponse report(@PathVariable UUID restaurantId,@PathVariable UUID branchId,
        @RequestParam(required=false) LocalDate date,@RequestParam(required=false) UUID staffId,
        @RequestParam(required=false) UUID shiftId,Authentication authentication) {
        return service.report(authentication,restaurantId,branchId,date,staffId,shiftId);
    }
}
