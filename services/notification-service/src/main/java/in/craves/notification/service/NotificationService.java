package in.craves.notification.service;

import in.craves.notification.api.AppNoticeResponse;
import in.craves.notification.api.CreateNotificationRequest;
import in.craves.notification.api.NotificationRequestResponse;
import in.craves.notification.domain.NotificationChannel;
import in.craves.notification.domain.NotificationStatus;
import in.craves.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private static final String EMAIL_KEY_SUFFIX = "-email";

    private final NotificationRepository repository;
    private final ImportantEmailPolicyProperties importantEmailPolicy;

    public NotificationService(
        NotificationRepository repository,
        ImportantEmailPolicyProperties importantEmailPolicy
    ) {
        this.repository = repository;
        this.importantEmailPolicy = importantEmailPolicy;
    }

    @Transactional
    public NotificationRequestResponse create(CreateNotificationRequest request) {
        NotificationRequestResponse response = repository.findByRequestKey(request.requestKey())
            .orElseGet(() -> createNew(request));
        NotificationChannel channel = request.channel() == null
            ? NotificationChannel.IN_APP
            : request.channel();
        if (importantEmailPolicy.shouldFanOut(request, channel)) {
            createImportantEmailCopy(request);
        }
        return response;
    }

    private void createImportantEmailCopy(CreateNotificationRequest request) {
        String emailRequestKey = emailRequestKey(request.requestKey());
        if (repository.findByRequestKey(emailRequestKey).isPresent()) {
            return;
        }
        CreateNotificationRequest emailRequest = new CreateNotificationRequest(
            emailRequestKey,
            request.sourceService(),
            request.eventType(),
            request.userId(),
            request.userRole(),
            NotificationChannel.EMAIL,
            emailTemplateCode(request.templateCode()),
            null,
            request.title(),
            request.body(),
            request.targetType(),
            request.targetId(),
            request.payload(),
            request.priority()
        );
        createNew(emailRequest);
    }

    private NotificationRequestResponse createNew(CreateNotificationRequest request) {
        UUID requestId = UUID.randomUUID();
        NotificationChannel channel = request.channel() == null ? NotificationChannel.IN_APP : request.channel();
        if (channel == NotificationChannel.SMS) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Transactional SMS is not enabled; Firebase Phone Authentication remains the OTP channel"
            );
        }
        NotificationStatus initialStatus = channel == NotificationChannel.IN_APP
            ? NotificationStatus.SENT
            : NotificationStatus.PENDING;
        CreateNotificationRequest normalized = normalizeChannel(request, channel);
        NotificationRequestResponse created = repository.insertRequest(requestId, normalized, initialStatus);
        if (channel == NotificationChannel.IN_APP) {
            repository.createAppNotice(created.id(), normalized);
            repository.insertAttempt(
                created.id(),
                channel,
                "craves-in-app",
                1,
                NotificationStatus.SENT,
                null
            );
        }
        return repository.findByRequestKey(request.requestKey()).orElse(created);
    }

    public List<AppNoticeResponse> appNotices(UUID userId, int limit) {
        return repository.findAppNotices(userId, limit);
    }

    @Transactional
    public void markRead(UUID userId, UUID noticeId) {
        repository.markRead(userId, noticeId);
    }

    private static String emailRequestKey(String requestKey) {
        String normalized = requestKey.trim();
        int maximumBaseLength = 160 - EMAIL_KEY_SUFFIX.length();
        if (normalized.length() > maximumBaseLength) {
            normalized = normalized.substring(0, maximumBaseLength);
        }
        return normalized + EMAIL_KEY_SUFFIX;
    }

    private static String emailTemplateCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return null;
        }
        if (templateCode.endsWith("_IN_APP")) {
            return templateCode.substring(0, templateCode.length() - "_IN_APP".length()) + "_EMAIL";
        }
        return templateCode + "_EMAIL";
    }

    private static CreateNotificationRequest normalizeChannel(
        CreateNotificationRequest request,
        NotificationChannel channel
    ) {
        return new CreateNotificationRequest(
            request.requestKey(),
            request.sourceService(),
            request.eventType(),
            request.userId(),
            request.userRole(),
            channel,
            request.templateCode(),
            request.address(),
            request.title(),
            request.body(),
            request.targetType(),
            request.targetId(),
            request.payload(),
            request.priority()
        );
    }
}
