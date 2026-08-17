package in.craves.notification.delivery;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryResult;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import in.craves.notification.delivery.NotificationDeliveryModels.PushDevice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.notification.delivery", name = "push-enabled", havingValue = "true")
public class FcmPushAdapter {
    private final FirebaseMessaging messaging;

    public FcmPushAdapter(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public DeliveryResult send(DeliveryWorkItem item, PushDevice device) {
        Message.Builder builder = Message.builder()
            .setToken(device.deviceToken())
            .setNotification(Notification.builder()
                .setTitle(item.title())
                .setBody(item.body())
                .build());
        if (StringUtils.hasText(item.targetType())) {
            builder.putData("targetType", item.targetType());
        }
        if (item.targetId() != null) {
            builder.putData("targetId", item.targetId().toString());
        }
        try {
            return new DeliveryResult("firebase-cloud-messaging", messaging.send(builder.build()));
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            throw new PushDeliveryException(
                code == null ? "UNKNOWN" : code.name(),
                code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT,
                exception
            );
        }
    }

    public static final class PushDeliveryException extends RuntimeException {
        private final String code;
        private final boolean invalidToken;

        PushDeliveryException(String code, boolean invalidToken, Throwable cause) {
            super("FCM delivery failed with code " + code, cause);
            this.code = code;
            this.invalidToken = invalidToken;
        }

        public String code() { return code; }
        public boolean invalidToken() { return invalidToken; }
    }
}
