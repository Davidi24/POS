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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.CreateOptionGroupTypeRequest;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.dto.UpdateOptionGroupTypeRequest;
import pos.pos.menu.service.OptionGroupTypeService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Option Group Types")
@RestController
@RequestMapping("/option-group-types")
@RequiredArgsConstructor
public class OptionGroupTypeController {

    private final OptionGroupTypeService optionGroupTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List option group types")
    public ResponseEntity<List<OptionGroupTypeResponse>> getTypes(
            Authentication authentication,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(optionGroupTypeService.getTypes(authentication, search));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create an option group type")
    public ResponseEntity<OptionGroupTypeResponse> createType(
            Authentication authentication,
            @Valid @RequestBody CreateOptionGroupTypeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupTypeService.createType(authentication, request));
    }

    @PutMapping("/{typeId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update an option group type")
    public ResponseEntity<OptionGroupTypeResponse> updateType(
            Authentication authentication,
            @PathVariable UUID typeId,
            @Valid @RequestBody UpdateOptionGroupTypeRequest request
    ) {
        return ResponseEntity.ok(optionGroupTypeService.updateType(authentication, typeId, request));
    }

    @DeleteMapping("/{typeId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete an option group type")
    public ResponseEntity<Void> deleteType(
            Authentication authentication,
            @PathVariable UUID typeId
    ) {
        optionGroupTypeService.deleteType(authentication, typeId);
        return ResponseEntity.noContent().build();
    }
}
