package ch.admin.bit.jeap.modulith.errorhandling.testapp.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Publishes events for the synchronous listener integration tests.
 */
@Service
public class SynchronousOrderService {

    private final ApplicationEventPublisher events;

    SynchronousOrderService(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Transactional
    public UUID completeOrder() {
        UUID orderId = UUID.randomUUID();
        events.publishEvent(new SynchronousOrderCompleted(orderId));
        return orderId;
    }

    @Transactional
    public void completeOrderWithPlainListener() {
        events.publishEvent(new PlainOrderCompleted(UUID.randomUUID()));
    }
}
