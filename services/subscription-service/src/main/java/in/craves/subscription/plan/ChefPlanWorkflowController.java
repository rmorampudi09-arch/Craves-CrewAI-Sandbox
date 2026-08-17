package in.craves.subscription.plan;

import in.craves.subscription.plan.ChefPlanModels.ChefPlanInput;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanResponse;
import in.craves.subscription.plan.ChefPlanModels.ReviewChefPlanRequest;
import in.craves.subscription.plan.ChefPlanModels.SubmitChefPlanRequest;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.PutScheduleRequest;
import in.craves.subscription.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChefPlanWorkflowController {
    private final ChefPlanWorkflowService workflow;
    private final ChefPlanScheduleService schedules;

    public ChefPlanWorkflowController(ChefPlanWorkflowService workflow, ChefPlanScheduleService schedules) {
        this.workflow = workflow;
        this.schedules = schedules;
    }

    @PostMapping("/chef/subscription-plans")
    public ResponseEntity<ChefPlanResponse> create(
        @Valid @RequestBody ChefPlanInput request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        ChefPlanResponse response = workflow.create(request, user);
        return ResponseEntity.created(URI.create("/api/v1/chef/subscription-plans/" + response.id())).body(response);
    }

    @GetMapping("/chef/subscription-plans")
    public List<ChefPlanResponse> listMine(@AuthenticationPrincipal CurrentUser user) {
        return workflow.listMine(user);
    }

    @GetMapping("/chef/subscription-plans/{planId}")
    public ChefPlanResponse getMine(
        @PathVariable UUID planId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return workflow.getMine(planId, user);
    }

    @PutMapping("/chef/subscription-plans/{planId}")
    public ChefPlanResponse update(
        @PathVariable UUID planId,
        @Valid @RequestBody ChefPlanInput request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return workflow.update(planId, request, user);
    }

    @GetMapping("/chef/subscription-plans/{planId}/schedule")
    public PlanScheduleResponse getSchedule(
        @PathVariable UUID planId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return schedules.getOwned(planId, user);
    }

    @PutMapping("/chef/subscription-plans/{planId}/schedule")
    public PlanScheduleResponse putSchedule(
        @PathVariable UUID planId,
        @Valid @RequestBody PutScheduleRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return schedules.putOwned(planId, request, user);
    }

    @PostMapping("/chef/subscription-plans/{planId}/submit")
    public ChefPlanResponse submit(
        @PathVariable UUID planId,
        @RequestBody(required = false) SubmitChefPlanRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return workflow.submit(planId, request == null ? null : request.note(), user);
    }

    @PostMapping("/admin/subscription-plans/{planId}/review")
    public ChefPlanResponse review(
        @PathVariable UUID planId,
        @Valid @RequestBody ReviewChefPlanRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return workflow.review(planId, request, user);
    }
}
