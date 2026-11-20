package pos.pos.menu.image;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// Production: dish photos in a Cloudflare R2 bucket (S3-compatible API). With a public bucket domain
// (app.storage.r2.public-base-url) guests' and staff's devices load photos straight from Cloudflare; without one, the
// backend serves them from /public/menu-images/**.
@Service
@ConditionalOnProperty(name = "app.storage.menu-images.provider", havingValue = "r2")
public class R2MenuImageStorage implements MenuImageStorage, DisposableBean {

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public R2MenuImageStorage(
            @Value("${app.storage.r2.endpoint:}") String endpoint,
            @Value("${app.storage.r2.access-key-id:}") String accessKeyId,
            @Value("${app.storage.r2.secret-access-key:}") String secretAccessKey,
            @Value("${app.storage.r2.bucket:}") String bucket,
            @Value("${app.storage.r2.public-base-url:}") String publicBaseUrl
    ) {
        if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(secretAccessKey) || isBlank(bucket)) {
            throw new IllegalStateException("Menu photos are set to R2, but R2_ENDPOINT, R2_ACCESS_KEY_ID, "
                    + "R2_SECRET_ACCESS_KEY and R2_BUCKET aren't all set");
        }
        this.bucket = bucket.trim();
        this.publicBaseUrl = isBlank(publicBaseUrl) ? null : publicBaseUrl.trim().replaceAll("/+$", "");
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint.trim()))
                // R2 ignores the region; "auto" is what Cloudflare documents.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                // R2 rejects Java SDK streaming/chunked SigV4 uploads with SignatureDoesNotMatch.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false).build())
                // R2 doesn't support the newer default checksums on every call: only send them when required.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
    }

    @Override
    public String store(UUID restaurantId, byte[] content, String contentType, String extension) {
        String key = MenuImageStorage.newKey(restaurantId, extension);
        try {
            client.putObject(request -> request
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .storageClass(software.amazon.awssdk.services.s3.model.StorageClass.STANDARD)
                            // Keys are never reused, so devices may keep the photo for a year.
                            .cacheControl("public, max-age=31536000, immutable"),
                    RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not upload the photo to storage. Try again.", exception);
        }
        return key;
    }

    @Override
    public void delete(String objectKey) {
        if (!MenuImageStorage.isValidKey(objectKey)) {
            return;
        }
        try {
            client.deleteObject(request -> request.bucket(bucket).key(objectKey));
        } catch (SdkException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not delete the old photo from storage. It may be cleaned up later.", exception);
        }
    }

    @Override
    public Optional<StoredPhoto> load(String objectKey) {
        if (!MenuImageStorage.isValidKey(objectKey)) {
            return Optional.empty();
        }
        try {
            ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            String type = bytes.response().contentType();
            return Optional.of(new StoredPhoto(bytes.asByteArray(), isBlank(type) ? MenuImageUploads.contentTypeOf(objectKey) : type));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (SdkException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photos can't be loaded right now. Try again.", unavailable);
        }
    }

    @Override
    public String urlFor(String objectKey) {
        return publicBaseUrl == null ? MenuImageStorage.backendUrl(objectKey) : publicBaseUrl + "/" + objectKey;
    }

    @Override
    public Optional<String> keyOf(String url) {
        if (url != null && publicBaseUrl != null && url.startsWith(publicBaseUrl + "/")) {
            String key = url.substring(publicBaseUrl.length() + 1);
            return MenuImageStorage.isValidKey(key) ? Optional.of(key) : Optional.empty();
        }
        return MenuImageStorage.keyFromBackendUrl(url);
    }

    @Override
    public void destroy() {
        client.close();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
