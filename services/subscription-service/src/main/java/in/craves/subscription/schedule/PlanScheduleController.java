package in.craves.subscription.schedule;

import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/subscription-plans/{planId}/schedule")
public class PlanScheduleController {
    private final PlanScheduleService service;

    public PlanScheduleController(PlanScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public PlanScheduleResponse get(
        @PathVariable UUID planId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.get(planId, user);
    }
}
