package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Spring Modulith publication error handling.
 */
@ConfigurationProperties("jeap.modulith.error-handling")
public class ModulithErrorHandlingProperties {

    /**
     * Whether Spring Modulith publication error handling is enabled.
     */
    private boolean enabled = true;

    private Duration retryInterval = Duration.ofSeconds(30);

    private Duration retryInitialDelay = Duration.ZERO;

    private Duration retryLockAtLeast = Duration.ofSeconds(5);

    private Duration retryLockAtMost = Duration.ofMinutes(5);

    private Duration retryMinAge = Duration.ofSeconds(30);

    private int maxCompletionAttempts = 3;

    private Duration reconciliationInterval = Duration.ofMinutes(5);

    private Duration reconciliationInitialDelay = Duration.ZERO;

    private Duration reconciliationLockAtLeast = Duration.ofSeconds(5);

    private Duration reconciliationLockAtMost = Duration.ofMinutes(30);

    private Duration reconciliationMinAge = Duration.ofMinutes(1);

    private int batchSize = 100;

    private int maxPayloadBytes = 256 * 1024;

    private String failureEventTopic;

    private String retryCommandTopic;

    private String discardCommandTopic;

    private String publicationTable = "event_publication";

    private String escalationTable = "modulith_publication_failure";

    /**
     * Creates properties with error handling enabled.
     */
    public ModulithErrorHandlingProperties() {
    }

    /**
     * Returns whether error handling is enabled.
     *
     * @return whether error handling is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables error handling.
     *
     * @param enabled whether error handling is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Duration getRetryInitialDelay() {
        return retryInitialDelay;
    }

    public void setRetryInitialDelay(Duration retryInitialDelay) {
        this.retryInitialDelay = retryInitialDelay;
    }

    public Duration getRetryLockAtLeast() {
        return retryLockAtLeast;
    }

    public void setRetryLockAtLeast(Duration retryLockAtLeast) {
        this.retryLockAtLeast = retryLockAtLeast;
    }

    public Duration getRetryLockAtMost() {
        return retryLockAtMost;
    }

    public void setRetryLockAtMost(Duration retryLockAtMost) {
        this.retryLockAtMost = retryLockAtMost;
    }

    public Duration getRetryMinAge() {
        return retryMinAge;
    }

    public void setRetryMinAge(Duration retryMinAge) {
        this.retryMinAge = retryMinAge;
    }

    public int getMaxCompletionAttempts() {
        return maxCompletionAttempts;
    }

    public void setMaxCompletionAttempts(int maxCompletionAttempts) {
        this.maxCompletionAttempts = maxCompletionAttempts;
    }

    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }

    public Duration getReconciliationInitialDelay() {
        return reconciliationInitialDelay;
    }

    public void setReconciliationInitialDelay(Duration reconciliationInitialDelay) {
        this.reconciliationInitialDelay = reconciliationInitialDelay;
    }

    public Duration getReconciliationLockAtLeast() {
        return reconciliationLockAtLeast;
    }

    public void setReconciliationLockAtLeast(Duration reconciliationLockAtLeast) {
        this.reconciliationLockAtLeast = reconciliationLockAtLeast;
    }

    public Duration getReconciliationLockAtMost() {
        return reconciliationLockAtMost;
    }

    public void setReconciliationLockAtMost(Duration reconciliationLockAtMost) {
        this.reconciliationLockAtMost = reconciliationLockAtMost;
    }

    public Duration getReconciliationMinAge() {
        return reconciliationMinAge;
    }

    public void setReconciliationMinAge(Duration reconciliationMinAge) {
        this.reconciliationMinAge = reconciliationMinAge;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public String getFailureEventTopic() {
        return failureEventTopic;
    }

    public void setFailureEventTopic(String failureEventTopic) {
        this.failureEventTopic = failureEventTopic;
    }

    public String getRetryCommandTopic() {
        return retryCommandTopic;
    }

    public void setRetryCommandTopic(String retryCommandTopic) {
        this.retryCommandTopic = retryCommandTopic;
    }

    public String getDiscardCommandTopic() {
        return discardCommandTopic;
    }

    public void setDiscardCommandTopic(String discardCommandTopic) {
        this.discardCommandTopic = discardCommandTopic;
    }

    public String getPublicationTable() {
        return publicationTable;
    }

    public void setPublicationTable(String publicationTable) {
        this.publicationTable = publicationTable;
    }

    public String getEscalationTable() {
        return escalationTable;
    }

    public void setEscalationTable(String escalationTable) {
        this.escalationTable = escalationTable;
    }
}
