package ch.admin.bit.jeap.modulith.errorhandling.testapp.order;

import java.util.UUID;

/**
 * Internal event consumed by a synchronous, persistent after-commit listener.
 */
public record SynchronousOrderCompleted(UUID orderId) {
}
