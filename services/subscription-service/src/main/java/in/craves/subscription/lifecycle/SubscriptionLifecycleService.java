package in.craves.subscription.lifecycle;

import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.exception.ApiException;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.AdminSubscriptionPage;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.CustomerOccurrenceResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.ResumeSubscriptionRequest;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SkipRequestResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SkipSubscriptionDateRequest;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SubscriptionStatusHistoryResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleRepository.OwnedSubscription;
import in.craves.subscription.lifecycle.SubscriptionLifecycleRepository.ScheduleClock;
import in.craves.subscription.policy.SubscriptionPolicyModels.SubscriptionPolicyResponse;
import in.craves.subscription.policy.SubscriptionPolicyRepository;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.web.ApiDtos.CustomerSubscriptionResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionLifecycleService {
    private static final Set<String> ADMIN_STATUSES = Set.of(
        "PENDING_PAYMENT", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "EXPIRED", "CANCELLED"
    );

    private final SubscriptionLifecycleRepository lifecycleRepository;
    private final SubscriptionPolicyRepository policyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CapacityService capacityService;
    private final Clock clock;

    @Autowired
    public SubscriptionLifecycleService(
        SubscriptionLifecycleRepository lifecycleRepository,
        SubscriptionPolicyRepository policyRepository,
        SubscriptionRepository subscriptionRepository,
        CapacityService capacityService
    ) {
        this(lifecycleRepository, policyRepository, subscriptionRepository, capacityService, Clock.systemUTC());
    }

    SubscriptionLifecycleService(
        SubscriptionLifecycleRepository lifecycleRepository,
        SubscriptionPolicyRepository policyRepository,
        SubscriptionRepository subscriptionRepository,
        CapacityService capacityService,
        Clock clock
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.policyRepository = policyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.capacityService = capacityService;
        this.clock = clock;
    }

    @Transactional
    public CustomerSubscriptionResponse pause(UUID subscriptionId, String reason, CurrentUser user) {
        requireCustomer(user);
        OwnedSubscription subscription = owned(subscriptionId, user);
        if (!"ACTIVE".equals(subscription.status())) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_ACTIVE", "Only an active subscription can be paused");
        }
        SubscriptionPolicyResponse policy = activePolicy(subscription.planId());
        if (!policy.customerPauseEnabled()) {
            throw ApiException.forbidden("SUBSCRIPTION_PAUSE_DISABLED", "Pause is disabled by the active admin policy");
        }
        enforceNextServiceCutoff(subscription, policy.pauseCutoffMinutes(), "SUBSCRIPTION_PAUSE_CUTOFF");
        SubscriptionResponse full = requireSubscription(subscriptionId);
        if (!lifecycleRepository.pause(subscriptionId, user.identityId(), trim(reason))) {
            throw ApiException.conflict("SUBSCRIPTION_STATE_CHANGED", "Subscription state changed before pause could be applied");
        }
        capacityService.releaseForPauseOrTerminal(
            full, LocalDate.now(clock), "Customer paused the subscription"
        );
        return current(subscriptionId);
    }

    @Transactional
    public CustomerSubscriptionResponse cancel(UUID subscriptionId, String reason, CurrentUser user) {
        requireCustomer(user);
        OwnedSubscription subscription = owned(subscriptionId, user);
        if (!("ACTIVE".equals(subscription.status()) || "PAUSED".equals(subscription.status()))) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_CANCELLABLE", "Only an active or paused subscription can be cancelled");
        }
        SubscriptionPolicyResponse policy = activePolicy(subscription.planId());
        if (!policy.customerCancelEnabled()) {
            throw ApiException.forbidden("SUBSCRIPTION_CANCEL_DISABLED", "Cancellation is disabled by the active admin policy");
        }
        if ("ACTIVE".equals(subscription.status())) {
            enforceNextServiceCutoff(subscription, policy.cancelCutoffMinutes(), "SUBSCRIPTION_CANCEL_CUTOFF");
        }
        SubscriptionResponse full = requireSubscription(subscriptionId);
        if (!lifecycleRepository.cancel(subscriptionId, user.identityId(), trim(reason))) {
            throw ApiException.conflict("SUBSCRIPTION_STATE_CHANGED", "Subscription state changed before cancellation could be applied");
        }
        capacityService.releaseForPauseOrTerminal(
            full, LocalDate.now(clock), "Customer cancelled the subscription"
        );
        return current(subscriptionId);
    }

    @Transactional
    public CustomerSubscriptionResponse resume(
        UUID subscriptionId,
        ResumeSubscriptionRequest request,
        CurrentUser user
    ) {
        requireCustomer(user);
        OwnedSubscription subscription = owned(subscriptionId, user);
        if (!"PAUSED".equals(subscription.status())) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_PAUSED", "Only a paused subscription can be resumed");
        }
        SubscriptionPolicyResponse policy = activePolicy(subscription.planId());
        if (!policy.customerResumeEnabled()) {
            throw ApiException.forbidden("SUBSCRIPTION_RESUME_DISABLED", "Resume is disabled by the active admin policy");
        }
        if (request.resumeDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("INVALID_RESUME_DATE", "resumeDate cannot be in the past");
        }
        ScheduleClock schedule = activeSchedule(subscription.planId());
        Instant resumeAt = serviceAt(request.resumeDate(), schedule);
        enforceCutoff(resumeAt, policy.resumeLeadMinutes(), "SUBSCRIPTION_RESUME_LEAD");
        SubscriptionResponse full = requireSubscription(subscriptionId);
        capacityService.reacquireForResume(full, request.resumeDate());
        if (!lifecycleRepository.resume(
            subscriptionId,
            user.identityId(),
            request.resumeDate(),
            trim(request.reason())
        )) {
            throw ApiException.conflict("SUBSCRIPTION_STATE_CHANGED", "Subscription state changed before resume could be applied");
        }
        return current(subscriptionId);
    }

    @Transactional
    public SkipRequestResponse skip(
        UUID subscriptionId,
        SkipSubscriptionDateRequest request,
        CurrentUser user
    ) {
        requireCustomer(user);
        OwnedSubscription subscription = owned(subscriptionId, user);
        if (!"ACTIVE".equals(subscription.status())) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_ACTIVE", "Only an active subscription can skip a meal date");
        }
        SubscriptionPolicyResponse policy = activePolicy(subscription.planId());
        if (!policy.customerSkipEnabled()) {
            throw ApiException.forbidden("SUBSCRIPTION_SKIP_DISABLED", "Skip is disabled by the active admin policy");
        }
        if (request.serviceDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("INVALID_SKIP_DATE", "serviceDate cannot be in the past");
        }
        if (!lifecycleRepository.isScheduledServiceDate(subscription.planId(), request.serviceDate())) {
            throw ApiException.badRequest("NOT_A_SUBSCRIPTION_SERVICE_DATE", "The requested date is not part of the active meal schedule");
        }
        ScheduleClock schedule = activeSchedule(subscription.planId());
        Instant serviceAt = lifecycleRepository.findOccurrenceServiceAt(subscriptionId, request.serviceDate())
            .orElseGet(() -> serviceAt(request.serviceDate(), schedule));
        enforceCutoff(serviceAt, policy.skipCutoffMinutes(), "SUBSCRIPTION_SKIP_CUTOFF");
        try {
            SkipRequestResponse response = lifecycleRepository.requestSkip(
                subscriptionId,
                user.identityId(),
                request.serviceDate(),
                trim(request.reason())
            );
            capacityService.releaseForSkip(requireSubscription(subscriptionId), request.serviceDate());
            return response;
        } catch (IllegalStateException exception) {
            switch (exception.getMessage()) {
                case "SUBSCRIPTION_NOT_FOUND" ->
                    throw ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found");
                case "SUBSCRIPTION_NOT_ACTIVE" ->
                    throw ApiException.conflict("SUBSCRIPTION_NOT_ACTIVE", "Subscription is no longer active");
                case "OCCURRENCE_NOT_SKIPPABLE" ->
                    throw ApiException.conflict(
                        "OCCURRENCE_NOT_SKIPPABLE",
                        "The meal has progressed too far to be skipped"
                    );
                default -> throw exception;
            }
        }
    }

    public List<CustomerOccurrenceResponse> listOccurrences(UUID subscriptionId, int limit, CurrentUser user) {
        requireCustomer(user);
        owned(subscriptionId, user);
        return lifecycleRepository.listOccurrences(subscriptionId, user.identityId(), bounded(limit, 1, 200));
    }

    public AdminSubscriptionPage listAdmin(
        String status,
        UUID planId,
        Instant afterCreatedAt,
        UUID afterId,
        int limit,
        CurrentUser user
    ) {
        requireAdmin(user);
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!ADMIN_STATUSES.contains(normalized)) {
                throw ApiException.badRequest("INVALID_SUBSCRIPTION_STATUS", "Unsupported subscription status filter");
            }
        }
        if ((afterCreatedAt == null) != (afterId == null)) {
            throw ApiException.badRequest("INVALID_CURSOR", "afterCreatedAt and afterId must be supplied together");
        }
        return lifecycleRepository.listAdmin(
            normalized,
            planId,
            afterCreatedAt,
            afterId,
            bounded(limit, 1, 200)
        );
    }

    public List<SubscriptionStatusHistoryResponse> history(UUID subscriptionId, int limit, CurrentUser user) {
        requireAdmin(user);
        if (subscriptionRepository.findSubscriptionById(subscriptionId).isEmpty()) {
            throw ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found");
        }
        return lifecycleRepository.listHistory(subscriptionId, bounded(limit, 1, 500));
    }

    private OwnedSubscription owned(UUID subscriptionId, CurrentUser user) {
        return lifecycleRepository.findOwned(subscriptionId, user.identityId())
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
    }

    private SubscriptionResponse requireSubscription(UUID subscriptionId) {
        return subscriptionRepository.findSubscriptionById(subscriptionId)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
    }

    private SubscriptionPolicyResponse activePolicy(UUID planId) {
        return policyRepository.findActive(planId)
            .orElseThrow(() -> ApiException.conflict(
                "SUBSCRIPTION_POLICY_NOT_CONFIGURED",
                "The plan has no active administrator policy"
            ));
    }

    private ScheduleClock activeSchedule(UUID planId) {
        return lifecycleRepository.findActiveScheduleClock(planId)
            .orElseThrow(() -> ApiException.conflict(
                "SUBSCRIPTION_SCHEDULE_NOT_ACTIVE",
                "The plan has no active meal schedule"
            ));
    }

    private void enforceNextServiceCutoff(OwnedSubscription subscription, Integer minutes, String code) {
        if (subscription.nextServiceDate() == null) {
            throw ApiException.conflict("NEXT_SERVICE_DATE_MISSING", "Subscription does not have a next service date");
        }
        ScheduleClock schedule = activeSchedule(subscription.planId());
        Instant serviceAt = lifecycleRepository.findOccurrenceServiceAt(subscription.id(), subscription.nextServiceDate())
            .orElseGet(() -> serviceAt(subscription.nextServiceDate(), schedule));
        enforceCutoff(serviceAt, minutes, code);
    }

    private void enforceCutoff(Instant serviceAt, Integer minutes, String code) {
        if (minutes == null) {
            throw ApiException.conflict(
                "SUBSCRIPTION_POLICY_INCOMPLETE",
                "The active administrator policy is missing a required cutoff"
            );
        }
        Instant latestAllowed = serviceAt.minusSeconds(minutes.longValue() * 60L);
        if (!clock.instant().isBefore(latestAllowed)) {
            throw ApiException.conflict(code, "The administrator-configured cutoff has passed");
        }
    }

    private static Instant serviceAt(LocalDate date, ScheduleClock schedule) {
        try {
            return ZonedDateTime.of(date, schedule.serviceTime(), ZoneId.of(schedule.timezone())).toInstant();
        } catch (DateTimeException exception) {
            throw ApiException.conflict("INVALID_ACTIVE_SCHEDULE", "Active schedule timezone or service time is invalid");
        }
    }

    private CustomerSubscriptionResponse current(UUID subscriptionId) {
        SubscriptionResponse value = requireSubscription(subscriptionId);
        return new CustomerSubscriptionResponse(
            value.id(), value.planId(), value.status(), value.startDate(), value.endDate(),
            value.nextServiceDate(), value.deliveryAddressId(), value.notes(), value.createdAt(), value.updatedAt()
        );
    }

    private static void requireCustomer(CurrentUser user) {
        if (user == null || !user.hasRole("CUSTOMER")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "Customer role is required");
        }
    }

    private static void requireAdmin(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN", "SUPPORT_ADMIN", "AUDIT_ADMIN")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "Subscription operations read role is required");
        }
    }

    private static int bounded(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
