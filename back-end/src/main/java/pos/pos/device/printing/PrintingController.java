package pos.pos.device.printing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Where things print, what a till needs to print, and prints left for the device a printer is plugged into (2026-11-27). */
@Tag(name = "Printing")
@Validated
@RestController
@RequiredArgsConstructor
public class PrintingController {

    private final PrintingService service;

    @GetMapping("/restaurants/{restaurantId}/printers/{printerId}/jobs")
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'DEVICES_MANAGE')")
    @Operation(summary = "What this printer prints: the tills whose receipts and the kitchen stations whose tickets")
    public PrintingDtos.PrinterJobs jobs(@PathVariable UUID restaurantId, @PathVariable UUID printerId, Authentication authentication) {
        return service.jobs(authentication, restaurantId, printerId);
    }

    @PutMapping("/restaurants/{restaurantId}/printers/{printerId}/jobs")
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "Choose what this printer prints; a till or station chosen here leaves the printer it was on")
    public PrintingDtos.PrinterJobs setJobs(@PathVariable UUID restaurantId, @PathVariable UUID printerId,
                                            @Valid @RequestBody PrintingDtos.SetPrinterJobsRequest request, Authentication authentication) {
        return service.setJobs(authentication, restaurantId, printerId, request);
    }

    @GetMapping("/restaurants/{restaurantId}/print-setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "What the device you're on prints where, and when things print by themselves")
    public PrintingDtos.Setup setup(@PathVariable UUID restaurantId, @RequestParam(required = false) UUID deviceId, Authentication authentication) {
        return service.setup(authentication, restaurantId, deviceId);
    }

    @PostMapping("/restaurants/{restaurantId}/printers/{printerId}/queue")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Leave a print for the device this printer is plugged into")
    public PrintingDtos.QueuedPrint queue(@PathVariable UUID restaurantId, @PathVariable UUID printerId,
                                          @Valid @RequestBody PrintingDtos.QueueRequest request, Authentication authentication) {
        return service.queue(authentication, restaurantId, printerId, request);
    }

    @GetMapping("/restaurants/{restaurantId}/print-queue/{id}")
    @PreAuthorize("isAuthenticated()")
    public PrintingDtos.QueuedPrint queued(@PathVariable UUID restaurantId, @PathVariable UUID id, Authentication authentication) {
        return service.queued(authentication, restaurantId, id);
    }

    @GetMapping("/public/devices/{deviceId}/printer-checks")
    @Operation(summary = "The printers this till checks once a minute (its device secret proves it)")
    public List<PrintingDtos.PrinterToCheck> printersToCheck(@PathVariable UUID deviceId,
                                                             @RequestHeader(value = "X-Device-Secret", required = false) String secret) {
        return service.printersToCheck(deviceId, secret);
    }

    @PostMapping("/public/devices/{deviceId}/printer-checks")
    @Operation(summary = "Which printers this till reached")
    public ResponseEntity<Void> printerChecks(@PathVariable UUID deviceId, @RequestHeader(value = "X-Device-Secret", required = false) String secret,
                                              @Valid @RequestBody PrintingDtos.PrinterChecksRequest request) {
        service.printerChecks(deviceId, secret, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/devices/{deviceId}/print-queue")
    @Operation(summary = "The prints waiting for this device's printers (the device's own secret)")
    public List<PrintingDtos.HostJob> hostJobs(@PathVariable UUID deviceId, @RequestHeader(value = "X-Device-Secret", required = false) String secret) {
        return service.hostJobs(deviceId, secret);
    }

    @PostMapping("/public/devices/{deviceId}/print-queue/{id}")
    public ResponseEntity<Void> hostResult(@PathVariable UUID deviceId, @PathVariable UUID id,
                                           @RequestHeader(value = "X-Device-Secret", required = false) String secret,
                                           @Valid @RequestBody PrintingDtos.HostResult result) {
        service.hostResult(deviceId, secret, id, result);
        return ResponseEntity.noContent().build();
    }
}
