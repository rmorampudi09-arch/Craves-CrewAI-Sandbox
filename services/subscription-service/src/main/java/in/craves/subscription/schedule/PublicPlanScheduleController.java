package in.craves.subscription.schedule;

import in.craves.subscription.schedule.PlanScheduleModels.PublicPlanScheduleResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions/plans")
public class PublicPlanScheduleController {
    private final PlanScheduleService service;

    public PublicPlanScheduleController(PlanScheduleService service) {
        this.service = service;
    }

    @GetMapping("/{planId}/schedule")
    public PublicPlanScheduleResponse getActiveSchedule(@PathVariable UUID planId) {
        return service.getPublicActive(planId);
    }
}
