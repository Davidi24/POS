package pos.pos.tables.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.tables.dto.ReorderTableCategoriesRequest;
import pos.pos.tables.dto.TableCategoryRequest;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.dto.UpdateTableCategoryStatusRequest;
import pos.pos.tables.service.TableCategoryService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tables")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/table-categories")
@RequiredArgsConstructor
public class TableCategoryController {

    private final TableCategoryService tableCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch table categories")
    public ResponseEntity<List<TableCategoryResponse>> getTableCategories(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tableCategoryService.getTableCategories(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a branch table category")
    public ResponseEntity<TableCategoryResponse> createTableCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody TableCategoryRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tableCategoryService.createTableCategory(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one branch table category")
    public ResponseEntity<TableCategoryResponse> getTableCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID categoryId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tableCategoryService.getTableCategory(authentication, restaurantId, branchId, categoryId));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a branch table category")
    public ResponseEntity<TableCategoryResponse> updateTableCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody TableCategoryRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tableCategoryService.updateTableCategory(authentication, restaurantId, branchId, categoryId, request));
    }

    @PatchMapping("/{categoryId}/status")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update branch table category active status")
    public ResponseEntity<TableCategoryResponse> updateTableCategoryStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateTableCategoryStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tableCategoryService.updateTableCategoryStatus(authentication, restaurantId, branchId, categoryId, request));
    }

    @PatchMapping("/reorder")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace branch table category order")
    public ResponseEntity<List<TableCategoryResponse>> reorderTableCategories(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody ReorderTableCategoriesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tableCategoryService.reorderTableCategories(authentication, restaurantId, branchId, request));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a branch table category")
    public ResponseEntity<Void> deleteTableCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID categoryId,
            Authentication authentication
    ) {
        tableCategoryService.deleteTableCategory(authentication, restaurantId, branchId, categoryId);
        return ResponseEntity.noContent().build();
    }
}
