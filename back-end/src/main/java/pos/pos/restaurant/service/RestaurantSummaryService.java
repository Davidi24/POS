package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.restaurant.dto.RestaurantSummaryResponse;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantAddressRepository;
import pos.pos.restaurant.repository.RestaurantBrandingRepository;
import pos.pos.restaurant.repository.RestaurantContactRepository;
import pos.pos.restaurant.repository.RestaurantTaxProfileRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantSummaryService {

    private final RestaurantScopeService restaurantScopeService;
    private final BranchRepository branchRepository;
    private final RestaurantAddressRepository restaurantAddressRepository;
    private final RestaurantContactRepository restaurantContactRepository;
    private final RestaurantBrandingRepository restaurantBrandingRepository;
    private final RestaurantTaxProfileRepository restaurantTaxProfileRepository;

    @Transactional(readOnly = true)
    public RestaurantSummaryResponse getSummary(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        long totalBranches = branchRepository.countByRestaurantIdAndDeletedAtIsNull(restaurantId);
        long activeBranches = branchRepository.countByRestaurantIdAndIsActiveTrueAndDeletedAtIsNull(restaurantId);

        return new RestaurantSummaryResponse(
                restaurantId,
                totalBranches,
                activeBranches,
                totalBranches - activeBranches,
                restaurantAddressRepository.existsByRestaurantIdAndDeletedAtIsNull(restaurantId),
                restaurantBrandingRepository.existsByRestaurantIdAndDeletedAtIsNull(restaurantId),
                restaurantContactRepository.existsByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(restaurantId),
                restaurantTaxProfileRepository.existsByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(restaurantId)
        );
    }
}
