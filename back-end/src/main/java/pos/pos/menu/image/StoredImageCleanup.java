package pos.pos.menu.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

/** Durable, retryable deletion. All image consumers share this reference check. */
@Service
public class StoredImageCleanup {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(StoredImageCleanup.class);
    private final JdbcTemplate jdbc;
    private final MenuImageStorage storage;
    private final String schema;
    private final TransactionTemplate independent;
    public StoredImageCleanup(JdbcTemplate jdbc, MenuImageStorage storage, PlatformTransactionManager transactions,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc=jdbc; this.storage=storage; this.schema="\""+schema+"\".";
        independent=new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** Called in the transaction dropping a reference; rollback also removes this request. */
    public void enqueue(String key, String url) {
        jdbc.update("insert into "+schema+"stored_image_cleanup (object_key,image_url) values (?,?) "
                +"on conflict (object_key) do update set next_attempt_at=now()",key,url);
    }
    /** Record a new object independently before the owning transaction can fail or the process can stop. */
    public void trackUpload(String key) {
        independent.executeWithoutResult(tx -> jdbc.update("insert into "+schema
                +"stored_image_cleanup (object_key,image_url,next_attempt_at) values (?,?,now()+interval '10 minutes') "
                +"on conflict (object_key) do nothing",key,storage.urlFor(key)));
    }
    public long references(String url) {
        String key=storage.keyOf(url).orElse(null);
        String backend=key==null?url:MenuImageStorage.backendUrl(key);
        String current=key==null?url:storage.urlFor(key);
        String condition=" in (?,?,?)";
        Long count=jdbc.queryForObject("select (select count(*) from "+schema+"\"menu-items\" where image_url"+condition+")"
                +"+(select count(*) from "+schema+"\"restaurant-branding\" where logo_url"+condition+")"
                +"+(select count(*) from "+schema+"restaurants where cover_url"+condition+")"
                +"+(select count(*) from "+schema+"branches where cover_url"+condition+")"
                +"+(select count(*) from "+schema+"inventory_items where image_url"+condition+")"
                +"+(select count(*) from "+schema+"inventory_locations where image_url"+condition+")"
                +"+(select count(*) from "+schema+"recipes where image_url"+condition+")"
                +"+(select count(*) from "+schema+"order_receipt_identities where identity_json->>'logoUrl'"+condition+")",
                Long.class,url,backend,current,url,backend,current,url,backend,current,url,backend,current,url,backend,current,url,backend,current,url,backend,current,url,backend,current);
        return count==null?0:count;
    }
    /** Post-commit cleanup must never turn a committed save into an HTTP failure. */
    public void processKey(String key) {
        try { processKeyInTransaction(key); }
        catch(RuntimeException failure) {
            log.warn("Image cleanup deferred after database failure ({})",failure.getClass().getSimpleName());
        }
    }
    private void processKeyInTransaction(String key) {
        independent.executeWithoutResult(tx -> {
            List<String> urls=jdbc.queryForList("select image_url from "+schema
                    +"stored_image_cleanup where object_key=? for update skip locked",String.class,key);
            if(urls.isEmpty())return;
            try {
                if(references(urls.getFirst())==0)storage.delete(key);
                jdbc.update("delete from "+schema+"stored_image_cleanup where object_key=?",key);
            } catch (RuntimeException failure) {
                // Keep the durable request even when the provider is unavailable. Cap retries at once an hour.
                String message=failure.getClass().getSimpleName();
                jdbc.update("update "+schema+"stored_image_cleanup set attempts=attempts+1,last_error=?,"
                        +"next_attempt_at=now()+least(3600,30*power(2,least(attempts,7))) * interval '1 second' where object_key=?",message,key);
            }
        });
    }
    @Scheduled(scheduler="storedImageCleanupScheduler",fixedDelayString="${app.storage.cleanup.poll-ms:60000}",initialDelayString="${app.storage.cleanup.initial-delay-ms:60000}")
    public void retryPending() {
        for(String key:jdbc.queryForList("select object_key from "+schema
                +"stored_image_cleanup where next_attempt_at<=now() order by next_attempt_at limit 50",String.class))processKey(key);
    }
}
