package ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping;

import ch.admin.bit.jeap.modulith.errorhandling.testapp.order.PlainOrderCompleted;
import ch.admin.bit.jeap.modulith.errorhandling.testapp.order.SynchronousOrderCompleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Exercises the supported persistent synchronous listener and the unsupported plain listener boundary.
 */
@Component
public class SynchronousShippingListener {

    private final ShipmentFailurePolicy failurePolicy;

    SynchronousShippingListener(ShipmentFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    // AFTER_COMMIT can still see the completed publisher transaction's resources, so listener work needs its own transaction.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(SynchronousOrderCompleted event) {
        failurePolicy.recordAndMaybeFail(event.orderId());
    }

    @EventListener
    void on(PlainOrderCompleted event) {
        failurePolicy.recordAndMaybeFail(event.orderId());
    }
}
