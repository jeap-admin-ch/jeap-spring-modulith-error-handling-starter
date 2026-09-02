package ch.admin.bit.jeap.modulith.errorhandling.testapp.order;

import java.util.UUID;

/**
 * Internal event consumed by a plain, non-persistent Spring event listener.
 */
public record PlainOrderCompleted(UUID orderId) {
}
