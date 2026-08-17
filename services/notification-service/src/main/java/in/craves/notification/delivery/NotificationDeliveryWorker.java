package in.craves.notification.delivery;

import in.craves.notification.delivery.FcmPushAdapter.PushDeliveryException;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryResult;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import in.craves.notification.delivery.NotificationDeliveryModels.PushDevice;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "craves.notification.delivery", name = "worker-enabled", havingValue = "true")
public class NotificationDeliveryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationDeliveryProperties properties;
    private final NotificationDeliveryRepository repository;
    private final ObjectProvider<FcmPushAdapter> pushAdapter;
    private final ObjectProvider<AcsEmailAdapter> emailAdapter;

    public NotificationDeliveryWorker(
        NotificationDeliveryProperties properties,
        NotificationDeliveryRepository repository,
        ObjectProvider<FcmPushAdapter> pushAdapter,
        ObjectProvider<AcsEmailAdapter> emailAdapter
    ) {
        this.properties = properties;
        this.repository = repository;
        this.pushAdapter = pushAdapter;
        this.emailAdapter = emailAdapter;
    }

    @Scheduled(fixedDelayString = "${craves.notification.delivery.fixed-delay-ms:5000}")
    public void deliver() {
        if (properties.isPushEnabled()) {
            deliverPush();
        }
        if (properties.isEmailEnabled()) {
            deliverEmail();
        }
    }

    private void deliverPush() {
        FcmPushAdapter adapter = pushAdapter.getIfAvailable();
        if (adapter == null) {
            throw new IllegalStateException("FCM adapter is not available while push delivery is enabled");
        }
        for (DeliveryWorkItem item : repository.claim(
            "PUSH",
            properties.getBatchSize(),
            properties.getMaxAttempts(),
            properties.getStaleLockMinutes()
        )) {
            try {
                List<PushDevice> devices = repository.activePushDevices(item.recipientIdentityId());
                if (devices.isEmpty()) {
                    throw new IllegalStateException("Recipient has no active push device");
                }
                DeliveryResult successful = null;
                RuntimeException lastFailure = null;
                for (PushDevice device : devices) {
                    try {
                        DeliveryResult result = adapter.send(item, device);
                        if (successful == null) {
                            successful = result;
                        }
                    } catch (PushDeliveryException exception) {
                        lastFailure = exception;
                        if (exception.invalidToken()) {
                            repository.disablePushDevice(device.id(), exception.code());
                        }
                    }
                }
                if (successful == null) {
                    throw lastFailure == null
                        ? new IllegalStateException("Push delivery failed for every device")
                        : lastFailure;
                }
                repository.markSent(item, successful);
            } catch (RuntimeException exception) {
                repository.markFailure(
                    item,
                    properties.getMaxAttempts(),
                    properties.getRetryBaseSeconds(),
                    "firebase-cloud-messaging",
                    exception
                );
                LOGGER.warn(
                    "Push delivery failed requestId={} attempt={}",
                    item.requestId(), item.attemptCount()
                );
            }
        }
    }

    private void deliverEmail() {
        AcsEmailAdapter adapter = emailAdapter.getIfAvailable();
        if (adapter == null) {
            throw new IllegalStateException("ACS Email adapter is not available while email delivery is enabled");
        }
        for (DeliveryWorkItem item : repository.claim(
            "EMAIL",
            properties.getBatchSize(),
            properties.getMaxAttempts(),
            properties.getStaleLockMinutes()
        )) {
            try {
                repository.markSent(item, adapter.send(item));
            } catch (RuntimeException exception) {
                repository.markFailure(
                    item,
                    properties.getMaxAttempts(),
                    properties.getRetryBaseSeconds(),
                    "azure-communication-services-email",
                    exception
                );
                LOGGER.warn(
                    "Email delivery failed requestId={} attempt={}",
                    item.requestId(), item.attemptCount()
                );
            }
        }
    }
}
