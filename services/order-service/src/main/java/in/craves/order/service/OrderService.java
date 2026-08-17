package in.craves.order.service;

import in.craves.order.exception.OrderApiException;
import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.CatalogClient.CatalogMenuItem;
import in.craves.order.service.CustomerAddressClient.CustomerAddress;
import in.craves.order.web.ApiDtos.AddCartItemRequest;
import in.craves.order.web.ApiDtos.CartItemResponse;
import in.craves.order.web.ApiDtos.CartResponse;
import in.craves.order.web.ApiDtos.CartTotalsResponse;
import in.craves.order.web.ApiDtos.ChargePolicyRequest;
import in.craves.order.web.ApiDtos.ChargePolicyResponse;
import in.craves.order.web.ApiDtos.CheckoutRequest;
import in.craves.order.web.ApiDtos.CheckoutResponse;
import in.craves.order.web.ApiDtos.CheckoutStatus;
import in.craves.order.web.ApiDtos.ChefAcceptRequest;
import in.craves.order.web.ApiDtos.ChefRejectRequest;
import in.craves.order.web.ApiDtos.CustomerAddressSnapshotResponse;
import in.craves.order.web.ApiDtos.KitchenPickupSnapshotResponse;
import in.craves.order.web.ApiDtos.OrderItemResponse;
import in.craves.order.web.ApiDtos.OrderResponse;
import in.craves.order.web.ApiDtos.OrderStatus;
import in.craves.order.web.ApiDtos.UpdateCartItemRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final String INR = "INR";
    private static final String DELIVERY_ADDRESS_REQUIRED_MESSAGE =
        "Save the current location or select a saved delivery address before placing the order.";

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClient catalogClient;
    private final CustomerAddressClient customerAddressClient;
    private final CheckoutSnapshotFactory checkoutSnapshotFactory;
    private final NotificationInternalClient notificationInternalClient;

    public OrderService(
        JdbcTemplate jdbcTemplate,
        CatalogClient catalogClient,
        CustomerAddressClient customerAddressClient,
        CheckoutSnapshotFactory checkoutSnapshotFactory,
        NotificationInternalClient notificationInternalClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogClient = catalogClient;
        this.customerAddressClient = customerAddressClient;
        this.checkoutSnapshotFactory = checkoutSnapshotFactory;
        this.notificationInternalClient = notificationInternalClient;
    }

    public CartResponse getCart(CravesPrincipal principal) {
        requireCustomer(principal);
        UUID cartId = getOrCreateCartId(principal.identityId());
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CartResponse addCartItem(CravesPrincipal principal, AddCartItemRequest request) {
        requireCustomer(principal);
        CatalogMenuItem item = catalogClient.getActiveMenuItem(request.menuItemId());
        CatalogKitchen kitchen = catalogClient.getKitchen(item.kitchenId());
        UUID cartId = getOrCreateCartId(principal.identityId());
        jdbcTemplate.update(
            "INSERT INTO order_schema.cart_item (id, cart_id, menu_item_id, kitchen_id, item_name_snapshot, kitchen_name_snapshot, unit_price_snapshot, currency_snapshot, quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (cart_id, menu_item_id) DO UPDATE SET quantity = order_schema.cart_item.quantity + EXCLUDED.quantity, kitchen_id = EXCLUDED.kitchen_id, item_name_snapshot = EXCLUDED.item_name_snapshot, kitchen_name_snapshot = EXCLUDED.kitchen_name_snapshot, unit_price_snapshot = EXCLUDED.unit_price_snapshot, currency_snapshot = EXCLUDED.currency_snapshot, updated_at = now()",
            UUID.randomUUID(), cartId, item.id(), item.kitchenId(), item.itemName(), displayKitchenName(kitchen), item.price(), currency(item.currency()), request.quantity()
        );
        touchCart(cartId);
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CartResponse updateCartItem(CravesPrincipal principal, UUID cartItemId, UpdateCartItemRequest request) {
        requireCustomer(principal);
        UUID cartId = requireCartId(principal.identityId());
        int updated = jdbcTemplate.update(
            "UPDATE order_schema.cart_item SET quantity = ?, updated_at = now() WHERE id = ? AND cart_id = ?",
            request.quantity(), cartItemId, cartId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item was not found");
        }
        touchCart(cartId);
        return validateCart(principal);
    }

    @Transactional
    public CartResponse removeCartItem(CravesPrincipal principal, UUID cartItemId) {
        requireCustomer(principal);
        UUID cartId = requireCartId(principal.identityId());
        jdbcTemplate.update("DELETE FROM order_schema.cart_item WHERE id = ? AND cart_id = ?", cartItemId, cartId);
        touchCart(cartId);
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CartResponse clearCart(CravesPrincipal principal) {
        requireCustomer(principal);
        UUID cartId = getOrCreateCartId(principal.identityId());
        jdbcTemplate.update("DELETE FROM order_schema.cart_item WHERE cart_id = ?", cartId);
        touchCart(cartId);
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CartResponse replaceCartFromOrder(CravesPrincipal principal, UUID orderId) {
        requireCustomer(principal);
        if (orderId == null) {
            throw OrderApiException.badRequest("ORDER_ID_REQUIRED", "Order id is required to reorder.");
        }

        OrderResponse historicalOrder = getOrderForCustomer(principal, orderId);
        if (historicalOrder.items() == null || historicalOrder.items().isEmpty()) {
            throw OrderApiException.badRequest("ORDER_HAS_NO_ITEMS", "This order has no items to reorder.");
        }

        // Resolve and validate every historical menu item before touching the existing cart.
        // Any unavailable item fails the transaction with the current cart unchanged.
        record ReorderResolvedItem(CatalogMenuItem item, CatalogKitchen kitchen, int quantity) {}
        List<ReorderResolvedItem> resolved = new ArrayList<>();
        for (OrderItemResponse historicalItem : historicalOrder.items()) {
            if (historicalItem.menuItemId() == null || historicalItem.quantity() < 1) {
                throw OrderApiException.badRequest("ORDER_ITEM_INVALID", "This historical order cannot be reordered.");
            }
            CatalogMenuItem currentItem = catalogClient.getActiveMenuItem(historicalItem.menuItemId());
            CatalogKitchen currentKitchen = catalogClient.getKitchen(currentItem.kitchenId());
            resolved.add(new ReorderResolvedItem(currentItem, currentKitchen, historicalItem.quantity()));
        }

        UUID cartId = getOrCreateCartId(principal.identityId());
        jdbcTemplate.update("DELETE FROM order_schema.cart_item WHERE cart_id = ?", cartId);
        for (ReorderResolvedItem replacement : resolved) {
            CatalogMenuItem item = replacement.item();
            jdbcTemplate.update(
                "INSERT INTO order_schema.cart_item (id, cart_id, menu_item_id, kitchen_id, item_name_snapshot, kitchen_name_snapshot, unit_price_snapshot, currency_snapshot, quantity, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                UUID.randomUUID(),
                cartId,
                item.id(),
                item.kitchenId(),
                item.itemName(),
                displayKitchenName(replacement.kitchen()),
                item.price(),
                currency(item.currency()),
                replacement.quantity()
            );
        }
        touchCart(cartId);
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CartResponse validateCart(CravesPrincipal principal) {
        requireCustomer(principal);
        UUID cartId = getOrCreateCartId(principal.identityId());
        List<CartItemResponse> current = listCartItems(cartId);
        for (CartItemResponse cartItem : current) {
            CatalogMenuItem item = catalogClient.getActiveMenuItem(cartItem.menuItemId());
            CatalogKitchen kitchen = catalogClient.getKitchen(item.kitchenId());
            jdbcTemplate.update(
                "UPDATE order_schema.cart_item SET kitchen_id = ?, item_name_snapshot = ?, kitchen_name_snapshot = ?, unit_price_snapshot = ?, currency_snapshot = ?, updated_at = now() WHERE id = ?",
                item.kitchenId(), item.itemName(), displayKitchenName(kitchen), item.price(), currency(item.currency()), cartItem.id()
            );
        }
        touchCart(cartId);
        return mapCart(cartId, principal.identityId());
    }

    @Transactional
    public CheckoutResponse checkout(CravesPrincipal principal, CheckoutRequest request) {
        requireCustomer(principal);
        if (request == null || request.deliveryAddressId() == null) {
            throw OrderApiException.badRequest("DELIVERY_ADDRESS_REQUIRED", DELIVERY_ADDRESS_REQUIRED_MESSAGE);
        }

        CustomerAddress customerAddress = customerAddressClient.getActiveOwnedAddress(
            principal.identityId(),
            request.deliveryAddressId()
        );
        CustomerAddressSnapshotResponse dropoff = checkoutSnapshotFactory.customerDropoff(customerAddress);

        CartResponse cart = validateCart(principal);
        if (cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        ChargePolicyResponse policy = currentChargePolicy();
        Map<UUID, CatalogMenuItem> catalogItems = new LinkedHashMap<>();
        Map<UUID, List<CartItemResponse>> byKitchen = new LinkedHashMap<>();
        for (CartItemResponse cartItem : cart.items()) {
            CatalogMenuItem catalogItem = catalogClient.getActiveMenuItem(cartItem.menuItemId());
            catalogItems.put(catalogItem.id(), catalogItem);
            byKitchen.computeIfAbsent(catalogItem.kitchenId(), ignored -> new ArrayList<>()).add(cartItem);
        }

        List<PendingKitchenOrder> pendingOrders = new ArrayList<>();
        BigDecimal checkoutFood = BigDecimal.ZERO;
        BigDecimal checkoutPlatform = BigDecimal.ZERO;
        BigDecimal checkoutTax = BigDecimal.ZERO;
        BigDecimal checkoutDelivery = BigDecimal.ZERO;

        for (Map.Entry<UUID, List<CartItemResponse>> entry : byKitchen.entrySet()) {
            UUID kitchenId = entry.getKey();
            CatalogKitchen kitchen = catalogClient.getKitchen(kitchenId);
            KitchenPickupSnapshotResponse pickup = checkoutSnapshotFactory.kitchenPickup(kitchen);
            BigDecimal foodSubtotal = entry.getValue().stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            Charges charges = calculateCharges(foodSubtotal, policy);
            OrderPackaging packaging = calculatePackaging(entry.getValue(), catalogItems);
            pendingOrders.add(new PendingKitchenOrder(
                kitchenId,
                kitchen,
                pickup,
                List.copyOf(entry.getValue()),
                foodSubtotal,
                charges,
                packaging
            ));
            checkoutFood = checkoutFood.add(foodSubtotal);
            checkoutPlatform = checkoutPlatform.add(charges.platformFee());
            checkoutTax = checkoutTax.add(charges.taxAmount());
            checkoutDelivery = checkoutDelivery.add(charges.deliveryFee());
        }

        UUID checkoutId = UUID.randomUUID();
        BigDecimal grandTotal = checkoutFood.add(checkoutPlatform)
            .add(checkoutTax)
            .add(checkoutDelivery)
            .setScale(2, RoundingMode.HALF_UP);

        jdbcTemplate.update(
            """
                INSERT INTO order_schema.checkout (
                    id, customer_identity_id, status, currency,
                    food_subtotal, platform_fee, tax_amount, delivery_fee, grand_total, charge_policy_id,
                    delivery_address_id,
                    dropoff_recipient_name, dropoff_contact_phone, dropoff_address_line1,
                    dropoff_address_line2, dropoff_landmark, dropoff_area_name, dropoff_city,
                    dropoff_state, dropoff_postal_code, dropoff_latitude, dropoff_longitude,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    now(), now()
                )
                """,
            checkoutId,
            principal.identityId(),
            CheckoutStatus.PAYMENT_PENDING.name(),
            INR,
            checkoutFood,
            checkoutPlatform,
            checkoutTax,
            checkoutDelivery,
            grandTotal,
            policy.id(),
            dropoff.sourceAddressId(),
            dropoff.recipientName(),
            dropoff.contactPhoneNumber(),
            dropoff.addressLine1(),
            dropoff.addressLine2(),
            dropoff.landmark(),
            dropoff.areaName(),
            dropoff.city(),
            dropoff.state(),
            dropoff.postalCode(),
            dropoff.latitude(),
            dropoff.longitude()
        );

        for (PendingKitchenOrder pending : pendingOrders) {
            UUID orderId = UUID.randomUUID();
            KitchenPickupSnapshotResponse pickup = pending.pickup();
            jdbcTemplate.update(
                """
                    INSERT INTO order_schema.customer_order (
                        id, checkout_id, customer_identity_id, kitchen_id, kitchen_name_snapshot,
                        status, currency, food_subtotal, platform_fee, tax_amount, delivery_fee,
                        grand_total, total_package_weight_grams, thermobox_required,
                        delivery_address_id,
                        dropoff_recipient_name, dropoff_contact_phone, dropoff_address_line1,
                        dropoff_address_line2, dropoff_landmark, dropoff_area_name, dropoff_city,
                        dropoff_state, dropoff_postal_code, dropoff_latitude, dropoff_longitude,
                        pickup_phone_number, pickup_email, pickup_address_line1, pickup_address_line2,
                        pickup_landmark, pickup_area_name, pickup_city, pickup_state,
                        pickup_postal_code, pickup_latitude, pickup_longitude,
                        created_at, updated_at
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        now(), now()
                    )
                    """,
                orderId,
                checkoutId,
                principal.identityId(),
                pending.kitchenId(),
                displayKitchenName(pending.kitchen()),
                OrderStatus.PAYMENT_PENDING.name(),
                INR,
                pending.foodSubtotal(),
                pending.charges().platformFee(),
                pending.charges().taxAmount(),
                pending.charges().deliveryFee(),
                pending.charges().grandTotal(),
                pending.packaging().totalPackageWeightGrams(),
                pending.packaging().thermoboxRequired(),
                dropoff.sourceAddressId(),
                dropoff.recipientName(),
                dropoff.contactPhoneNumber(),
                dropoff.addressLine1(),
                dropoff.addressLine2(),
                dropoff.landmark(),
                dropoff.areaName(),
                dropoff.city(),
                dropoff.state(),
                dropoff.postalCode(),
                dropoff.latitude(),
                dropoff.longitude(),
                pickup.contactPhoneNumber(),
                pickup.email(),
                pickup.addressLine1(),
                pickup.addressLine2(),
                pickup.landmark(),
                pickup.areaName(),
                pickup.city(),
                pickup.state(),
                pickup.postalCode(),
                pickup.latitude(),
                pickup.longitude()
            );
            addStatusHistory(orderId, null, OrderStatus.PAYMENT_PENDING, principal.identityId(), "Checkout created");
            for (CartItemResponse cartItem : pending.items()) {
                CatalogMenuItem catalogItem = catalogItems.get(cartItem.menuItemId());
                jdbcTemplate.update(
                    "INSERT INTO order_schema.order_item (id, order_id, menu_item_id, item_name_snapshot, category_snapshot, food_type_snapshot, unit_price_snapshot, unit_package_weight_grams_snapshot, thermobox_required_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                    UUID.randomUUID(),
                    orderId,
                    cartItem.menuItemId(),
                    cartItem.itemName(),
                    catalogItem.category(),
                    catalogItem.foodType(),
                    cartItem.unitPrice(),
                    catalogItem.unitPackageWeightGrams(),
                    catalogItem.thermoboxRequired(),
                    cartItem.quantity(),
                    cartItem.lineTotal()
                );
            }
        }

        clearCart(principal);
        CheckoutResponse response = getCheckout(principal, checkoutId);
        notifyOrderCreatedAfterCommit(response);
        return response;
    }

    public CheckoutResponse getCheckout(CravesPrincipal principal, UUID checkoutId) {
        requireCustomer(principal);
        List<CheckoutResponse> rows = jdbcTemplate.query(
            "SELECT * FROM order_schema.checkout WHERE id = ? AND customer_identity_id = ?",
            (rs, rowNum) -> mapCheckout(rs, listOrdersByCheckout(checkoutId)),
            checkoutId, principal.identityId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout was not found");
        }
        return rows.getFirst();
    }

    public List<OrderResponse> listCustomerOrders(CravesPrincipal principal) {
        requireCustomer(principal);
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.customer_order WHERE customer_identity_id = ? ORDER BY created_at DESC LIMIT 50",
            this::mapOrder,
            principal.identityId()
        );
    }

    public OrderResponse getOrderForCustomer(CravesPrincipal principal, UUID orderId) {
        requireCustomer(principal);
        List<OrderResponse> rows = jdbcTemplate.query(
            "SELECT * FROM order_schema.customer_order WHERE id = ? AND customer_identity_id = ?",
            this::mapOrder,
            orderId, principal.identityId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found");
        }
        return rows.getFirst();
    }

    public List<OrderResponse> listChefOrders(CravesPrincipal principal) {
        requireChef(principal);
        List<OrderResponse> allRecent = jdbcTemplate.query(
            "SELECT * FROM order_schema.customer_order ORDER BY created_at DESC LIMIT 100",
            this::mapOrder
        );
        return allRecent.stream()
            .filter(order -> catalogClient.getKitchen(order.kitchenId()).identityId().equals(principal.identityId()))
            .toList();
    }

    @Transactional
    public OrderResponse acceptChefOrder(CravesPrincipal principal, UUID orderId, ChefAcceptRequest request) {
        requireChef(principal);
        OrderResponse order = getOrderForChef(principal, orderId);
        if (order.status() != OrderStatus.PAYMENT_PENDING && order.status() != OrderStatus.CHEF_ACCEPTANCE_PENDING && order.status() != OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not waiting for chef acceptance");
        }
        updateOrderStatus(orderId, order.status(), OrderStatus.CHEF_ACCEPTED, principal.identityId(), safeReason(request.note()), request.prepTimeMinutes());
        return getOrderForChef(principal, orderId);
    }

    @Transactional
    public OrderResponse rejectChefOrder(CravesPrincipal principal, UUID orderId, ChefRejectRequest request) {
        requireChef(principal);
        OrderResponse order = getOrderForChef(principal, orderId);
        updateOrderStatus(orderId, order.status(), OrderStatus.CHEF_REJECTED, principal.identityId(), safeReason(request.reason()), null);
        return getOrderForChef(principal, orderId);
    }

    @Transactional
    public OrderResponse markReadyForPickup(CravesPrincipal principal, UUID orderId) {
        requireChef(principal);
        OrderResponse order = getOrderForChef(principal, orderId);
        if (order.status() != OrderStatus.CHEF_ACCEPTED && order.status() != OrderStatus.PREPARING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be marked ready yet");
        }
        updateOrderStatus(orderId, order.status(), OrderStatus.READY_FOR_PICKUP, principal.identityId(), "Chef marked food ready", order.prepTimeMinutes());
        return getOrderForChef(principal, orderId);
    }

    public OrderResponse getOrderForChef(CravesPrincipal principal, UUID orderId) {
        requireChef(principal);
        List<OrderResponse> rows = jdbcTemplate.query("SELECT * FROM order_schema.customer_order WHERE id = ?", this::mapOrder, orderId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found");
        }
        OrderResponse order = rows.getFirst();
        CatalogKitchen kitchen = catalogClient.getKitchen(order.kitchenId());
        if (!kitchen.identityId().equals(principal.identityId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chef cannot access this order");
        }
        return order;
    }

    public ChargePolicyResponse currentChargePolicy() {
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.charge_policy WHERE is_active = true ORDER BY created_at DESC LIMIT 1",
            this::mapChargePolicy
        ).getFirst();
    }

    @Transactional
    public ChargePolicyResponse createChargePolicy(CravesPrincipal principal, ChargePolicyRequest request) {
        requireAdmin(principal);
        jdbcTemplate.update("UPDATE order_schema.charge_policy SET is_active = false WHERE is_active = true");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO order_schema.charge_policy (id, policy_name, platform_fee_percent, platform_fee_flat, tax_percent, delivery_fee_flat, is_active, created_by_identity_id, created_at) VALUES (?, ?, ?, ?, ?, ?, true, ?, now())",
            id,
            StringUtils.hasText(request.policyName()) ? request.policyName().trim() : "ADMIN_CHARGE_POLICY",
            zeroIfNull(request.platformFeePercent()),
            zeroIfNull(request.platformFeeFlat()),
            zeroIfNull(request.taxPercent()),
            zeroIfNull(request.deliveryFeeFlat()),
            principal.identityId()
        );
        return currentChargePolicy();
    }

    private CartResponse mapCart(UUID cartId, UUID customerIdentityId) {
        List<CartItemResponse> items = listCartItems(cartId);
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new CartResponse(cartId, customerIdentityId, INR, items, new CartTotalsResponse(subtotal, INR));
    }

    private List<CartItemResponse> listCartItems(UUID cartId) {
        return jdbcTemplate.query("SELECT * FROM order_schema.cart_item WHERE cart_id = ? ORDER BY created_at ASC", this::mapCartItem, cartId);
    }

    private UUID getOrCreateCartId(UUID customerIdentityId) {
        return jdbcTemplate.query("SELECT id FROM order_schema.cart WHERE customer_identity_id = ?", (rs, rowNum) -> rs.getObject("id", UUID.class), customerIdentityId)
            .stream()
            .findFirst()
            .orElseGet(() -> {
                UUID id = UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO order_schema.cart (id, customer_identity_id, currency, created_at, updated_at) VALUES (?, ?, ?, now(), now())", id, customerIdentityId, INR);
                return id;
            });
    }

    private UUID requireCartId(UUID customerIdentityId) {
        return getOrCreateCartId(customerIdentityId);
    }

    private void touchCart(UUID cartId) {
        jdbcTemplate.update("UPDATE order_schema.cart SET updated_at = now() WHERE id = ?", cartId);
    }

    private void updateOrderStatus(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, UUID actor, String reason, Integer prepTimeMinutes) {
        if (newStatus == OrderStatus.CHEF_ACCEPTED) {
            if (prepTimeMinutes == null || prepTimeMinutes <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preparation time is required when accepting an order");
            }
            jdbcTemplate.update(
                "UPDATE order_schema.customer_order SET status = ?, chef_response_note = ?, prep_time_minutes = ?, ready_at = now() + (? * INTERVAL '1 minute'), updated_at = now() WHERE id = ?",
                newStatus.name(), reason, prepTimeMinutes, prepTimeMinutes, orderId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE order_schema.customer_order SET status = ?, chef_response_note = ?, prep_time_minutes = COALESCE(?, prep_time_minutes), updated_at = now() WHERE id = ?",
                newStatus.name(), reason, prepTimeMinutes, orderId
            );
        }
        addStatusHistory(orderId, oldStatus, newStatus, actor, reason);
    }

    private void addStatusHistory(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, UUID actor, String reason) {
        jdbcTemplate.update(
            "INSERT INTO order_schema.order_status_history (id, order_id, old_status, new_status, actor_identity_id, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), orderId, oldStatus == null ? null : oldStatus.name(), newStatus.name(), actor, reason
        );
    }

    private void notifyOrderCreatedAfterCommit(CheckoutResponse checkout) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationInternalClient.orderCreated(checkout);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationInternalClient.orderCreated(checkout);
            }
        });
    }

    private List<OrderResponse> listOrdersByCheckout(UUID checkoutId) {
        return jdbcTemplate.query("SELECT * FROM order_schema.customer_order WHERE checkout_id = ? ORDER BY created_at ASC", this::mapOrder, checkoutId);
    }

    private CartItemResponse mapCartItem(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal unitPrice = rs.getBigDecimal("unit_price_snapshot");
        int quantity = rs.getInt("quantity");
        BigDecimal lineTotal = unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        return new CartItemResponse(
            rs.getObject("id", UUID.class), rs.getObject("menu_item_id", UUID.class), rs.getObject("kitchen_id", UUID.class), rs.getString("item_name_snapshot"), rs.getString("kitchen_name_snapshot"), unitPrice, rs.getString("currency_snapshot"), quantity, lineTotal, instant(rs, "created_at"), instant(rs, "updated_at")
        );
    }

    private CheckoutResponse mapCheckout(ResultSet rs, List<OrderResponse> orders) throws SQLException {
        CustomerAddressSnapshotResponse dropoff = mapDropoffSnapshot(rs);
        return new CheckoutResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("customer_identity_id", UUID.class),
            CheckoutStatus.valueOf(rs.getString("status")),
            rs.getString("currency"),
            rs.getBigDecimal("food_subtotal"),
            rs.getBigDecimal("platform_fee"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("delivery_fee"),
            rs.getBigDecimal("grand_total"),
            rs.getObject("charge_policy_id", UUID.class),
            rs.getObject("delivery_address_id", UUID.class),
            dropoff,
            orders,
            instant(rs, "created_at")
        );
    }

    private OrderResponse mapOrder(ResultSet rs, int rowNum) throws SQLException {
        UUID orderId = rs.getObject("id", UUID.class);
        return new OrderResponse(
            orderId,
            rs.getObject("checkout_id", UUID.class),
            rs.getObject("customer_identity_id", UUID.class),
            rs.getObject("kitchen_id", UUID.class),
            rs.getString("kitchen_name_snapshot"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getString("currency"),
            rs.getBigDecimal("food_subtotal"),
            rs.getBigDecimal("platform_fee"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("delivery_fee"),
            rs.getBigDecimal("grand_total"),
            rs.getString("chef_response_note"),
            integerOrNull(rs, "prep_time_minutes"),
            mapDropoffSnapshot(rs),
            mapPickupSnapshot(rs),
            listOrderItems(orderId),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private CustomerAddressSnapshotResponse mapDropoffSnapshot(ResultSet rs) throws SQLException {
        UUID sourceAddressId = rs.getObject("delivery_address_id", UUID.class);
        if (sourceAddressId == null) {
            return null;
        }
        return new CustomerAddressSnapshotResponse(
            sourceAddressId,
            rs.getString("dropoff_recipient_name"),
            rs.getString("dropoff_contact_phone"),
            rs.getString("dropoff_address_line1"),
            rs.getString("dropoff_address_line2"),
            rs.getString("dropoff_landmark"),
            rs.getString("dropoff_area_name"),
            rs.getString("dropoff_city"),
            rs.getString("dropoff_state"),
            rs.getString("dropoff_postal_code"),
            rs.getBigDecimal("dropoff_latitude"),
            rs.getBigDecimal("dropoff_longitude")
        );
    }

    private KitchenPickupSnapshotResponse mapPickupSnapshot(ResultSet rs) throws SQLException {
        if (!StringUtils.hasText(rs.getString("pickup_address_line1"))) {
            return null;
        }
        return new KitchenPickupSnapshotResponse(
            rs.getObject("kitchen_id", UUID.class),
            rs.getString("kitchen_name_snapshot"),
            rs.getString("pickup_phone_number"),
            rs.getString("pickup_email"),
            rs.getString("pickup_address_line1"),
            rs.getString("pickup_address_line2"),
            rs.getString("pickup_landmark"),
            rs.getString("pickup_area_name"),
            rs.getString("pickup_city"),
            rs.getString("pickup_state"),
            rs.getString("pickup_postal_code"),
            rs.getBigDecimal("pickup_latitude"),
            rs.getBigDecimal("pickup_longitude")
        );
    }

    private List<OrderItemResponse> listOrderItems(UUID orderId) {
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.order_item WHERE order_id = ? ORDER BY created_at ASC",
            (rs, rowNum) -> new OrderItemResponse(rs.getObject("id", UUID.class), rs.getObject("menu_item_id", UUID.class), rs.getString("item_name_snapshot"), rs.getString("category_snapshot"), rs.getString("food_type_snapshot"), rs.getBigDecimal("unit_price_snapshot"), rs.getInt("quantity"), rs.getBigDecimal("line_total")),
            orderId
        );
    }

    private ChargePolicyResponse mapChargePolicy(ResultSet rs, int rowNum) throws SQLException {
        return new ChargePolicyResponse(rs.getObject("id", UUID.class), rs.getString("policy_name"), rs.getBigDecimal("platform_fee_percent"), rs.getBigDecimal("platform_fee_flat"), rs.getBigDecimal("tax_percent"), rs.getBigDecimal("delivery_fee_flat"), rs.getBoolean("is_active"), instant(rs, "created_at"));
    }

    private static OrderPackaging calculatePackaging(
        List<CartItemResponse> cartItems,
        Map<UUID, CatalogMenuItem> catalogItems
    ) {
        long totalWeightGrams = 0;
        boolean thermoboxRequired = false;
        try {
            for (CartItemResponse cartItem : cartItems) {
                CatalogMenuItem catalogItem = catalogItems.get(cartItem.menuItemId());
                if (catalogItem == null || catalogItem.unitPackageWeightGrams() == null
                    || catalogItem.unitPackageWeightGrams() <= 0 || catalogItem.thermoboxRequired() == null) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Menu item delivery metadata is incomplete"
                    );
                }
                long lineWeight = Math.multiplyExact(
                    catalogItem.unitPackageWeightGrams().longValue(),
                    cartItem.quantity()
                );
                totalWeightGrams = Math.addExact(totalWeightGrams, lineWeight);
                thermoboxRequired = thermoboxRequired || catalogItem.thermoboxRequired();
            }
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calculated package weight is too large");
        }
        if (totalWeightGrams <= 0 || totalWeightGrams > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calculated package weight is invalid");
        }
        return new OrderPackaging((int) totalWeightGrams, thermoboxRequired);
    }

    private Charges calculateCharges(BigDecimal foodSubtotal, ChargePolicyResponse policy) {
        BigDecimal platform = foodSubtotal.multiply(policy.platformFeePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).add(policy.platformFeeFlat()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = foodSubtotal.multiply(policy.taxPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal delivery = policy.deliveryFeeFlat().setScale(2, RoundingMode.HALF_UP);
        return new Charges(platform, tax, delivery, foodSubtotal.add(platform).add(tax).add(delivery).setScale(2, RoundingMode.HALF_UP));
    }

    private void requireCustomer(CravesPrincipal principal) {
        if (principal == null || !principal.hasRole("CUSTOMER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer role is required");
        }
    }

    private void requireChef(CravesPrincipal principal) {
        if (principal == null || !principal.hasRole("CHEF")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chef role is required");
        }
    }

    private void requireAdmin(CravesPrincipal principal) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
    }

    private static String displayKitchenName(CatalogKitchen kitchen) {
        return StringUtils.hasText(kitchen.displayName()) ? kitchen.displayName() : kitchen.kitchenName();
    }

    private static String currency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : INR;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String safeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Charges(BigDecimal platformFee, BigDecimal taxAmount, BigDecimal deliveryFee, BigDecimal grandTotal) {
    }

    private record OrderPackaging(int totalPackageWeightGrams, boolean thermoboxRequired) {
    }

    private record PendingKitchenOrder(
        UUID kitchenId,
        CatalogKitchen kitchen,
        KitchenPickupSnapshotResponse pickup,
        List<CartItemResponse> items,
        BigDecimal foodSubtotal,
        Charges charges,
        OrderPackaging packaging
    ) {
    }
}
