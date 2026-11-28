package pos.pos.device.network;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.network.DeviceNetworkDtos.AddNetworkRequest;
import pos.pos.device.network.DeviceNetworkDtos.DeviceNetworkView;
import pos.pos.device.network.DeviceNetworkDtos.SetRuleRequest;
import pos.pos.security.util.ClientInfoExtractor;

import java.util.UUID;

/** Settings → Devices & printing → the restaurant's network, per branch (DEVICES_MANAGE). */
@Tag(name = "Devices")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/device-network")
@RequiredArgsConstructor
public class DeviceNetworkController {

    private final DeviceNetworkService service;
    private final ClientInfoExtractor clientInfoExtractor;

    @GetMapping
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "The branch's networks for tills, tablets and screens, and the network you're on")
    public ResponseEntity<DeviceNetworkView> get(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                 Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(service.view(authentication, restaurantId, branchId, clientInfoExtractor.extractIp(request)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "Switch \"Tills only on the restaurant's network\" on or off")
    public ResponseEntity<DeviceNetworkView> setRule(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                     @Valid @RequestBody SetRuleRequest body,
                                                     Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(service.setRule(authentication, restaurantId, branchId, body.onlyOnNetwork(),
                clientInfoExtractor.extractIp(request)));
    }

    @PostMapping("/networks")
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "Save the network you're on, or one a device was turned away from")
    public ResponseEntity<DeviceNetworkView> add(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                 @Valid @RequestBody(required = false) AddNetworkRequest body,
                                                 Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(service.add(authentication, restaurantId, branchId, body == null ? null : body.address(),
                clientInfoExtractor.extractIp(request)));
    }

    @DeleteMapping("/networks/{networkId}")
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "Remove a saved network")
    public ResponseEntity<DeviceNetworkView> remove(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                    @PathVariable UUID networkId,
                                                    Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(service.remove(authentication, restaurantId, branchId, networkId, clientInfoExtractor.extractIp(request)));
    }
}
