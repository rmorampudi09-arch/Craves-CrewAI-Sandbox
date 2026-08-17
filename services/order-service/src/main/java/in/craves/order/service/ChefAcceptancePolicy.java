package in.craves.order.service;

import in.craves.order.exception.OrderApiException;
import in.craves.order.web.ApiDtos.OrderStatus;
import java.util.Objects;

public final class ChefAcceptancePolicy {
    private ChefAcceptancePolicy() {
    }

    public enum Decision {
        ACCEPT,
        IDEMPOTENT_SUCCESS
    }

    public static Decision decide(
        OrderStatus currentStatus,
        Integer existingPrepTimeMinutes,
        int requestedPrepTimeMinutes
    ) {
        if (requestedPrepTimeMinutes <= 0) {
            throw OrderApiException.badRequest(
                "PREPARATION_TIME_REQUIRED",
                "A positive preparation time is required when accepting an order."
            );
        }

        if (currentStatus == OrderStatus.CHEF_ACCEPTED) {
            if (Objects.equals(existingPrepTimeMinutes, requestedPrepTimeMinutes)) {
                return Decision.IDEMPOTENT_SUCCESS;
            }
            throw OrderApiException.conflict(
                "ORDER_ALREADY_ACCEPTED",
                "The order was already accepted with a different preparation time."
            );
        }

        if (currentStatus != OrderStatus.CHEF_ACCEPTANCE_PENDING) {
            throw OrderApiException.conflict(
                "ORDER_NOT_WAITING_FOR_CHEF_ACCEPTANCE",
                "Only an order awaiting chef acceptance can be accepted."
            );
        }

        return Decision.ACCEPT;
    }
}
