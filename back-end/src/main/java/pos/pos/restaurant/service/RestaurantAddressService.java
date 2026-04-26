package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.RestaurantAddressNotFoundException;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantAddress;
import pos.pos.restaurant.mapper.RestaurantAddressMapper;
import pos.pos.restaurant.repository.RestaurantAddressRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

//checked
@Service
@RequiredArgsConstructor
public class RestaurantAddressService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantAddressRepository restaurantAddressRepository;
    private final RestaurantAddressMapper restaurantAddressMapper;

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantAddressRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(restaurantId)
                .stream()
                .map(restaurantAddressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            Authentication authentication,
            UUID restaurantId,
            UpsertAddressRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(restaurantId, null, actorId);
        }

        RestaurantAddress address = restaurantAddressMapper.toNewEntity(restaurant, request, actorId);
        restaurantAddressRepository.save(address);
        return restaurantAddressMapper.toResponse(address);
    }

    @Transactional
    public AddressResponse updateAddress(
            Authentication authentication,
            UUID restaurantId,
            UUID addressId,
            UpsertAddressRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantAddress address = findExistingAddress(restaurantId, addressId);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(restaurantId, addressId, actorId);
        }

        restaurantAddressMapper.updateEntity(address, request, actorId);
        restaurantAddressRepository.save(address);
        return restaurantAddressMapper.toResponse(address);
    }

    @Transactional
    public AddressResponse makePrimary(Authentication authentication, UUID restaurantId, UUID addressId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantAddress address = findExistingAddress(restaurantId, addressId);
        clearExistingPrimary(restaurantId, addressId, actorId);
        restaurantAddressMapper.updatePrimary(address, true, actorId);
        restaurantAddressRepository.save(address);
        return restaurantAddressMapper.toResponse(address);
    }

    @Transactional
    public void deleteAddress(Authentication authentication, UUID restaurantId, UUID addressId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantAddress address = findExistingAddress(restaurantId, addressId);
        restaurantAddressMapper.markDeleted(
                address,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        restaurantAddressRepository.save(address);
    }

    private RestaurantAddress findExistingAddress(UUID restaurantId, UUID addressId) {
        return restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(addressId, restaurantId)
                .orElseThrow(RestaurantAddressNotFoundException::new);
    }

    private void clearExistingPrimary(UUID restaurantId, UUID keepAddressId, UUID actorId) {
        restaurantAddressRepository.clearPrimary(restaurantId, keepAddressId, actorId);
    }
}
