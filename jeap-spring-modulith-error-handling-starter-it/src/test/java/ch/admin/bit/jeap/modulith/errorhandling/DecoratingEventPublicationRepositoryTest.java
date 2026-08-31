package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecoratingEventPublicationRepositoryTest {

    @Test
    void loadsOnlySelectedPublicationsWithoutUnboundedDelegateLookup() {
        EventPublicationRepository delegate = mock(EventPublicationRepository.class);
        JdbcModulithPublicationRepository jdbcRepository = mock(JdbcModulithPublicationRepository.class);
        ModulithErrorHandlingProperties properties = new ModulithErrorHandlingProperties();
        PublicationGeneration generation = new PublicationGeneration(UUID.randomUUID(), 2);
        TargetEventPublication publication = mock(TargetEventPublication.class);
        when(jdbcRepository.findRetryable(any(Integer.class), any(Instant.class), any(Long.class)))
                .thenReturn(List.of(generation));
        when(jdbcRepository.findFailedPublications(List.of(generation))).thenReturn(List.of(publication));
        PublicationSelectionContext context = new PublicationSelectionContext(jdbcRepository, properties);
        DecoratingEventPublicationRepository repository = new DecoratingEventPublicationRepository(
                delegate, new PublicationFailureCaptureContext(), context, jdbcRepository);

        context.retryable(() -> assertThat(repository.findFailedPublications(
                EventPublicationRepository.FailedCriteria.ALL
                        .withPublicationsPublishedBefore(Instant.parse("2026-08-27T10:00:00Z"))
                        .withItemsToRead(10)))
                .containsExactly(publication));

        verify(delegate, never()).findByStatus(any());
    }

    @Test
    void generationAwareClaimDoesNotFallBackToDelegateWhenCasLosesRace() {
        EventPublicationRepository delegate = mock(EventPublicationRepository.class);
        JdbcModulithPublicationRepository jdbcRepository = mock(JdbcModulithPublicationRepository.class);
        ModulithErrorHandlingProperties properties = new ModulithErrorHandlingProperties();
        PublicationGeneration generation = new PublicationGeneration(UUID.randomUUID(), 2);
        when(jdbcRepository.findRetryable(any(Integer.class), any(), any(Long.class)))
                .thenReturn(List.of(generation));
        when(jdbcRepository.findFailedPublications(List.of(generation)))
                .thenReturn(List.of(mock(TargetEventPublication.class)));
        PublicationSelectionContext context = new PublicationSelectionContext(jdbcRepository, properties);
        DecoratingEventPublicationRepository repository = new DecoratingEventPublicationRepository(
                delegate, new PublicationFailureCaptureContext(), context, jdbcRepository);
        Instant now = Instant.parse("2026-08-27T10:00:00Z");

        context.retryable(() -> {
            repository.findFailedPublications(EventPublicationRepository.FailedCriteria.ALL);
            assertThat(repository.markResubmitted(generation.publicationId(), now)).isFalse();
        });

        verify(jdbcRepository).markResubmitted(generation, now, properties.getMaxCompletionAttempts());
        verify(delegate, never()).markResubmitted(any(), any());
    }
}
