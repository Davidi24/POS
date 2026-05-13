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
import pos.pos.menu.dto.CreateOptionItemRequest;
import pos.pos.menu.dto.OptionItemResponse;
import pos.pos.menu.dto.UpdateOptionItemAvailabilityRequest;
import pos.pos.menu.dto.UpdateOptionItemRequest;
import pos.pos.menu.service.OptionItemService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Option Items")
@RestController
@RequestMapping("/option-groups/{groupId}/items")
@RequiredArgsConstructor
public class OptionItemController {

    private final OptionItemService optionItemService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List items for one option group")
    public ResponseEntity<List<OptionItemResponse>> getOptionItems(
            Authentication authentication,
            @PathVariable UUID groupId,
            @RequestParam(required = false) Boolean available
    ) {
        return ResponseEntity.ok(optionItemService.getOptionItems(authentication, groupId, available));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create an item under an option group")
    public ResponseEntity<OptionItemResponse> createOptionItem(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateOptionItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionItemService.createOptionItem(authentication, groupId, request));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update an option item")
    public ResponseEntity<OptionItemResponse> updateOptionItem(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateOptionItemRequest request
    ) {
        return ResponseEntity.ok(optionItemService.updateOptionItem(authentication, groupId, itemId, request));
    }

    @PatchMapping("/{itemId}/availability")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update only the availability of an option item")
    public ResponseEntity<OptionItemResponse> updateOptionItemAvailability(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateOptionItemAvailabilityRequest request
    ) {
        return ResponseEntity.ok(optionItemService.updateOptionItemAvailability(authentication, groupId, itemId, request));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete an option item")
    public ResponseEntity<Void> deleteOptionItem(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID itemId
    ) {
        optionItemService.deleteOptionItem(authentication, groupId, itemId);
        return ResponseEntity.noContent().build();
    }
}
