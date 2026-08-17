package in.craves.userchef.service;

import in.craves.userchef.exception.ApiException;
import in.craves.userchef.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerFavoriteService {
    private static final int MAX_FAVORITES = 200;

    private final JdbcTemplate jdbcTemplate;

    public CustomerFavoriteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CustomerFavorite(UUID menuItemId, Instant createdAt) {
    }

    public List<CustomerFavorite> list(CurrentUser user) {
        requireCustomer(user);
        return jdbcTemplate.query(
            "SELECT menu_item_id, created_at FROM customer_favorite_menu_item " +
                "WHERE identity_id = ? ORDER BY created_at DESC, menu_item_id ASC LIMIT ?",
            this::mapFavorite,
            user.identityId(),
            MAX_FAVORITES
        );
    }

    @Transactional
    public CustomerFavorite save(CurrentUser user, UUID menuItemId) {
        requireCustomer(user);

        List<CustomerFavorite> existing = find(user.identityId(), menuItemId);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_favorite_menu_item WHERE identity_id = ?",
            Integer.class,
            user.identityId()
        );
        if (count != null && count >= MAX_FAVORITES) {
            throw ApiException.conflict(
                "FAVORITES_LIMIT_REACHED",
                "You can save up to " + MAX_FAVORITES + " favorite dishes"
            );
        }

        jdbcTemplate.update(
            "INSERT INTO customer_favorite_menu_item (identity_id, menu_item_id, created_at) " +
                "VALUES (?, ?, now()) ON CONFLICT (identity_id, menu_item_id) DO NOTHING",
            user.identityId(),
            menuItemId
        );

        List<CustomerFavorite> stored = find(user.identityId(), menuItemId);
        if (stored.isEmpty()) {
            throw new IllegalStateException("Favorite was not persisted");
        }
        return stored.getFirst();
    }

    @Transactional
    public void remove(CurrentUser user, UUID menuItemId) {
        requireCustomer(user);
        jdbcTemplate.update(
            "DELETE FROM customer_favorite_menu_item WHERE identity_id = ? AND menu_item_id = ?",
            user.identityId(),
            menuItemId
        );
    }

    private List<CustomerFavorite> find(UUID identityId, UUID menuItemId) {
        return jdbcTemplate.query(
            "SELECT menu_item_id, created_at FROM customer_favorite_menu_item " +
                "WHERE identity_id = ? AND menu_item_id = ?",
            this::mapFavorite,
            identityId,
            menuItemId
        );
    }

    private CustomerFavorite mapFavorite(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerFavorite(
            rs.getObject("menu_item_id", UUID.class),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private static void requireCustomer(CurrentUser user) {
        if (user == null || !user.hasRole("CUSTOMER")) {
            throw ApiException.forbidden(
                "CUSTOMER_ROLE_REQUIRED",
                "Customer favorites require an active CUSTOMER role"
            );
        }
    }
}
