package pos.pos.restaurant.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.menu.image.StoredImageCleanup;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.ReceiptDesignResponse;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.ReceiptPictureResponse;
import pos.pos.restaurant.receipt.ReceiptDesignDtos.SaveReceiptDesignRequest;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The restaurant's receipt design (Restaurant info → Receipt): read by anyone at the restaurant, saved and given pictures by
 * whoever manages it (like the logo). The design is the app's; the server checks it's a JSON object of a sane size, keeps
 * it whole, and lets go of pictures it no longer uses. A picture uploaded but never saved into the design is removed after
 * a day.
 */
@Service
@RequiredArgsConstructor
public class ReceiptDesignService {

    /** A design is words and settings, never pictures (those are links): 256 KB is far more than any receipt needs. */
    static final int MAX_DESIGN_CHARS = 256 * 1024;

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantReceiptDesignRepository repository;
    private final ObjectMapper objectMapper;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;
    private final StoredImageCleanup imageCleanup;

    @Transactional(readOnly = true)
    public ReceiptDesignResponse get(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return repository.findById(restaurantId)
                .map(saved -> new ReceiptDesignResponse(read(saved.getDesign()), saved.getVersion()))
                .orElse(new ReceiptDesignResponse(null, 0));
    }

    @Transactional
    public ReceiptDesignResponse save(Authentication authentication, UUID restaurantId, SaveReceiptDesignRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        JsonNode design = request.design();
        if (design == null || !design.isObject()) {
            throw new AuthException("The receipt's design must be an object", HttpStatus.BAD_REQUEST);
        }
        String text = write(design);
        if (text.length() > MAX_DESIGN_CHARS) {
            throw new AuthException("The receipt's design is too big", HttpStatus.BAD_REQUEST);
        }
        RestaurantReceiptDesign saved = repository.findById(restaurantId).orElse(null);
        if (saved != null && request.expectedVersion() != null && saved.getVersion() != request.expectedVersion()) {
            throw new AuthException("The receipt was changed meanwhile. Reload it and try again.", HttpStatus.CONFLICT);
        }
        Set<String> before = saved == null ? Set.of() : pictures(read(saved.getDesign()));
        if (saved == null) {
            saved = new RestaurantReceiptDesign();
            saved.setRestaurantId(restaurantId);
        }
        saved.setDesign(text);
        saved.setUpdatedAt(OffsetDateTime.now());
        saved.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saved = repository.saveAndFlush(saved);
        // Pictures taken off the receipt go once it's saved (only if nothing else uses them).
        Set<String> after = pictures(design);
        before.stream().filter(url -> !after.contains(url)).forEach(imageReleaser::releaseAfterCommit);
        return new ReceiptDesignResponse(design, saved.getVersion());
    }

    @Transactional
    public ReceiptPictureResponse uploadPicture(Authentication authentication, UUID restaurantId, MultipartFile file) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        MenuImageUploads.CheckedPhoto picture = MenuImageUploads.check(file);
        String key = imageStorage.store(restaurantId, picture.content(), picture.contentType(), picture.extension());
        // Kept a day for the design to be saved with it; then removed if the receipt doesn't use it.
        imageCleanup.trackUnclaimed(key);
        return new ReceiptPictureResponse(imageStorage.urlFor(key));
    }

    /** Every picture link in a design (any "imageUrl" field, wherever it is). */
    static Set<String> pictures(JsonNode design) {
        Set<String> urls = new HashSet<>();
        if (design == null) return urls;
        design.findValues("imageUrl").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) urls.add(node.asText());
        });
        return urls;
    }

    private JsonNode read(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String write(JsonNode design) {
        try {
            return objectMapper.writeValueAsString(design);
        } catch (JsonProcessingException e) {
            throw new AuthException("The receipt's design couldn't be read", HttpStatus.BAD_REQUEST);
        }
    }
}
