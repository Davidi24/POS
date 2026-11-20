package pos.pos.unit.storage;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.storage.DisabledFloorPlanImageStorage;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledFloorPlanImageStorageTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("9a2aa42b-1f3c-4460-94db-b93f5abcf6f1");
    private static final UUID BRANCH_ID = UUID.fromString("66948ab2-ecb1-4314-9a08-36b0b1af9ac2");
    private final DisabledFloorPlanImageStorage storage = new DisabledFloorPlanImageStorage();

    @Test
    void refusesUploadsWhenNoDurableProviderIsConfigured() {
        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", new byte[]{1});

        assertUnavailable(() -> storage.store(RESTAURANT_ID, BRANCH_ID, file));
    }

    @Test
    void refusesDownloadsWhenNoDurableProviderIsConfigured() {
        assertUnavailable(() -> storage.load(RESTAURANT_ID, BRANCH_ID, "f88447bd-b511-4a62-9e0d-3166c6ad7cc3.png"));
    }

    @Test
    void doesNotExposeBrokenUrlsWhenStorageIsDisabled() {
        assertThat(storage.publicUrl("restaurant/branch/plan.png")).isNull();
    }

    @Test
    void allowsMetadataRemovalWhenThereIsNoBackingObject() {
        storage.delete(RESTAURANT_ID, BRANCH_ID, "f88447bd-b511-4a62-9e0d-3166c6ad7cc3.png");
    }

    private void assertUnavailable(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        Object run() throws Exception;
    }
}
