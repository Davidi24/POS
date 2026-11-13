package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchNotFoundException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.restaurant.policy.BranchPolicy;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.util.UUID;

// This service is the single place where "does this user have access to this restaurant/branch?" is answered.
// Before any service reads or modifies a restaurant or branch, it calls one of the require* methods here.
// Each method fetches the resource from the DB and checks if the logged-in user is allowed to touch it.
// If the resource doesn't exist or the user is not allowed — an exception is thrown and the operation stops.
@Service
@RequiredArgsConstructor
public class RestaurantScopeService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entities;

    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final ActorScopeService actorScopeService;
    private final RestaurantPolicy restaurantPolicy;
    private final BranchPolicy branchPolicy;

    public Restaurant requireExistingRestaurant(UUID restaurantId) {
        Restaurant restaurant=restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow(RestaurantNotFoundException::new);
        lockForMutation(restaurant);
        if(restaurant.getDeletedAt()!=null)throw new RestaurantNotFoundException();
        return restaurant;
    }

    /** Historical receipts remain readable by authorized actors after archival. */
    public Restaurant requireHistoricalRestaurant(Authentication authentication,UUID restaurantId){
        Restaurant restaurant=restaurantRepository.findById(restaurantId).orElseThrow(RestaurantNotFoundException::new);
        restaurantPolicy.assertCanAccess(actorScopeService.resolve(authentication),restaurant);
        lockForMutation(restaurant);return restaurant;
    }

    public Restaurant requireAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
        return requireAccessibleRestaurant(actorScopeService.resolve(authentication), restaurantId);
    }

    public Restaurant requireManageableRestaurant(Authentication authentication, UUID restaurantId) {
        return requireManageableRestaurant(actorScopeService.resolve(authentication), restaurantId);
    }

    public Restaurant requireAccessibleRestaurant(ActorScope scope, UUID restaurantId) {
        Restaurant restaurant = requireExistingRestaurant(restaurantId);
        restaurantPolicy.assertCanAccess(scope, restaurant);
        return restaurant;
    }

    // check if restaurant exist if yes check its policy between restaurant and user
    public Restaurant requireManageableRestaurant(ActorScope scope, UUID restaurantId) {
        Restaurant restaurant = requireExistingRestaurant(restaurantId);
        restaurantPolicy.assertCanManage(scope, restaurant);
        return restaurant;
    }

    /**
     * For what only the Super Admin and Co-Super-Admins decide (opening and closing restaurants and branches, legal
     * and tax details, currency, time zone). The restaurant's own staff, owner included, get [message] as a 403.
     */
    public Restaurant requirePlatformManagedRestaurant(Authentication authentication, UUID restaurantId, String message) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Restaurant restaurant = requireExistingRestaurant(restaurantId);
        if (!scope.superAdmin()) {
            restaurantPolicy.assertCanAccess(scope, restaurant);
            throw new AuthException(message, HttpStatus.FORBIDDEN);
        }
        return restaurant;
    }

    public Branch requireExistingBranch(UUID restaurantId, UUID branchId) {
        Branch branch=branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId,restaurantId).orElseThrow(BranchNotFoundException::new);
        lockForMutation(branch.getRestaurant());
        if(writing() && entities.getLockMode(branch)!=jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            entities.refresh(branch,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(branch.getDeletedAt()!=null || branch.getRestaurant().getDeletedAt()!=null)throw new BranchNotFoundException();
        return branch;
    }

    public Branch requireAccessibleBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        return requireAccessibleBranch(actorScopeService.resolve(authentication), restaurantId, branchId);
    }

    public Branch requireAccessibleBranch(ActorScope scope, UUID restaurantId, UUID branchId) {
        Branch branch = requireExistingBranch(restaurantId, branchId);
        branchPolicy.assertCanAccess(scope, branch);
        return branch;
    }

    public Branch requireManageableBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        return requireManageableBranch(actorScopeService.resolve(authentication), restaurantId, branchId);
    }

    public Branch requireManageableBranch(ActorScope scope, UUID restaurantId, UUID branchId) {
        Branch branch = requireExistingBranch(restaurantId, branchId);
        branchPolicy.assertCanManage(scope, branch);
        return branch;
    }

    /** All scoped mutations use the same parent lock as currency, time-zone and closure changes. */
    public void lockForMutation(Restaurant restaurant) {
        if(writing() && entities.getLockMode(restaurant)!=jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            entities.refresh(restaurant,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    private boolean writing(){
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    public void assertCanCreateRestaurant(Authentication authentication) {
        assertCanCreateRestaurant(actorScopeService.resolve(authentication));
    }

    public void assertCanDeleteRestaurant(Authentication authentication) {
        assertCanDeleteRestaurant(actorScopeService.resolve(authentication));
    }

    public void assertCanCreateRestaurant(ActorScope scope) {
        restaurantPolicy.assertCanCreate(scope);
    }

    public void assertCanDeleteRestaurant(ActorScope scope) {
        restaurantPolicy.assertCanDelete(scope);
    }

    public UUID currentUserId(Authentication authentication) {
        return actorScopeService.currentUserId(authentication);
    }

    public User currentActor(Authentication authentication) {
        return actorScopeService.currentActor(authentication);
    }
}
