package in.craves.auth.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InternalChefRoleGrantRequest(
    @NotNull UUID identityId,
    @NotNull UUID sourceApplicationId
) {
}
