package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.entity.SettingsReceipt;
import pos.pos.settings.repository.SettingsRepository;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsDomainSupport {

    private final SettingsRepository settingsRepository;
    private final RestaurantScopeService restaurantScopeService;

    public UUID currentActorId(Authentication authentication) {
        return restaurantScopeService.currentUserId(authentication);
    }

    public Restaurant requireAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
        return translateSettingsAccessError(
                () -> restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId),
                "You are not allowed to manage settings for this restaurant"
        );
    }

    public Restaurant requireManageableRestaurant(Authentication authentication, UUID restaurantId) {
        return translateSettingsAccessError(
                () -> restaurantScopeService.requireManageableRestaurant(authentication, restaurantId),
                "You are not allowed to manage settings for this restaurant"
        );
    }

    public Branch requireAccessibleBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        return translateSettingsAccessError(
                () -> restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId),
                "You are not allowed to manage settings for this branch"
        );
    }

    public Branch requireManageableBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        return translateSettingsAccessError(
                () -> restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId),
                "You are not allowed to manage settings for this branch"
        );
    }

    public Branch resolveBranch(UUID restaurantId, UUID branchId) {
        return restaurantScopeService.requireExistingBranch(restaurantId, branchId);
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

    private <T> T translateSettingsAccessError(Supplier<T> action, String message) {
        try {
            return action.get();
        } catch (AuthException ex) {
            if (ex.getStatus() == HttpStatus.FORBIDDEN) {
                throw new AuthException(message, HttpStatus.FORBIDDEN);
            }
            throw ex;
        }
    }
}
