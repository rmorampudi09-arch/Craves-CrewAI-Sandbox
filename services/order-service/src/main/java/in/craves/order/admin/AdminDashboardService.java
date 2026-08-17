package in.craves.order.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardSummary loadSummary() {
        Metrics metrics = jdbcTemplate.queryForObject(
            """
            SELECT count(*) FILTER (WHERE created_at >= now() - interval '24 hours') AS orders_created_24h,
                   count(*) FILTER (WHERE status = 'CHEF_ACCEPTANCE_PENDING') AS chef_acceptance_pending,
                   count(*) FILTER (WHERE status = 'PREPARING') AS preparing,
                   count(*) FILTER (WHERE status = 'READY_FOR_PICKUP') AS ready_for_pickup,
                   count(*) FILTER (WHERE status = 'OUT_FOR_DELIVERY') AS out_for_delivery,
                   count(*) FILTER (WHERE status = 'REFUND_PENDING') AS refund_pending,
                   count(*) FILTER (WHERE status = 'REFUND_FAILED') AS refund_failed,
                   count(*) FILTER (WHERE status = 'DELIVERED' AND updated_at >= now() - interval '24 hours') AS delivered_24h
              FROM order_schema.customer_order
             WHERE created_at >= now() - interval '24 hours'
                OR updated_at >= now() - interval '24 hours'
                OR status IN (
                    'CHEF_ACCEPTANCE_PENDING', 'PREPARING', 'READY_FOR_PICKUP',
                    'OUT_FOR_DELIVERY', 'REFUND_PENDING', 'REFUND_FAILED'
                )
            """,
            (rs, rowNum) -> new Metrics(
                rs.getLong("orders_created_24h"),
                rs.getLong("chef_acceptance_pending"),
                rs.getLong("preparing"),
                rs.getLong("ready_for_pickup"),
                rs.getLong("out_for_delivery"),
                rs.getLong("refund_pending"),
                rs.getLong("refund_failed"),
                rs.getLong("delivered_24h")
            )
        );

        List<StatusCount> statusCounts = jdbcTemplate.query(
            """
            SELECT status, count(*) AS order_count
              FROM order_schema.customer_order
             WHERE status IN (
                'CHEF_ACCEPTANCE_PENDING', 'PREPARING', 'READY_FOR_PICKUP',
                'OUT_FOR_DELIVERY', 'REFUND_PENDING', 'REFUND_FAILED'
             )
             GROUP BY status
             ORDER BY status
            """,
            (rs, rowNum) -> new StatusCount(rs.getString("status"), rs.getLong("order_count"))
        );

        List<OrderTrendPoint> orderTrend = jdbcTemplate.query(
            """
            WITH days AS (
                SELECT generate_series(
                    (now() AT TIME ZONE 'UTC')::date - 6,
                    (now() AT TIME ZONE 'UTC')::date,
                    interval '1 day'
                )::date AS day
            )
            SELECT days.day, count(orders.id) AS order_count
              FROM days
              LEFT JOIN order_schema.customer_order orders
                ON orders.created_at >= days.day::timestamp AT TIME ZONE 'UTC'
               AND orders.created_at < (days.day + 1)::timestamp AT TIME ZONE 'UTC'
             GROUP BY days.day
             ORDER BY days.day
            """,
            (rs, rowNum) -> new OrderTrendPoint(
                rs.getObject("day", LocalDate.class), rs.getLong("order_count")
            )
        );

        List<RecentException> recentExceptions = jdbcTemplate.query(
            """
            SELECT id, kitchen_name_snapshot, status, updated_at
              FROM order_schema.customer_order
             WHERE status IN ('CHEF_REJECTED', 'CANCELLED', 'REFUND_PENDING', 'REFUND_FAILED')
             ORDER BY updated_at DESC, id
             LIMIT 10
            """,
            (rs, rowNum) -> new RecentException(
                rs.getObject("id", UUID.class),
                rs.getString("kitchen_name_snapshot"),
                rs.getString("status"),
                rs.getObject("updated_at", OffsetDateTime.class)
            )
        );

        return new DashboardSummary(
            OffsetDateTime.now(ZoneOffset.UTC), metrics, statusCounts, orderTrend, recentExceptions
        );
    }

    public record DashboardSummary(
        OffsetDateTime generatedAt,
        Metrics metrics,
        List<StatusCount> statusCounts,
        List<OrderTrendPoint> orderTrend,
        List<RecentException> recentExceptions
    ) {}

    public record Metrics(
        long ordersCreated24h,
        long chefAcceptancePending,
        long preparing,
        long readyForPickup,
        long outForDelivery,
        long refundPending,
        long refundFailed,
        long delivered24h
    ) {}

    public record StatusCount(String status, long count) {}

    public record OrderTrendPoint(LocalDate date, long count) {}

    public record RecentException(UUID orderId, String kitchenName, String status, OffsetDateTime updatedAt) {}
}
