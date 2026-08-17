package in.craves.notification.delivery;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationProviderConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "craves.notification.delivery", name = "push-enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(NotificationDeliveryProperties properties) throws Exception {
        byte[] json = Base64.getDecoder().decode(properties.getFirebaseServiceAccountJsonBase64());
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        FirebaseApp app = FirebaseApp.getApps().stream()
            .filter(existing -> "craves-notification".equals(existing.getName()))
            .findFirst()
            .orElseGet(() -> FirebaseApp.initializeApp(options, "craves-notification"));
        java.util.Arrays.fill(json, (byte) 0);
        return FirebaseMessaging.getInstance(app);
    }

    @Bean
    @ConditionalOnProperty(prefix = "craves.notification.delivery", name = "email-enabled", havingValue = "true")
    EmailClient acsEmailClient(NotificationDeliveryProperties properties) {
        return new EmailClientBuilder()
            .connectionString(properties.getAcsEmailConnectionString())
            .buildClient();
    }
}
