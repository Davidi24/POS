package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchNotFoundException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.entity.SettingsReceipt;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.function.Function;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsDomainSupport {

    private final SettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;

    public UUID currentActorId(Authentication authentication) {
        return roleHierarchyService.currentUserId(authentication);
    }

    public Restaurant requireAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);

        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return restaurant;
        }

        User actor = userRepository.findByIdAndDeletedAtIsNull(roleHierarchyService.currentUserId(authentication))
                .orElseThrow(UserNotFoundException::new);

        if (!restaurantId.equals(actor.getRestaurantId())) {
            throw new AuthException(
                    "You are not allowed to manage settings for this restaurant",
                    HttpStatus.FORBIDDEN
            );
        }

        return restaurant;
    }

    public Branch requireAccessibleBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        requireAccessibleRestaurant(authentication, restaurantId);
        return resolveBranch(restaurantId, branchId);
    }

    public Branch resolveBranch(UUID restaurantId, UUID branchId) {
        return branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)
                .orElseThrow(BranchNotFoundException::new);
    }

    public Settings loadOrCreateSettings(Authentication authentication, UUID restaurantId) {
        UUID actorId = currentActorId(authentication);
        Restaurant restaurant = requireAccessibleRestaurant(authentication, restaurantId);
        return loadOrCreateSettings(restaurant, actorId);
    }

    public Settings loadOrCreateSettings(Restaurant restaurant, UUID actorId) {
        Settings settings = settingsRepository.findByRestaurant_Id(restaurant.getId())
                .orElseGet(() -> createDefaultSettings(restaurant, actorId));
        return ensureChildSettings(settings, actorId);
    }

    public Settings saveSettings(Settings settings) {
        return persistSettings(settings, ex -> null);
    }

    private Settings createDefaultSettings(Restaurant restaurant, UUID actorId) {
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setCreatedBy(actorId);
        settings.setUpdatedBy(actorId);
        settings.setReceiptSettings(new SettingsReceipt());
        settings.setOrderRuleSettings(new SettingsOrderRule());

        return persistSettings(
                settings,
                ex -> settingsRepository.findByRestaurant_Id(restaurant.getId())
                        .orElseThrow(() -> ex)
        );
    }

    private Settings ensureChildSettings(Settings settings, UUID actorId) {
        boolean changed = false;

        if (settings.getReceiptSettings() == null) {
            settings.setReceiptSettings(new SettingsReceipt());
            changed = true;
        }

        if (settings.getOrderRuleSettings() == null) {
            settings.setOrderRuleSettings(new SettingsOrderRule());
            changed = true;
        }

        if (!changed) {
            return settings;
        }

        settings.setUpdatedBy(actorId);
        return saveSettings(settings);
    }

    private Settings persistSettings(
            Settings settings,
            Function<DataIntegrityViolationException, Settings> conflictResolver
    ) {
        try {
            return settingsRepository.saveAndFlush(settings);
        } catch (DataIntegrityViolationException ex) {
            Settings recovered = conflictResolver.apply(ex);
            if (recovered != null) {
                return recovered;
            }
            throw new AuthException("Settings update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
