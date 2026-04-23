package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.BranchAddressNotFoundException;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.BranchAddress;
import pos.pos.restaurant.mapper.BranchAddressMapper;
import pos.pos.restaurant.repository.BranchAddressRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchAddressService {

    private final RestaurantScopeService restaurantScopeService;
    private final BranchAddressRepository branchAddressRepository;
    private final BranchAddressMapper branchAddressMapper;

    public List<AddressResponse> getAddresses(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return branchAddressRepository.findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(branchId)
                .stream()
                .map(branchAddressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpsertAddressRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(branchId, null, actorId);
        }

        BranchAddress address = branchAddressMapper.toNewEntity(branch, request, actorId);
        branchAddressRepository.save(address);
        return branchAddressMapper.toResponse(address);
    }

    @Transactional
    public AddressResponse updateAddress(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID addressId,
            UpsertAddressRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchAddress address = findExistingAddress(branchId, addressId);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(branchId, addressId, actorId);
        }

        branchAddressMapper.updateEntity(address, request, actorId);
        branchAddressRepository.save(address);
        return branchAddressMapper.toResponse(address);
    }

    @Transactional
    public AddressResponse makePrimary(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID addressId
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchAddress address = findExistingAddress(branchId, addressId);
        clearExistingPrimary(branchId, addressId, actorId);
        branchAddressMapper.updatePrimary(address, true, actorId);
        branchAddressRepository.save(address);
        return branchAddressMapper.toResponse(address);
    }

    @Transactional
    public void deleteAddress(Authentication authentication, UUID restaurantId, UUID branchId, UUID addressId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        BranchAddress address = findExistingAddress(branchId, addressId);
        branchAddressMapper.markDeleted(
                address,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        branchAddressRepository.save(address);
    }

    private BranchAddress findExistingAddress(UUID branchId, UUID addressId) {
        return branchAddressRepository.findByIdAndBranchIdAndDeletedAtIsNull(addressId, branchId)
                .orElseThrow(BranchAddressNotFoundException::new);
    }

    private void clearExistingPrimary(UUID branchId, UUID keepAddressId, UUID actorId) {
        branchAddressRepository.findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(branchId)
                .filter(existing -> !existing.getId().equals(keepAddressId))
                .ifPresent(existing -> {
                    branchAddressMapper.updatePrimary(existing, false, actorId);
                    branchAddressRepository.save(existing);
                });
    }
}
