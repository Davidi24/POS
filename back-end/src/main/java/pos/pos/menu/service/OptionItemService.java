package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.OptionGroupNotFoundException;
import pos.pos.exception.menu.OptionItemGroupMismatchException;
import pos.pos.exception.menu.OptionItemNameAlreadyExistsException;
import pos.pos.exception.menu.OptionItemNotFoundException;
import pos.pos.menu.dto.CreateOptionItemRequest;
import pos.pos.menu.dto.OptionItemResponse;
import pos.pos.menu.dto.UpdateOptionItemAvailabilityRequest;
import pos.pos.menu.dto.UpdateOptionItemRequest;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.menu.util.MenuCodeNormalizer;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptionItemService {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;
    private final MenuMapper menuMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public List<OptionItemResponse> getOptionItems(Authentication authentication, UUID groupId, Boolean available) {
        OptionGroup group = requireAccessibleOptionGroup(authentication, groupId);
        List<OptionItem> items = available == null
                ? optionItemRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(group.getId())
                : optionItemRepository.findByOptionGroupIdAndAvailableOrderByDisplayOrderAscNameAsc(group.getId(), available);
        return items.stream()
                .map(menuMapper::toOptionItemResponse)
                .toList();
    }

    @Transactional
    public OptionItemResponse createOptionItem(
            Authentication authentication,
            UUID groupId,
            CreateOptionItemRequest request
    ) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        String normalizedName = NormalizationUtils.normalize(request.getName());
        String normalizedCode = resolveCode(request.getCode(), normalizedName, "Name is required");
        assertUniqueName(groupId, normalizedName, null);

        OptionItem item = new OptionItem();
        item.setOptionGroup(group);
        item.setCode(normalizedCode);
        item.setName(normalizedName);
        item.setPriceDelta(request.getPriceDelta());
        item.setAvailable(request.getAvailable() == null || request.getAvailable());
        item.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());

        return menuMapper.toOptionItemResponse(optionItemRepository.saveAndFlush(item));
    }

    @Transactional
    public OptionItemResponse updateOptionItem(
            Authentication authentication,
            UUID groupId,
            UUID itemId,
            UpdateOptionItemRequest request
    ) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        OptionItem item = requireScopedItem(group, itemId);
        String normalizedName = NormalizationUtils.normalize(request.getName());
        String normalizedCode = resolveCode(request.getCode(), normalizedName, "Name is required");
        assertUniqueName(groupId, normalizedName, itemId);

        item.setCode(normalizedCode);
        item.setName(normalizedName);
        item.setPriceDelta(request.getPriceDelta());
        item.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        item.setDisplayOrder(request.getDisplayOrder());

        return menuMapper.toOptionItemResponse(optionItemRepository.saveAndFlush(item));
    }

    @Transactional
    public OptionItemResponse updateOptionItemAvailability(
            Authentication authentication,
            UUID groupId,
            UUID itemId,
            UpdateOptionItemAvailabilityRequest request
    ) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        OptionItem item = requireScopedItem(group, itemId);
        item.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        return menuMapper.toOptionItemResponse(optionItemRepository.saveAndFlush(item));
    }

    @Transactional
    public void deleteOptionItem(Authentication authentication, UUID groupId, UUID itemId) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        OptionItem item = requireScopedItem(group, itemId);
        optionItemRepository.delete(item);
    }

    private OptionGroup requireAccessibleOptionGroup(Authentication authentication, UUID groupId) {
        OptionGroup group = optionGroupRepository.findById(groupId)
                .orElseThrow(OptionGroupNotFoundException::new);
        restaurantScopeService.requireAccessibleRestaurant(authentication, group.getRestaurant().getId());
        return group;
    }

    private OptionGroup requireManageableOptionGroup(Authentication authentication, UUID groupId) {
        OptionGroup group = optionGroupRepository.findById(groupId)
                .orElseThrow(OptionGroupNotFoundException::new);
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, group.getRestaurant().getId());
        assertOptionGroupWriteAllowed(restaurant);
        return group;
    }

    private OptionItem requireScopedItem(OptionGroup group, UUID itemId) {
        OptionItem item = optionItemRepository.findById(itemId)
                .orElseThrow(OptionItemNotFoundException::new);
        if (!item.getOptionGroup().getId().equals(group.getId())) {
            throw new OptionItemGroupMismatchException();
        }
        return item;
    }

    private String resolveCode(String requestedCode, String fallbackName, String emptyMessage) {
        String normalizedCode = MenuCodeNormalizer.normalize(
                NormalizationUtils.normalize(requestedCode) == null ? fallbackName : requestedCode
        );
        if (normalizedCode == null) {
            throw new AuthException(emptyMessage, HttpStatus.BAD_REQUEST);
        }
        return normalizedCode;
    }

    private void assertUniqueName(UUID groupId, String name, UUID itemIdToExclude) {
        boolean exists = itemIdToExclude == null
                ? optionItemRepository.existsByOptionGroupIdAndName(groupId, name)
                : optionItemRepository.existsByOptionGroupIdAndNameAndIdNot(groupId, name, itemIdToExclude);
        if (exists) {
            throw new OptionItemNameAlreadyExistsException();
        }
    }

    private void assertOptionGroupWriteAllowed(Restaurant restaurant) {
        RestaurantStatus status = restaurant.getStatus();
        restaurantValidationService.validateManageableStatus(status);
        if (status == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }
}
