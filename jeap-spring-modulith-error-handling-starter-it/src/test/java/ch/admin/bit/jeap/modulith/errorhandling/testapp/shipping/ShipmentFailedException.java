package ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping;

/**
 * Thrown by the shipping listener to simulate a failing module.
 */
public class ShipmentFailedException extends RuntimeException {

    public ShipmentFailedException(String message) {
        super(message);
    }
}
