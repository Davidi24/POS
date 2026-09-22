package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import pos.pos.notification.enums.NotificationMutationType;

@Aspect
@Component
@RequiredArgsConstructor
class NotificationEntityChangeAspect {

    private final NotificationOperationalPublisher notificationOperationalPublisher;

    @AfterReturning(
            pointcut = """
                    execution(* pos.pos..repository..*.save(..)) ||
                    execution(* pos.pos..repository..*.saveAndFlush(..))
                    """,
            returning = "result"
    )
    public void afterSave(Object result) {
        notificationOperationalPublisher.publishEntityChange(result, NotificationMutationType.UPSERT);
    }

    @AfterReturning(
            pointcut = """
                    execution(* pos.pos..repository..*.saveAll(..)) ||
                    execution(* pos.pos..repository..*.saveAllAndFlush(..))
                    """,
            returning = "result"
    )
    public void afterSaveAll(Object result) {
        publishIterable(result, NotificationMutationType.UPSERT);
    }

    @Before("execution(* pos.pos..repository..*.delete(..)) && args(entity)")
    public void beforeDelete(Object entity) {
        notificationOperationalPublisher.publishEntityChange(entity, NotificationMutationType.DELETE);
    }

    @Before("execution(* pos.pos..repository..*.deleteAll(..)) && args(entities)")
    public void beforeDeleteAll(Object entities) {
        publishIterable(entities, NotificationMutationType.DELETE);
    }

    private void publishIterable(Object value, NotificationMutationType mutationType) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                notificationOperationalPublisher.publishEntityChange(item, mutationType);
            }
            return;
        }
        notificationOperationalPublisher.publishEntityChange(value, mutationType);
    }
}
