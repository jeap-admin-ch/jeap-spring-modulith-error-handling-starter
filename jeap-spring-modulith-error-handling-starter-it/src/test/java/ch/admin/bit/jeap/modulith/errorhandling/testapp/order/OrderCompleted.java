package ch.admin.bit.jeap.modulith.errorhandling.testapp.order;

import java.util.UUID;

/**
 * Event published by the {@code order} module and consumed asynchronously by the {@code shipping} module.
 */
public record OrderCompleted(UUID orderId) {
}
