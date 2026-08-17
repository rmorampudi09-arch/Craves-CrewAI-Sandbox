package in.craves.order.subscription;

import in.craves.order.subscription.SubscriptionOrderModels.CallbackRecord;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "craves.subscription-orders", name = "callback-worker-enabled", havingValue = "true")
public class SubscriptionOrderCallbackWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionOrderCallbackWorker.class);
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final SubscriptionOrderProperties properties;
    private final SubscriptionOrderRepository repository;
    private final RestClient subscriptionClient;

    public SubscriptionOrderCallbackWorker(
        SubscriptionOrderProperties properties,
        SubscriptionOrderRepository repository,
        RestClient.Builder builder
    ) {
        this.properties = properties;
        this.repository = repository;
        this.subscriptionClient = builder.baseUrl(properties.getSubscriptionServiceBaseUrl()).build();
    }

    @Scheduled(fixedDelayString = "${craves.subscription-orders.callback-fixed-delay-ms:5000}")
    public void deliver() {
        for (CallbackRecord callback : repository.claimCallbacks(
            properties.getCallbackBatchSize(),
            properties.getCallbackMaxAttempts(),
            properties.getCallbackStaleMinutes()
        )) {
            deliverOne(callback);
        }
    }

    private void deliverOne(CallbackRecord callback) {
        try {
            subscriptionClient.post()
                .uri(
                    "/internal/v1/subscription-occurrences/{occurrenceId}/order-created",
                    callback.occurrenceId()
                )
                .header(INTERNAL_HEADER, properties.getInternalAccessValue())
                .body(Map.of("orderId", callback.orderId()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException(
                        "Subscription callback returned HTTP " + response.getStatusCode().value()
                    );
                })
                .toBodilessEntity();
            repository.markCallbackDelivered(callback);
        } catch (RestClientResponseException exception) {
            repository.markCallbackFailure(callback, properties.getCallbackMaxAttempts(), exception);
            LOGGER.error(
                "Subscription order callback failed callbackId={} occurrenceId={} status={}",
                callback.id(), callback.occurrenceId(), exception.getStatusCode().value()
            );
        } catch (RuntimeException exception) {
            repository.markCallbackFailure(callback, properties.getCallbackMaxAttempts(), exception);
            LOGGER.error(
                "Subscription order callback failed callbackId={} occurrenceId={}",
                callback.id(), callback.occurrenceId(), exception
            );
        }
    }
}
