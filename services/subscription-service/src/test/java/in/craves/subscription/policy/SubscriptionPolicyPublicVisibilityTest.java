package in.craves.subscription.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.subscription.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionPolicyPublicVisibilityTest {
    private final SubscriptionPolicyRepository repository = mock(SubscriptionPolicyRepository.class);
    private final SubscriptionPolicyService service = new SubscriptionPolicyService(repository);

    @Test
    void publicPolicyLookupUsesPlanStatusGatedRepositoryQuery() {
        UUID planId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        when(repository.findPublicActive(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicActive(planId))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).getCode())
            .isEqualTo("SUBSCRIPTION_POLICY_NOT_FOUND");

        verify(repository).findPublicActive(planId);
    }
}
