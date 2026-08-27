package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ModulithPublicationFailureAdvisorTest {

    @Test
    void doesNotResolveEscalationServiceWhileAdvisorIsCreated() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ModulithPublicationEscalationService> escalationService = mock(ObjectProvider.class);

        new ModulithPublicationFailureAdvisor(new PublicationFailureCaptureContext(), escalationService);

        verifyNoInteractions(escalationService);
    }
}
