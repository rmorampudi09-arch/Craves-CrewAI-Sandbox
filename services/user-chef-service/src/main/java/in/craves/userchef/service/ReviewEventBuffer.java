package in.craves.userchef.service;

import in.craves.userchef.web.ApiDtos.ChefApplicationResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewEventBuffer {
    private static final Logger log = LoggerFactory.getLogger(ReviewEventBuffer.class);
    private final ChefNoticeOutboxRepository repository;

    public ReviewEventBuffer(ChefNoticeOutboxRepository repository) {
        this.repository = repository;
    }

    public void accepted(ChefApplicationResponse application) {
        write(new ChefNoticeOutboxEvent(
            "chef-approved-" + application.id(),
            "CHEF_" + "APPROVED",
            "CHEF_APPLICATION",
            application.id(),
            application.identityId(),
            "CHEF",
            "IN_APP",
            "CHEF_" + "APPROVED_IN_APP",
            "Chef profile approved",
            "Your Craves chef profile is approved. You can now publish your kitchen and menu.",
            "CHEF_APPLICATION",
            application.id(),
            Map.of("applicationId", application.id().toString())
        ));
    }

    public void returned(ChefApplicationResponse application) {
        String reason = StringUtils.hasText(application.rejectionReason()) ? application.rejectionReason() : "Please review your application details and submit again.";
        write(new ChefNoticeOutboxEvent(
            "chef-rejected-" + application.id() + "-" + application.reviewedAt(),
            "CHEF_" + "REJECTED",
            "CHEF_APPLICATION",
            application.id(),
            application.identityId(),
            "CHEF",
            "IN_APP",
            "CHEF_" + "REJECTED_IN_APP",
            "Chef profile needs changes",
            "Your Craves chef profile was not approved. Reason: " + reason,
            "CHEF_APPLICATION",
            application.id(),
            Map.of("applicationId", application.id().toString(), "reason", reason)
        ));
    }

    private void write(ChefNoticeOutboxEvent event) {
        try {
            repository.savePending(event);
            log.info("Review event buffered eventKey={} eventType={} targetId={}", event.eventKey(), event.eventType(), event.targetId());
        } catch (RuntimeException ex) {
            log.warn("Review event buffer write failed eventKey={}: {}", event.eventKey(), ex.getMessage());
        }
    }
}
