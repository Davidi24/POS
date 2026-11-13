package pos.pos.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** A slow or unreachable mail server must not hold up booking reminders and the other scheduled jobs. */
@Configuration
public class EmailOutboxSchedulingConfiguration {

    @Bean(name = "emailOutboxScheduler")
    ThreadPoolTaskScheduler emailOutboxScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("email-outbox-");
        return scheduler;
    }
}
