package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.CreateOptionGroupRequest;
import pos.pos.menu.dto.OptionGroupResponse;
import pos.pos.menu.dto.UpdateOptionGroupRequest;
import pos.pos.menu.dto.UpdateOptionGroupStatusRequest;
import pos.pos.menu.service.OptionGroupService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Option Groups")
@RestController
@RequestMapping("/option-groups")
@RequiredArgsConstructor
public class OptionGroupController {

    private final OptionGroupService optionGroupService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List option groups for one restaurant")
    public ResponseEntity<List<OptionGroupResponse>> getOptionGroups(
            Authentication authentication,
            @RequestParam UUID restaurantId,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeItems
    ) {
        return ResponseEntity.ok(optionGroupService.getOptionGroups(
                authentication,
                restaurantId,
                typeId,
                active,
                search,
                includeItems
        ));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "Get one option group")
    public ResponseEntity<OptionGroupResponse> getOptionGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "false") boolean includeItems
    ) {
        return ResponseEntity.ok(optionGroupService.getOptionGroup(authentication, groupId, includeItems));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create an option group")
    public ResponseEntity<OptionGroupResponse> createOptionGroup(
            Authentication authentication,
            @Valid @RequestBody CreateOptionGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupService.createOptionGroup(authentication, request));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update an option group")
    public ResponseEntity<OptionGroupResponse> updateOptionGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateOptionGroupRequest request
    ) {
        return ResponseEntity.ok(optionGroupService.updateOptionGroup(authentication, groupId, request));
    }

    @PatchMapping("/{groupId}/status")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update only the active state of an option group")
    public ResponseEntity<OptionGroupResponse> updateOptionGroupStatus(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateOptionGroupStatusRequest request
    ) {
        return ResponseEntity.ok(optionGroupService.updateOptionGroupStatus(authentication, groupId, request));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete an option group")
    public ResponseEntity<Void> deleteOptionGroup(
            Authentication authentication,
            @PathVariable UUID groupId
    ) {
        optionGroupService.deleteOptionGroup(authentication, groupId);
        return ResponseEntity.noContent().build();
    }
}
