package pos.pos.menu.image;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
/** Slow object-storage retries must not stall booking reminders and expiry jobs. */
@Configuration
public class ImageCleanupSchedulingConfiguration {
 @Bean(name="taskScheduler")
 ThreadPoolTaskScheduler taskScheduler(@Value("${spring.task.scheduling.pool.size:1}") int size){
  return scheduler(Math.max(1,size),"pos-scheduled-");
 }
 @Bean(name="storedImageCleanupScheduler")
 ThreadPoolTaskScheduler storedImageCleanupScheduler(){return scheduler(1,"image-cleanup-");}
 private ThreadPoolTaskScheduler scheduler(int size,String name){
  var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(size);scheduler.setThreadNamePrefix(name);return scheduler;
 }
}
