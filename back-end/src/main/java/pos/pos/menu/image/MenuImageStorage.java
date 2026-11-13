package pos.pos.menu.image;

import java.util.Optional;
import java.util.UUID;

// Where dish photos live: Cloudflare R2 in production, a local folder in development. Objects are never overwritten
// (every upload gets a new key), so a URL always shows the same picture.
public interface MenuImageStorage {

    /** Stores the (already checked) photo and returns its object key, e.g. "menu-images/{restaurant}/{uuid}.jpg". */
    String store(UUID restaurantId, byte[] content, String contentType, String extension);

    /** Deletes the object; a missing object is fine. */
    void delete(String objectKey);

    /** The photo's bytes for the backend's /public/menu-images/** route, or empty when it doesn't exist. */
    Optional<StoredPhoto> load(String objectKey);

    /** The URL saved on the dish: the public R2 domain when there is one, otherwise the backend's own route. */
    String urlFor(String objectKey);

    /** The object key when the URL points at a photo this storage keeps (other URLs are left alone). */
    Optional<String> keyOf(String url);

    record StoredPhoto(byte[] content, String contentType) {
    }

    static String newKey(UUID restaurantId, String extension) {
        return "menu-images/" + restaurantId + "/" + UUID.randomUUID() + extension;
    }

    // Keys look like "menu-images/{uuid}/{uuid}.{ext}": checked before any key from a URL reaches the storage.
    static boolean isValidKey(String key) {
        return key != null && key.matches("menu-images/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\.(png|jpg|webp)");
    }

    // The backend's own route for a key (used when photos aren't served from a public R2 domain).
    static String backendUrl(String key) {
        return "/public/" + key;
    }

    static Optional<String> keyFromBackendUrl(String url) {
        if (url == null || !url.startsWith("/public/menu-images/")) {
            return Optional.empty();
        }
        String key = url.substring("/public/".length());
        return isValidKey(key) ? Optional.of(key) : Optional.empty();
    }
}
