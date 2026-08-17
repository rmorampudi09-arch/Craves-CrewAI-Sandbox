package in.craves.userchef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.userchef.exception.ApiException;
import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.CustomerFavoriteService.CustomerFavorite;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CustomerFavoriteServiceTest {
    private JdbcTemplate jdbcTemplate;
    private CustomerFavoriteService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new CustomerFavoriteService(jdbcTemplate);
    }

    @Test
    void rejectsNonCustomerBeforeTouchingDatabase() {
        CurrentUser chef = new CurrentUser(
            UUID.randomUUID(),
            "firebase-chef",
            "+919876543210",
            List.of("CHEF")
        );

        assertThatThrownBy(() -> service.list(chef))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).getCode())
                .isEqualTo("CUSTOMER_ROLE_REQUIRED"));

        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void saveIsIdempotentWhenFavoriteAlreadyExists() {
        UUID identityId = UUID.randomUUID();
        UUID menuItemId = UUID.randomUUID();
        CurrentUser customer = customer(identityId);
        CustomerFavorite existing = new CustomerFavorite(menuItemId, Instant.parse("2026-08-16T12:00:00Z"));

        doReturn(List.of(existing)).when(jdbcTemplate).query(
            anyString(),
            any(RowMapper.class),
            eq(identityId),
            eq(menuItemId)
        );

        CustomerFavorite saved = service.save(customer, menuItemId);

        assertThat(saved).isEqualTo(existing);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void removeIsIdempotentAndScopedToCurrentIdentity() {
        UUID identityId = UUID.randomUUID();
        UUID menuItemId = UUID.randomUUID();
        CurrentUser customer = customer(identityId);
        when(jdbcTemplate.update(anyString(), eq(identityId), eq(menuItemId))).thenReturn(0);

        service.remove(customer, menuItemId);

        verify(jdbcTemplate).update(anyString(), eq(identityId), eq(menuItemId));
    }

    private static CurrentUser customer(UUID identityId) {
        return new CurrentUser(
            identityId,
            "firebase-customer",
            "+919876543210",
            List.of("CUSTOMER")
        );
    }
}
