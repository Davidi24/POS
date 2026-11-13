package pos.pos.menu.image;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

// Dish photos for the apps and the guest pages when they aren't served from a public R2 domain. Keys are random and
// never reused, so a photo can be cached for a year.
@RestController
@RequestMapping("/public/menu-images")
@RequiredArgsConstructor
public class MenuImagePublicController {

    private final MenuImageStorage storage;

    @GetMapping("/{restaurantId}/{fileName}")
    public ResponseEntity<byte[]> photo(@PathVariable String restaurantId, @PathVariable String fileName) {
        String key = "menu-images/" + restaurantId + "/" + fileName;
        if (!MenuImageStorage.isValidKey(key)) {
            return ResponseEntity.notFound().build();
        }
        return storage.load(key)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .body(photo.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
