package in.craves.auth.admin;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import in.craves.auth.admin.AdminAccountInterventionRepository.ProviderWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.admin-account-intervention",
    name = "firebase-worker-enabled",
    havingValue = "true"
)
public class FirebaseAccountInterventionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseAccountInterventionWorker.class);

    private final FirebaseApp firebaseApp;
    private final AdminAccountInterventionProperties properties;
    private final AdminAccountInterventionRepository repository;

    public FirebaseAccountInterventionWorker(
        FirebaseApp firebaseApp,
        AdminAccountInterventionProperties properties,
        AdminAccountInterventionRepository repository
    ) {
        this.firebaseApp = firebaseApp;
        this.properties = properties;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${craves.admin-account-intervention.worker-fixed-delay-ms:5000}")
    public void process() {
        for (ProviderWorkItem item : repository.claimProviderWork(
            properties.getBatchSize(), properties.getMaxAttempts(), properties.getStaleLockMinutes()
        )) {
            try {
                if (item.firebaseUid() == null || item.firebaseUid().isBlank()) {
                    throw new IllegalStateException("Firebase UID is missing for the target identity");
                }

                boolean disabled = repository.currentProviderDisabled(item.identityId());
                FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(firebaseApp);
                firebaseAuth.updateUser(
                    new UserRecord.UpdateRequest(item.firebaseUid()).setDisabled(disabled)
                );
                if (disabled) {
                    firebaseAuth.revokeRefreshTokens(item.firebaseUid());
                }
                repository.markProviderCompleted(item);
                LOGGER.info(
                    "Firebase account state synchronized interventionId={} identityId={} requestedAction={} appliedDisabled={}",
                    item.interventionId(), item.identityId(), item.action(), disabled
                );
            } catch (Exception exception) {
                repository.markProviderFailure(
                    item, properties.getMaxAttempts(), properties.getRetryBaseSeconds(), exception
                );
                LOGGER.error(
                    "Firebase account intervention failed interventionId={} identityId={} attempt={}",
                    item.interventionId(), item.identityId(), item.attemptCount(), exception
                );
            }
        }
    }
}
