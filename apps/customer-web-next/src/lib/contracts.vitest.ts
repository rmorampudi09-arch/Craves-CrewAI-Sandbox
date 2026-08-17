import { describe, expect, it } from "vitest";
import { parseIdentity } from "./auth-contract";
import { parsePaymentSession } from "./payment-contract";
import { parseCustomerNotifications } from "./notification-contract";
import { parseDeliveryStatusResponse, safeTrackingUrl } from "./delivery-status";

const id = "123e4567-e89b-42d3-a456-426614174000";
const id2 = "123e4567-e89b-42d3-a456-426614174001";

describe("backend boundary contracts", () => {
  it("accepts a valid Firebase-backed Craves identity", () => {
    expect(parseIdentity({ id, phoneNumber: "+919876543210", email: null, emailVerified: false, displayName: "Craves Customer", status: "ACTIVE", roles: ["CUSTOMER"] }))
      .toMatchObject({ id, phoneNumber: "+919876543210", roles: ["CUSTOMER"] });
  });

  it("rejects a payment session without a backend-issued hosted session id", () => {
    expect(parsePaymentSession({ paymentOrderId: id, checkoutId: id2, amount: 250, currency: "INR", status: "CREATED", createdAt: "2026-08-02T08:00:00Z" })).toBeNull();
    expect(parsePaymentSession({ paymentOrderId: id, checkoutId: id2, provider: "CASHFREE", providerOrderId: "order_123", paymentSessionId: "session_from_cashfree", amount: 250, currency: "INR", status: "CREATED", createdAt: "2026-08-02T08:00:00Z" }))
      .toMatchObject({ paymentOrderId: id, checkoutId: id2, currency: "INR" });
  });

  it("validates notification ids and timestamps", () => {
    expect(parseCustomerNotifications([{ id, title: "Order update", body: "Your order is ready.", noticeType: "ORDER_STATUS", targetType: "ORDER", targetId: id2, readAt: null, createdAt: "2026-08-02T08:00:00Z" }])?.[0].readAt).toBeNull();
    expect(parseCustomerNotifications([{ id: "not-a-uuid", title: "Bad", body: "Bad", noticeType: "ORDER", createdAt: "today" }])).toBeNull();
  });

  it("keeps delivery tracking HTTPS-only", () => {
    expect(safeTrackingUrl("javascript:alert(1)")).toBeNull();
    expect(parseDeliveryStatusResponse({ orderId: id, deliveryJobId: id2, providerId: "provider", status: "IN_TRANSIT", trackingUrl: "https://tracking.example/order", observedAt: "2026-08-02T08:00:00Z", history: [{ oldStatus: "PICKED_UP", newStatus: "IN_TRANSIT", trackingUrl: "https://tracking.example/order", observedAt: "2026-08-02T08:00:00Z", recordedAt: "2026-08-02T08:00:01Z" }] }).status).toBe("IN_TRANSIT");
  });
});
