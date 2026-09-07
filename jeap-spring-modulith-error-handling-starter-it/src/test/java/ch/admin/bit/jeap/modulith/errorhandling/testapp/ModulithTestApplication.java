package ch.admin.bit.jeap.modulith.errorhandling.testapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A real Spring Boot application with a real Spring Modulith setup, used by the integration tests of the
 * starter.
 * <p>
 * The application deliberately lives in its own package so that component scanning picks up only the test
 * modules below it, while the tests themselves stay in the starter's package and can therefore reach its
 * package private types.
 */
@SpringBootApplication
public class ModulithTestApplication {
}
