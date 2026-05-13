package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.OptionGroupDeletionBlockedException;
import pos.pos.exception.menu.OptionGroupNameAlreadyExistsException;
import pos.pos.exception.menu.OptionGroupNotFoundException;
import pos.pos.exception.menu.OptionGroupTypeNotFoundException;
import pos.pos.menu.dto.CreateOptionGroupRequest;
import pos.pos.menu.dto.OptionGroupResponse;
import pos.pos.menu.dto.UpdateOptionGroupRequest;
import pos.pos.menu.dto.UpdateOptionGroupStatusRequest;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.entity.OptionItem;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptionGroupService {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionGroupTypeRepository optionGroupTypeRepository;
    private final OptionItemRepository optionItemRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final MenuMapper menuMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public List<OptionGroupResponse> getOptionGroups(
            Authentication authentication,
            UUID restaurantId,
            UUID typeId,
            Boolean active,
            String search,
            boolean includeItems
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
        List<OptionGroup> groups = optionGroupRepository.searchByRestaurant(restaurantId, typeId, active, searchLike);
        return groups.stream()
                .map(group -> toOptionGroupResponse(group, includeItems))
                .toList();
    }

    @Transactional(readOnly = true)
    public OptionGroupResponse getOptionGroup(Authentication authentication, UUID groupId, boolean includeItems) {
        OptionGroup group = requireAccessibleOptionGroup(authentication, groupId);
        return toOptionGroupResponse(group, includeItems);
    }

    @Transactional
    public OptionGroupResponse createOptionGroup(Authentication authentication, CreateOptionGroupRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, request.getRestaurantId());
        assertOptionGroupWriteAllowed(restaurant);

        OptionGroupType type = optionGroupTypeRepository.findById(request.getTypeId())
                .orElseThrow(OptionGroupTypeNotFoundException::new);
        String normalizedName = NormalizationUtils.normalize(request.getName());
        validateSelectionBounds(request.getMinSelect(), request.getMaxSelect());
        assertUniqueName(restaurant.getId(), normalizedName, null);

        OptionGroup group = new OptionGroup();
        group.setRestaurant(restaurant);
        group.setType(type);
        group.setName(normalizedName);
        group.setDescription(NormalizationUtils.normalize(request.getDescription()));
        group.setMinSelect(request.getMinSelect());
        group.setMaxSelect(request.getMaxSelect());
        group.setRequired(Boolean.TRUE.equals(request.getRequired()));
        group.setActive(request.getActive() == null || request.getActive());
        group.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());

        return menuMapper.toOptionGroupResponse(optionGroupRepository.saveAndFlush(group));
    }

    @Transactional
    public OptionGroupResponse updateOptionGroup(
            Authentication authentication,
            UUID groupId,
            UpdateOptionGroupRequest request
    ) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        OptionGroupType type = optionGroupTypeRepository.findById(request.getTypeId())
                .orElseThrow(OptionGroupTypeNotFoundException::new);
        String normalizedName = NormalizationUtils.normalize(request.getName());
        validateSelectionBounds(request.getMinSelect(), request.getMaxSelect());
        assertUniqueName(group.getRestaurant().getId(), normalizedName, groupId);

        group.setType(type);
        group.setName(normalizedName);
        group.setDescription(NormalizationUtils.normalize(request.getDescription()));
        group.setMinSelect(request.getMinSelect());
        group.setMaxSelect(request.getMaxSelect());
        group.setRequired(Boolean.TRUE.equals(request.getRequired()));
        group.setActive(Boolean.TRUE.equals(request.getActive()));
        group.setDisplayOrder(request.getDisplayOrder());

        return menuMapper.toOptionGroupResponse(optionGroupRepository.saveAndFlush(group));
    }

    @Transactional
    public OptionGroupResponse updateOptionGroupStatus(
            Authentication authentication,
            UUID groupId,
            UpdateOptionGroupStatusRequest request
    ) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        group.setActive(Boolean.TRUE.equals(request.getActive()));
        return menuMapper.toOptionGroupResponse(optionGroupRepository.saveAndFlush(group));
    }

    @Transactional
    public void deleteOptionGroup(Authentication authentication, UUID groupId) {
        OptionGroup group = requireManageableOptionGroup(authentication, groupId);
        if (optionItemRepository.existsByOptionGroupId(groupId) || menuItemOptionGroupRepository.existsByOptionGroupId(groupId)) {
            throw new OptionGroupDeletionBlockedException();
        }
        optionGroupRepository.delete(group);
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

    private OptionGroupResponse toOptionGroupResponse(OptionGroup group, boolean includeItems) {
        List<OptionItem> items = includeItems
                ? optionItemRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(group.getId())
                : null;
        return menuMapper.toOptionGroupResponse(group, items);
    }

    private void validateSelectionBounds(Integer minSelect, Integer maxSelect) {
        if (minSelect != null && maxSelect != null && minSelect > maxSelect) {
            throw new AuthException("minSelect must be less than or equal to maxSelect", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertUniqueName(UUID restaurantId, String name, UUID groupIdToExclude) {
        boolean exists = groupIdToExclude == null
                ? optionGroupRepository.existsByRestaurantIdAndName(restaurantId, name)
                : optionGroupRepository.existsByRestaurantIdAndNameAndIdNot(restaurantId, name, groupIdToExclude);
        if (exists) {
            throw new OptionGroupNameAlreadyExistsException();
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
