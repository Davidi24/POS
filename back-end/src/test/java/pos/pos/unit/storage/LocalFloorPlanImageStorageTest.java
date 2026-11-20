package pos.pos.unit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.storage.LocalFloorPlanImageStorage;
import pos.pos.storage.FloorPlanImageStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFloorPlanImageStorageTest {

    @TempDir
    Path tempDirectory;

    private static final UUID RESTAURANT_ID = UUID.fromString("9a2aa42b-1f3c-4460-94db-b93f5abcf6f1");
    private static final UUID BRANCH_ID = UUID.fromString("66948ab2-ecb1-4314-9a08-36b0b1af9ac2");

    @Test
    void storesImageInRestaurantAndBranchScopedDirectory() throws Exception {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        MockMultipartFile upload = new MockMultipartFile("file", "plan.png", "image/png", png);

        FloorPlanImageStorage.StoredImage stored = storage.store(RESTAURANT_ID, BRANCH_ID, upload);

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.size()).isEqualTo(png.length);
        assertThat(stored.objectKey()).startsWith(RESTAURANT_ID + "/" + BRANCH_ID + "/");
        assertThat(stored.url()).isEqualTo("/public/floor-plan-images/" + stored.objectKey());
        String fileName = stored.objectKey().substring(stored.objectKey().lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(tempDirectory.resolve(RESTAURANT_ID.toString())
                .resolve(BRANCH_ID.toString()).resolve(fileName))).containsExactly(png);
        assertThat(storage.load(RESTAURANT_ID, BRANCH_ID, fileName).getInputStream().readAllBytes())
                .containsExactly(png);

        storage.delete(RESTAURANT_ID, BRANCH_ID, fileName);
        assertThatThrownBy(() -> storage.load(RESTAURANT_ID, BRANCH_ID, fileName))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Test
    void acceptsJpegAndWebpSignatures() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        byte[][] contents = {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3},
                {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1}
        };
        String[] types = {"image/jpeg", "image/webp"};
        String[] extensions = {".jpg", ".webp"};

        for (int index = 0; index < types.length; index++) {
            MockMultipartFile upload = new MockMultipartFile("file", "plan", types[index], contents[index]);
            FloorPlanImageStorage.StoredImage stored = storage.store(RESTAURANT_ID, BRANCH_ID, upload);
            assertThat(stored.contentType()).isEqualTo(types[index]);
            assertThat(stored.objectKey()).endsWith(extensions[index]);
        }
    }

    @Test
    void rejectsFilesOverTheConfiguredSizeLimit() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile upload = new MockMultipartFile("file", "plan.png", "image/png", oversized);

        assertThatThrownBy(() -> storage.store(RESTAURANT_ID, BRANCH_ID, upload))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsEmptyUploadsAndUnapprovedMediaTypes() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        MockMultipartFile empty = new MockMultipartFile("file", "plan.png", "image/png", new byte[0]);
        MockMultipartFile svg = new MockMultipartFile("file", "plan.svg", "image/svg+xml", "<svg/>".getBytes());

        assertThatThrownBy(() -> storage.store(RESTAURANT_ID, BRANCH_ID, empty))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> storage.store(RESTAURANT_ID, BRANCH_ID, svg))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsBytesThatDoNotMatchDeclaredImageType() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile("file", "plan.png", "image/png", "not a png".getBytes());

        assertThatThrownBy(() -> storage.store(RESTAURANT_ID, BRANCH_ID, upload))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsContentTypeSpoofing() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        MockMultipartFile upload = new MockMultipartFile("file", "plan.png", "image/png", jpeg);

        assertThatThrownBy(() -> storage.store(RESTAURANT_ID, BRANCH_ID, upload))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsUnsafeFileNamesWhenLoadingOrDeleting() {
        LocalFloorPlanImageStorage storage = new LocalFloorPlanImageStorage(tempDirectory.toString());

        assertThatThrownBy(() -> storage.load(RESTAURANT_ID, BRANCH_ID, "../secret.png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> storage.delete(RESTAURANT_ID, BRANCH_ID, "../secret.png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
    }
}
