package ch.admin.bit.jeap.modulith.errorhandling;

import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;

final class ModulithPublicationFailureAdvisor extends DefaultPointcutAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(ModulithPublicationFailureAdvisor.class);

    ModulithPublicationFailureAdvisor(PublicationFailureCaptureContext captureContext,
            ObjectProvider<ModulithPublicationEscalationService> escalationService) {
        super(new CompletionRegisteringAdvisor(() -> {
            throw new IllegalStateException("Pointcut-only advisor supplier must not be invoked");
        }).getPointcut(), (MethodInterceptor) invocation -> {
            captureContext.begin();
            try {
                return invocation.proceed();
            } catch (Throwable failure) {
                captureContext.failedPublicationId().ifPresent(publicationId -> {
                    try {
                        escalationService.getObject().escalate(publicationId, failure);
                    } catch (RuntimeException escalationFailure) {
                        LOG.warn("Immediate escalation of Modulith publication {} failed; reconciliation will retry it.",
                                publicationId, escalationFailure);
                    }
                });
                throw failure;
            } finally {
                captureContext.clear();
            }
        });
        setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    }
}
