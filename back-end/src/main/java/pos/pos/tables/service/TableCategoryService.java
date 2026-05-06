package pos.pos.tables.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.exception.tables.TableCategoryNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.dto.ReorderTableCategoriesRequest;
import pos.pos.tables.dto.TableCategoryRequest;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.dto.UpdateTableCategoryStatusRequest;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.mapper.TableCategoryMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class TableCategoryService {

    private final RestaurantScopeService restaurantScopeService;
    private final TableCategoryRepository tableCategoryRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final TableCategoryMapper tableCategoryMapper;

    @Transactional(readOnly = true)
    public List<TableCategoryResponse> getTableCategories(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return tableCategoryRepository.findAllByBranch_IdOrderByDisplayOrderAscNameAsc(branchId).stream()
                .map(tableCategoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public TableCategoryResponse createTableCategory(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            TableCategoryRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        TableCategory tableCategory = new TableCategory();
        tableCategory.setBranch(branch);
        tableCategoryMapper.applyRequest(tableCategory, request);
        return tableCategoryMapper.toResponse(saveTableCategory(tableCategory));
    }

    @Transactional(readOnly = true)
    public TableCategoryResponse getTableCategory(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID categoryId
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return tableCategoryMapper.toResponse(requireTableCategory(branchId, categoryId));
    }

    @Transactional
    public TableCategoryResponse updateTableCategory(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID categoryId,
            TableCategoryRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        TableCategory tableCategory = requireTableCategory(branchId, categoryId);
        tableCategoryMapper.applyRequest(tableCategory, request);
        return tableCategoryMapper.toResponse(saveTableCategory(tableCategory));
    }

    @Transactional
    public TableCategoryResponse updateTableCategoryStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID categoryId,
            UpdateTableCategoryStatusRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        TableCategory tableCategory = requireTableCategory(branchId, categoryId);
        tableCategory.setActive(request.getActive());
        return tableCategoryMapper.toResponse(saveTableCategory(tableCategory));
    }

    @Transactional
    public List<TableCategoryResponse> reorderTableCategories(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            ReorderTableCategoriesRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        List<TableCategory> tableCategories = tableCategoryRepository.findAllByBranch_IdOrderByDisplayOrderAscNameAsc(branchId);
        Map<UUID, TableCategory> categoriesById = tableCategories.stream()
                .collect(LinkedHashMap::new, (map, category) -> map.put(category.getId(), category), Map::putAll);
        List<UUID> requestedIds = request.getCategoryIds();

        validateReorderRequest(tableCategories, categoriesById.keySet(), requestedIds);

        for (int index = 0; index < requestedIds.size(); index++) {
            categoriesById.get(requestedIds.get(index)).setDisplayOrder(index);
        }

        return tableCategoryRepository.saveAllAndFlush(tableCategories).stream()
                .sorted(Comparator.comparingInt(TableCategory::getDisplayOrder)
                        .thenComparing(TableCategory::getName))
                .map(tableCategoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deleteTableCategory(Authentication authentication, UUID restaurantId, UUID branchId, UUID categoryId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        TableCategory tableCategory = requireTableCategory(branchId, categoryId);

        if (restaurantTableRepository.existsByCategory_Id(categoryId)) {
            throw new AuthException("Cannot delete a table category that is still assigned to tables", HttpStatus.CONFLICT);
        }

        tableCategoryRepository.delete(tableCategory);
        tableCategoryRepository.flush();
    }

    private TableCategory requireTableCategory(UUID branchId, UUID categoryId) {
        return tableCategoryRepository.findByIdAndBranch_Id(categoryId, branchId)
                .orElseThrow(TableCategoryNotFoundException::new);
    }

    private void validateReorderRequest(List<TableCategory> tableCategories, Set<UUID> existingIds, List<UUID> requestedIds) {
        if (requestedIds.size() != tableCategories.size()) {
            throw new AuthException("categoryIds must include every table category exactly once", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size() || !existingIds.equals(uniqueIds)) {
            throw new AuthException("categoryIds must include every table category exactly once", HttpStatus.BAD_REQUEST);
        }
    }

    private TableCategory saveTableCategory(TableCategory tableCategory) {
        try {
            return tableCategoryRepository.saveAndFlush(tableCategory);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Table category update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
