package in.craves.integration.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import in.craves.integration.security.CravesPrincipal;
import in.craves.integration.settlement.ChefFinancialModels.CreateEarningRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChefFinancialServiceTest {
    private final ChefFinancialService service = new ChefFinancialService(mock(ChefFinancialRepository.class));

    @Test
    void rejectsArithmeticMismatchBeforePersistence() {
        CravesPrincipal admin = new CravesPrincipal(UUID.randomUUID(), null, Set.of("PLATFORM_ADMIN"));
        CreateEarningRequest request = new CreateEarningRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ON_DEMAND",
            "INR",
            new BigDecimal("100.00"),
            new BigDecimal("10.00"),
            new BigDecimal("5.00"),
            BigDecimal.ZERO,
            new BigDecimal("90.00"),
            "ALLOC-1",
            "Manual approved allocation"
        );

        assertThatThrownBy(() -> service.create(admin, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("netPayable");
    }

    @Test
    void chefCannotCreateFinancialAllocation() {
        CravesPrincipal chef = new CravesPrincipal(UUID.randomUUID(), null, Set.of("CHEF"));
        CreateEarningRequest request = new CreateEarningRequest(
            UUID.randomUUID(), UUID.randomUUID(), "SUBSCRIPTION", "INR",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            "ALLOC-2", "Not authorized"
        );

        assertThatThrownBy(() -> service.create(chef, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Payments administration role");
    }
}
