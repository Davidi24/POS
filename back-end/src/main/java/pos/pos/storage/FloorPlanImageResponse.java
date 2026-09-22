package pos.pos.storage;

public record FloorPlanImageResponse(
        String objectKey,
        String url,
        String contentType,
        long size
) {
    public static FloorPlanImageResponse from(FloorPlanImageStorage.StoredImage image) {
        return new FloorPlanImageResponse(
                image.objectKey(),
                image.url(),
                image.contentType(),
                image.size()
        );
    }
}
