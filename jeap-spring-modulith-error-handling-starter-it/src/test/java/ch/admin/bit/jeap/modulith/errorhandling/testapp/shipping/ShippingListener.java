package ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping;

import ch.admin.bit.jeap.modulith.errorhandling.testapp.order.OrderCompleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The listener the starter is built for: {@code @ApplicationModuleListener} is Spring Modulith's shortcut
 * for {@code @Async @Transactional(REQUIRES_NEW) @TransactionalEventListener}. It runs on another thread
 * after the publishing transaction committed, so an exception escaping it never reaches the publisher and
 * only leaves a failed {@code event_publication} row behind.
 */
@Component
public class ShippingListener {

    private final ShipmentFailurePolicy failurePolicy;

    ShippingListener(ShipmentFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    @ApplicationModuleListener
    void on(OrderCompleted event) {
        failurePolicy.recordAndMaybeFail(event.orderId());
    }
}
