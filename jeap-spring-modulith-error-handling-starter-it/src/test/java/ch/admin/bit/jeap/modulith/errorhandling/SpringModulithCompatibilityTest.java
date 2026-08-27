package ch.admin.bit.jeap.modulith.errorhandling;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringModulithCompatibilityTest {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void registryMarksFailuresThroughDecoratedRepositoryByPublicationId() {
        EventPublicationRepository delegate = mock(EventPublicationRepository.class);
        when(delegate.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicBoolean delegateUpdated = new AtomicBoolean();
        AtomicReference<Object> observedIdentifier = new AtomicReference<>();
        doAnswer(invocation -> {
            delegateUpdated.set(true);
            return null;
        }).when(delegate).markFailed(any());

        ProxyFactory proxyFactory = new ProxyFactory(delegate);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("markFailed")) {
                assertThat(delegateUpdated).isTrue();
                observedIdentifier.set(invocation.getArguments()[0]);
            }
            return result;
        });

        EventPublicationRepository decorated = (EventPublicationRepository) proxyFactory.getProxy();
        DefaultEventPublicationRegistry registry = new DefaultEventPublicationRegistry(decorated, CLOCK);
        Object event = new Object();
        PublicationTargetIdentifier listener = PublicationTargetIdentifier.of("example.Listener.on(java.lang.Object)");

        TargetEventPublication publication = registry.store(event, java.util.stream.Stream.of(listener))
                .iterator().next();
        registry.markFailed(event, listener);

        assertThat(observedIdentifier).hasValue(publication.getIdentifier());
        verify(delegate).markFailed(publication.getIdentifier());
    }

    @Test
    void outerFailureAdvisorObservesFailureAfterCompletionAdvisorMarkedIt() {
        EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
        AtomicBoolean publicationMarkedFailed = new AtomicBoolean();
        AtomicBoolean outerAdvisorObservedFailedState = new AtomicBoolean();
        doAnswer(invocation -> {
            publicationMarkedFailed.set(true);
            return null;
        }).when(registry).markFailed(any(), any());

        CompletionRegisteringAdvisor completionAdvisor = new CompletionRegisteringAdvisor(() -> registry);
        DefaultPointcutAdvisor failureAdvisor = new DefaultPointcutAdvisor(
                completionAdvisor.getPointcut(),
                (MethodInterceptor) invocation -> {
                    try {
                        return invocation.proceed();
                    } catch (Throwable failure) {
                        outerAdvisorObservedFailedState.set(publicationMarkedFailed.get());
                        throw failure;
                    }
                });
        failureAdvisor.setOrder(Ordered.HIGHEST_PRECEDENCE);

        List<Advisor> advisors = new ArrayList<>(List.of(completionAdvisor, failureAdvisor));
        AnnotationAwareOrderComparator.sort(advisors);
        assertThat(advisors).containsExactly(failureAdvisor, completionAdvisor);

        ProxyFactory proxyFactory = new ProxyFactory(new FailingListener());
        proxyFactory.setProxyTargetClass(true);
        advisors.forEach(proxyFactory::addAdvisor);
        FailingListener listener = (FailingListener) proxyFactory.getProxy();

        assertThatThrownBy(() -> listener.on("event"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("listener failed");
        assertThat(outerAdvisorObservedFailedState).isTrue();
    }

    @Test
    void failedPublicationFilterClaimsOnlyTheMatchingPublicationId() {
        EventPublicationRepository repository = mock(EventPublicationRepository.class);
        TargetEventPublication first = publication("first");
        TargetEventPublication target = publication("target");
        when(repository.findFailedPublications(any())).thenReturn(List.of(first, target));
        when(repository.markResubmitted(eq(target.getIdentifier()), any())).thenReturn(true);

        DefaultEventPublicationRegistry registry = new DefaultEventPublicationRegistry(repository, CLOCK);
        List<TargetEventPublication> resubmitted = new ArrayList<>();
        registry.processFailedPublications(
                ResubmissionOptions.defaults()
                        .withFilter(publication -> publication.getIdentifier().equals(target.getIdentifier())),
                resubmitted::add);

        assertThat(resubmitted).containsExactly(target);
        verify(repository).markResubmitted(eq(target.getIdentifier()), eq(NOW));
        verify(repository, never()).markResubmitted(eq(first.getIdentifier()), any());
    }

    @Test
    void failedPublicationFilterCannotFindAnIdentifierOutsideTheSelectedBatch() {
        EventPublicationRepository repository = mock(EventPublicationRepository.class);
        List<TargetEventPublication> publications = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> publication("event-" + index))
                .toList();
        TargetEventPublication target = publications.getLast();

        when(repository.findFailedPublications(any())).thenAnswer(invocation -> {
            EventPublicationRepository.FailedCriteria criteria = invocation.getArgument(0);
            return publications.subList(0, Math.toIntExact(criteria.getMaxItemsToRead()));
        });

        DefaultEventPublicationRegistry registry = new DefaultEventPublicationRegistry(repository, CLOCK);
        List<TargetEventPublication> resubmitted = new ArrayList<>();
        registry.processFailedPublications(
                ResubmissionOptions.defaults()
                        .withFilter(publication -> publication.getIdentifier().equals(target.getIdentifier())),
                resubmitted::add);

        assertThat(resubmitted).isEmpty();
        verify(repository, never()).markResubmitted(eq(target.getIdentifier()), any());
    }

    private static TargetEventPublication publication(String event) {
        return TargetEventPublication.of(event, PublicationTargetIdentifier.of("example.Listener.on"), NOW);
    }

    static class FailingListener {

        @TransactionalEventListener
        public void on(String event) {
            throw new IllegalStateException("listener failed");
        }
    }
}
