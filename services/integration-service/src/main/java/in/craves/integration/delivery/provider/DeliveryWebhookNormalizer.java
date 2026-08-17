package in.craves.integration.delivery.provider;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderStatusUpdate;

/**
 * Provider-specific, network-free webhook normalization contract.
 */
public interface DeliveryWebhookNormalizer {
    String providerId();

    ProviderStatusUpdate normalize(JsonNode payload);
}
