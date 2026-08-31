package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandReferences;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandReferences;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.modulith.events.FailedEventPublications;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModulithPublicationCommandListenerTest {

    private final FailedEventPublications failedPublications = mock(FailedEventPublications.class);
    private final JdbcModulithPublicationRepository repository = mock(JdbcModulithPublicationRepository.class);
    private final PublicationSelectionContext selectionContext = mock(PublicationSelectionContext.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final ModulithPublicationCommandListener listener = new ModulithPublicationCommandListener(
            failedPublications, selectionContext, repository, Clock.systemUTC());

    @Test
    void acknowledgesRetryWithoutRequiredFailureEventTokenAsNoOp() {
        listener.retry(retryCommand(UUID.randomUUID(), null), acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(repository, never()).findCommandTarget(any(), any());
        verify(failedPublications, never()).resubmit(any());
    }

    @Test
    void acknowledgesStaleOrDuplicateRetryAsNoOp() {
        UUID publicationId = UUID.randomUUID();
        when(repository.findCommandTarget(publicationId, "old-event")).thenReturn(Optional.empty());

        listener.retry(retryCommand(publicationId, "old-event"), acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(selectionContext, never()).exact(any(), any());
        verify(failedPublications, never()).resubmit(any());
    }

    @Test
    void retryUsesGenerationBoundToFailureEvent() {
        UUID publicationId = UUID.randomUUID();
        PublicationGeneration generation = new PublicationGeneration(publicationId, 4);
        when(repository.findCommandTarget(publicationId, "current-event")).thenReturn(Optional.of(generation));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(selectionContext).exact(eq(generation), any());

        listener.retry(retryCommand(publicationId, "current-event"), acknowledgment);

        verify(failedPublications).resubmit(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void redeliveryAfterFailedRequestedRetryCannotRetryNextGeneration() {
        UUID publicationId = UUID.randomUUID();
        PublicationGeneration generation = new PublicationGeneration(publicationId, 4);
        JdbcModulithPublicationRepository commandRepository = mock(JdbcModulithPublicationRepository.class);
        AtomicInteger deliveries = new AtomicInteger();
        when(commandRepository.findCommandTarget(publicationId, "current-event"))
                .thenAnswer(invocation -> deliveries.getAndIncrement() == 0
                        ? Optional.of(generation)
                        : Optional.empty());
        FailedEventPublications publications = mock(FailedEventPublications.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("retry failed"))
                .when(publications).resubmit(any());
        PublicationSelectionContext context = new PublicationSelectionContext(
                commandRepository, new ModulithErrorHandlingProperties());
        ModulithPublicationCommandListener commandListener = new ModulithPublicationCommandListener(
                publications, context, commandRepository, Clock.systemUTC());
        RetryModulithPublicationCommand command = retryCommand(publicationId, "current-event");

        assertThatThrownBy(() -> commandListener.retry(command, acknowledgment))
                .isInstanceOf(IllegalStateException.class);
        commandListener.retry(command, acknowledgment);

        verify(publications).resubmit(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void acknowledgesDiscardWithoutRequiredFailureEventTokenAsNoOp() {
        listener.discard(discardCommand(UUID.randomUUID(), null), acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(repository, never()).completeFailed(any(), any(), any());
    }

    @Test
    void acknowledgesStaleOrDuplicateDiscardWhenAtomicUpdateDoesNotMatch() {
        UUID publicationId = UUID.randomUUID();

        listener.discard(discardCommand(publicationId, "old-event"), acknowledgment);

        verify(repository).completeFailed(eq(publicationId), eq("old-event"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void listenerConsumerGroupsAreApplicationSpecific() throws Exception {
        Method retry = ModulithPublicationCommandListener.class.getDeclaredMethod(
                "retry", RetryModulithPublicationCommand.class, Acknowledgment.class);
        Method discard = ModulithPublicationCommandListener.class.getDeclaredMethod(
                "discard", DiscardModulithPublicationCommand.class, Acknowledgment.class);

        assertThat(retry.getAnnotation(KafkaListener.class).groupId())
                .contains("jeap.messaging.kafka.systemName", "spring.application.name");
        assertThat(discard.getAnnotation(KafkaListener.class).groupId())
                .contains("jeap.messaging.kafka.systemName", "spring.application.name");
    }

    private static RetryModulithPublicationCommand retryCommand(UUID publicationId, String failureEventId) {
        RetryModulithPublicationCommand command = new RetryModulithPublicationCommand();
        command.setReferences(new RetryModulithPublicationCommandReferences(
                new ch.admin.bit.jeap.modulith.command.retrypublication.ModulithPublicationReference(
                        "modulithPublication", publicationId.toString(), failureEventId)));
        return command;
    }

    private static DiscardModulithPublicationCommand discardCommand(UUID publicationId, String failureEventId) {
        DiscardModulithPublicationCommand command = new DiscardModulithPublicationCommand();
        command.setReferences(new DiscardModulithPublicationCommandReferences(
                new ch.admin.bit.jeap.modulith.command.discardpublication.ModulithPublicationReference(
                        "modulithPublication", publicationId.toString(), failureEventId)));
        command.setPayload(new DiscardModulithPublicationCommandPayload("operator request"));
        return command;
    }
}
