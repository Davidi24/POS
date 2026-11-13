package pos.pos.integration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.menu.image.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Durable queue tested with real commits/rollbacks; provider failures are deterministic. */
class StoredImageCleanupIntegrationTest extends AbstractPosApiIntegrationTest {
 @MockBean MenuImageStorage storage;
 @Autowired StoredImageCleanup cleanup;
 @Autowired MenuImageReleaser releaser;
 @Autowired PlatformTransactionManager transactions;
 @BeforeEach void urls(){
  when(storage.urlFor(anyString())).thenAnswer(a->MenuImageStorage.backendUrl(a.getArgument(0)));
  when(storage.keyOf(anyString())).thenAnswer(a->MenuImageStorage.keyFromBackendUrl(a.getArgument(0)));
 }
 private int queued(String key){return jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".stored_image_cleanup where object_key=?",Integer.class,key);}
 @Test void sharedDishLogoRestaurantAndBranchReferencesProtectOneObject()throws Exception{
  var w=newWorld("image-shared");var m=menu(w,"Photos");var item=item(w,m,"Soup","10");String key=MenuImageStorage.newKey(w.restaurantId(),".png"),url=storage.urlFor(key);
  jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=? where id=?",url,w.restaurantId());
  jdbcTemplate.update("update "+SCHEMA+".branches set cover_url=? where id=?",url,w.branchId());
  jdbcTemplate.update("update "+SCHEMA+".\"menu-items\" set image_url=? where id=?",url,item);
  jdbcTemplate.update("insert into "+SCHEMA+".\"restaurant-branding\" (id,restaurant_id,logo_url,created_at,updated_at) values (?,?,?,now(),now())",UUID.randomUUID(),w.restaurantId(),url);
  assertThat(cleanup.references(url)).isEqualTo(4);
  cleanup.enqueue(key,url);cleanup.processKey(key);verify(storage,never()).delete(key);assertThat(queued(key)).isZero();
  jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=null where id=?",w.restaurantId());
  jdbcTemplate.update("update "+SCHEMA+".branches set cover_url=null where id=?",w.branchId());
  jdbcTemplate.update("update "+SCHEMA+".\"menu-items\" set image_url=null where id=?",item);
  cleanup.enqueue(key,url);cleanup.processKey(key);verify(storage,never()).delete(key);
  jdbcTemplate.update("update "+SCHEMA+".\"restaurant-branding\" set logo_url=null where restaurant_id=?",w.restaurantId());
  cleanup.enqueue(key,url);cleanup.processKey(key);verify(storage).delete(key);assertThat(queued(key)).isZero();
 }
 @Test void failedDeletionIsDurableAndCanBeRetried()throws Exception{
  var w=newWorld("image-retry");String key=MenuImageStorage.newKey(w.restaurantId(),".png");
  doThrow(new IllegalStateException("simulated provider outage")).when(storage).delete(key);
  cleanup.enqueue(key,storage.urlFor(key));cleanup.processKey(key);
  assertThat(queued(key)).isEqualTo(1);
  assertThat(jdbcTemplate.queryForObject("select attempts from "+SCHEMA+".stored_image_cleanup where object_key=?",Integer.class,key)).isEqualTo(1);
  doNothing().when(storage).delete(key);cleanup.processKey(key);verify(storage,times(2)).delete(key);assertThat(queued(key)).isZero();
 }
 @Test void rollbackDoesNotDeletePreviousImageButDiscardsNewUpload()throws Exception{
  var w=newWorld("image-rollback");String old=MenuImageStorage.newKey(w.restaurantId(),".png"),fresh=MenuImageStorage.newKey(w.restaurantId(),".png");
  jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=? where id=?",storage.urlFor(old),w.restaurantId());
  new TransactionTemplate(transactions).executeWithoutResult(tx->{
   releaser.discardOnRollback(fresh);
   jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=? where id=?",storage.urlFor(fresh),w.restaurantId());
   releaser.releaseAfterCommit(storage.urlFor(old));tx.setRollbackOnly();
  });
  verify(storage).delete(fresh);verify(storage,never()).delete(old);assertThat(cleanup.references(storage.urlFor(old))).isEqualTo(1);
 }
 @Test void commitDeletesOldImageAndKeepsTheNewOne()throws Exception{
  var w=newWorld("image-commit");String old=MenuImageStorage.newKey(w.restaurantId(),".png"),fresh=MenuImageStorage.newKey(w.restaurantId(),".png");
  jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=? where id=?",storage.urlFor(old),w.restaurantId());
  new TransactionTemplate(transactions).executeWithoutResult(tx->{
   releaser.discardOnRollback(fresh);
   jdbcTemplate.update("update "+SCHEMA+".restaurants set cover_url=? where id=?",storage.urlFor(fresh),w.restaurantId());releaser.releaseAfterCommit(storage.urlFor(old));
  });
  verify(storage).delete(old);verify(storage,never()).delete(fresh);assertThat(queued(old)).isZero();assertThat(queued(fresh)).isZero();
 }
 @Test void abandonedUploadSurvivesQueueRestartAndIsReconciled()throws Exception{
  var w=newWorld("image-crash");String key=MenuImageStorage.newKey(w.restaurantId(),".png");cleanup.trackUpload(key);assertThat(queued(key)).isEqualTo(1);
  jdbcTemplate.update("update "+SCHEMA+".stored_image_cleanup set next_attempt_at=now() where object_key=?",key);
  // A fresh component reconstructs all pending work from PostgreSQL, without in-memory callbacks.
  var restarted=new StoredImageCleanup(jdbcTemplate,storage,transactions,SCHEMA);restarted.retryPending();verify(storage).delete(key);assertThat(queued(key)).isZero();
 }
}
