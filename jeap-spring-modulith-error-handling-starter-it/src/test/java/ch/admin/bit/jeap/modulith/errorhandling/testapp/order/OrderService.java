package ch.admin.bit.jeap.modulith.errorhandling.testapp.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Publishes {@link OrderCompleted} inside a transaction, which is what makes Spring Modulith write the
 * {@code event_publication} row the starter later operates on.
 */
@Service
public class OrderService {

    private final ApplicationEventPublisher events;

    OrderService(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Transactional
    public UUID completeOrder() {
        UUID orderId = UUID.randomUUID();
        events.publishEvent(new OrderCompleted(orderId));
        return orderId;
    }
}
