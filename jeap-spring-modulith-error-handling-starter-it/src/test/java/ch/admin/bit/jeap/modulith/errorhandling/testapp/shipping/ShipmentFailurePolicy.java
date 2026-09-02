package ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets a test decide how often the shipping listener fails before it succeeds, and records every
 * invocation. The asynchronous and synchronous listener tests share this component, so all state here is thread safe.
 */
@Component
public class ShipmentFailurePolicy {

    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final List<UUID> invocations = new CopyOnWriteArrayList<>();
    private final List<Thread> invocationThreads = new CopyOnWriteArrayList<>();

    /**
     * Makes the listener fail on every invocation.
     */
    public void failAlways() {
        failuresRemaining.set(Integer.MAX_VALUE);
    }

    /**
     * Makes the listener fail the given number of times and succeed afterwards.
     */
    public void failTimes(int times) {
        failuresRemaining.set(times);
    }

    public void succeedAlways() {
        failuresRemaining.set(0);
    }

    public void reset() {
        failuresRemaining.set(0);
        invocations.clear();
        invocationThreads.clear();
    }

    /**
     * Every invocation of the listener, in order, including the ones that failed.
     */
    public List<UUID> invocations() {
        return List.copyOf(invocations);
    }

    public int invocationCount() {
        return invocations.size();
    }

    public List<Thread> invocationThreads() {
        return List.copyOf(invocationThreads);
    }

    void recordAndMaybeFail(UUID orderId) {
        invocations.add(orderId);
        invocationThreads.add(Thread.currentThread());
        if (failuresRemaining.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0) {
            throw new ShipmentFailedException("Shipping of order %s failed".formatted(orderId));
        }
    }
}
