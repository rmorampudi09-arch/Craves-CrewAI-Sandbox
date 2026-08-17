package in.craves.notification.delivery;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.PollResponse;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryResult;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.notification.delivery", name = "email-enabled", havingValue = "true")
public class AcsEmailAdapter {
    private final EmailClient emailClient;
    private final NotificationDeliveryProperties properties;
    private final AuthRecipientEmailResolver recipientEmailResolver;

    public AcsEmailAdapter(
        EmailClient emailClient,
        NotificationDeliveryProperties properties,
        AuthRecipientEmailResolver recipientEmailResolver
    ) {
        this.emailClient = emailClient;
        this.properties = properties;
        this.recipientEmailResolver = recipientEmailResolver;
    }

    public DeliveryResult send(DeliveryWorkItem item) {
        String recipient = recipientEmailResolver.resolve(item);
        EmailMessage message = new EmailMessage()
            .setSenderAddress(properties.getAcsEmailSenderAddress())
            .setToRecipients(recipient)
            .setSubject(item.title())
            .setBodyPlainText(item.body());
        if (StringUtils.hasText(properties.getAcsEmailReplyToAddress())) {
            message.setReplyTo(new EmailAddress(properties.getAcsEmailReplyToAddress().trim()));
        }
        PollResponse<EmailSendResult> response = emailClient.beginSend(message).waitForCompletion();
        EmailSendResult result = response.getValue();
        if (result == null || result.getId() == null) {
            throw new IllegalStateException("ACS Email did not return an operation identifier");
        }
        return new DeliveryResult("azure-communication-services-email", result.getId());
    }
}
