package pos.pos.menu.image;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persist cleanup before commit; execute only when the image's final references are visible. */
@Component
@RequiredArgsConstructor
public class MenuImageReleaser {
    private final MenuImageStorage storage;
    private final StoredImageCleanup cleanup;

    public void releaseAfterCommit(String url) {
        storage.keyOf(url).ifPresent(key -> {
            cleanup.enqueue(key,url);
            if(TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit(){cleanup.processKey(key);}
                });
            } else cleanup.processKey(key);
        });
    }
    public void discardOnRollback(String key) {
        cleanup.trackUpload(key);
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status){cleanup.processKey(key);}
            });
        }
    }
}
