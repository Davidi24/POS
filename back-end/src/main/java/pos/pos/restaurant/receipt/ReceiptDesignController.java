package pos.pos.restaurant.receipt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.ReceiptDesignResponse;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.ReceiptPictureResponse;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.SaveReceiptDesignRequest;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/{restaurantId}/receipt-design")
@RequiredArgsConstructor
public class ReceiptDesignController {

    private final ReceiptDesignService service;

    @GetMapping
    // Every till prints receipts with it, so anyone working at the restaurant reads it (2026-11-27); changing it stays the owner's.
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The receipts' design (preliminary bill and fiscal invoice); design null = never saved")
    public ResponseEntity<ReceiptDesignResponse> get(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(service.get(authentication, restaurantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Save the receipts' design (a JSON object up to 256 KB; expectedVersion refuses a stale save with 409)")
    public ResponseEntity<ReceiptDesignResponse> save(@PathVariable UUID restaurantId, @Valid @RequestBody SaveReceiptDesignRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.ok(service.save(authentication, restaurantId, request));
    }

    @PostMapping(value = "/pictures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "A picture for the receipt (PNG, JPEG or WebP, up to 5 MB); removed after a day unless a saved design uses it")
    public ResponseEntity<ReceiptPictureResponse> uploadPicture(@PathVariable UUID restaurantId, @RequestPart("file") MultipartFile file,
                                                                Authentication authentication) {
        return ResponseEntity.ok(service.uploadPicture(authentication, restaurantId, file));
    }
}
