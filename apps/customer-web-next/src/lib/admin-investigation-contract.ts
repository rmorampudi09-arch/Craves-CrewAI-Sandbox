export type AdminInvestigationResource = "order" | "payment" | "refund" | "delivery-command";

export type TimelineEntry = {
  label: string;
  status: string | null;
  occurredAt: string | null;
  detail: string | null;
};

export type AdminInvestigationResult = {
  resource: AdminInvestigationResource;
  resourceId: string;
  correlationId: string;
  title: string;
  status: string | null;
  summary: Array<{ label: string; value: string }>;
  timeline: TimelineEntry[];
};

const RESOURCE_TYPES = new Set<AdminInvestigationResource>([
  "order",
  "payment",
  "refund",
  "delivery-command"
]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function text(value: unknown, max = 500): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.replace(/[\r\n]+/g, " ").trim();
  return normalized && normalized.length <= max ? normalized : null;
}

function nullableText(value: unknown, max = 500): string | null {
  return value == null ? null : text(value, max);
}

function uuid(value: unknown): string | null {
  const candidate = text(value, 64);
  return candidate && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(candidate)
    ? candidate
    : null;
}

function dateTime(value: unknown): string | null {
  const candidate = nullableText(value, 80);
  return candidate && !Number.isNaN(Date.parse(candidate)) ? candidate : null;
}

function numberText(value: unknown): string | null {
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  if (typeof value === "string" && /^-?\d+(?:\.\d+)?$/.test(value.trim())) return value.trim();
  return null;
}

function valueOrDash(value: string | null): string {
  return value ?? "Not recorded";
}

function currency(amount: unknown, currencyCode: unknown): string {
  const amountText = numberText(amount);
  const code = text(currencyCode, 3);
  return amountText && code ? `${code} ${amountText}` : valueOrDash(amountText);
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value.slice(0, 100) : [];
}

export function parseAdminInvestigationRequest(value: unknown): {
  resource: AdminInvestigationResource;
  resourceId: string;
  reason: string;
} | null {
  const raw = record(value);
  if (!raw) return null;
  const resource = text(raw.resource, 40) as AdminInvestigationResource | null;
  const resourceId = uuid(raw.resourceId);
  const reason = text(raw.reason, 500);
  if (!resource || !RESOURCE_TYPES.has(resource) || !resourceId || !reason || reason.length < 10) return null;
  return { resource, resourceId, reason };
}

export function parseOrderInvestigation(value: unknown, correlationId: string): AdminInvestigationResult | null {
  const root = record(value);
  const order = record(root?.order);
  const orderId = uuid(order?.orderId);
  const status = nullableText(order?.status, 80);
  if (!root || !order || !orderId || !status) return null;

  const timeline: TimelineEntry[] = [];
  for (const entryValue of array(root.statusHistory)) {
    const entry = record(entryValue);
    if (!entry) continue;
    const oldStatus = nullableText(entry.oldStatus, 80);
    const newStatus = nullableText(entry.newStatus, 80);
    timeline.push({
      label: "Order status",
      status: newStatus,
      occurredAt: dateTime(entry.createdAt),
      detail: oldStatus ? `${oldStatus} → ${valueOrDash(newStatus)}` : newStatus
    });
  }
  for (const entryValue of array(root.deliveryHistory)) {
    const entry = record(entryValue);
    if (!entry) continue;
    const oldStatus = nullableText(entry.oldStatus, 80);
    const newStatus = nullableText(entry.newStatus, 80);
    timeline.push({
      label: "Delivery status",
      status: newStatus,
      occurredAt: dateTime(entry.observedAt) ?? dateTime(entry.createdAt),
      detail: oldStatus ? `${oldStatus} → ${valueOrDash(newStatus)}` : newStatus
    });
  }
  for (const entryValue of array(root.refundEvents)) {
    const entry = record(entryValue);
    if (!entry) continue;
    timeline.push({
      label: "Refund event",
      status: nullableText(entry.normalizedStatus, 80),
      occurredAt: dateTime(entry.receivedAt),
      detail: nullableText(entry.processingStatus, 80)
    });
  }

  timeline.sort((a, b) => (a.occurredAt ?? "").localeCompare(b.occurredAt ?? ""));
  return {
    resource: "order",
    resourceId: orderId,
    correlationId,
    title: nullableText(order.kitchenName, 200) ?? "Order investigation",
    status,
    summary: [
      { label: "Order total", value: currency(order.grandTotal, order.currency) },
      { label: "Order source", value: valueOrDash(nullableText(order.orderSource, 80)) },
      { label: "Financial allocation", value: valueOrDash(nullableText(order.financialAllocationStatus, 80)) },
      { label: "Delivery status", value: valueOrDash(nullableText(order.deliveryStatus, 80)) },
      { label: "Recipient phone", value: valueOrDash(nullableText(order.maskedRecipientPhone, 40)) },
      { label: "Drop-off area", value: [nullableText(order.areaName, 120), nullableText(order.city, 120)].filter(Boolean).join(", ") || "Not recorded" },
      { label: "Items", value: String(array(root.items).length) },
      { label: "Created", value: valueOrDash(dateTime(order.createdAt)) }
    ],
    timeline
  };
}

export function parsePaymentInvestigation(value: unknown, correlationId: string): AdminInvestigationResult | null {
  const root = record(value);
  const payment = record(root?.payment);
  const paymentOrderId = uuid(payment?.paymentOrderId);
  const status = nullableText(payment?.status, 80);
  if (!root || !payment || !paymentOrderId || !status) return null;

  const timeline: TimelineEntry[] = [];
  for (const attemptValue of array(root.attempts)) {
    const attempt = record(attemptValue);
    if (!attempt) continue;
    timeline.push({
      label: "Payment attempt",
      status: nullableText(attempt.paymentStatus, 80),
      occurredAt: dateTime(attempt.createdAt),
      detail: currency(attempt.amount, attempt.currency)
    });
  }
  for (const eventValue of array(root.events)) {
    const event = record(eventValue);
    if (!event) continue;
    timeline.push({
      label: nullableText(event.eventType, 120) ?? "Payment event",
      status: nullableText(event.paymentStatus, 80),
      occurredAt: dateTime(event.createdAt),
      detail: null
    });
  }
  timeline.sort((a, b) => (a.occurredAt ?? "").localeCompare(b.occurredAt ?? ""));

  return {
    resource: "payment",
    resourceId: paymentOrderId,
    correlationId,
    title: "Payment investigation",
    status,
    summary: [
      { label: "Amount", value: currency(payment.amount, payment.currency) },
      { label: "Provider status", value: valueOrDash(nullableText(payment.providerStatus, 80)) },
      { label: "Craves reference", value: valueOrDash(nullableText(payment.cravesReference, 150)) },
      { label: "Cashfree order", value: valueOrDash(nullableText(payment.cashfreeOrderId, 150)) },
      { label: "Attempts", value: String(array(root.attempts).length) },
      { label: "Events", value: String(array(root.events).length) },
      { label: "Created", value: valueOrDash(dateTime(payment.createdAt)) },
      { label: "Updated", value: valueOrDash(dateTime(payment.updatedAt)) }
    ],
    timeline
  };
}

export function parseRefundInvestigation(value: unknown, correlationId: string): AdminInvestigationResult | null {
  const root = record(value);
  const refund = record(root?.refund);
  const refundId = uuid(refund?.refundId);
  const status = nullableText(refund?.status, 80);
  if (!root || !refund || !refundId || !status) return null;

  const timeline: TimelineEntry[] = array(root.statusEvents).flatMap(entryValue => {
    const entry = record(entryValue);
    if (!entry) return [];
    return [{
      label: nullableText(entry.eventType, 120) ?? "Refund status event",
      status: nullableText(entry.status, 80),
      occurredAt: dateTime(entry.publishedAt) ?? dateTime(entry.createdAt),
      detail: nullableText(entry.lastError, 500)
    }];
  });

  return {
    resource: "refund",
    resourceId: refundId,
    correlationId,
    title: "Refund investigation",
    status,
    summary: [
      { label: "Amount", value: currency(refund.amount, refund.currency) },
      { label: "Provider status", value: valueOrDash(nullableText(refund.providerStatus, 80)) },
      { label: "Refund reference", value: valueOrDash(nullableText(refund.refundReference, 150)) },
      { label: "Attempts", value: valueOrDash(numberText(refund.attemptCount)) },
      { label: "Next attempt", value: valueOrDash(dateTime(refund.nextAttemptAt)) },
      { label: "Processed", value: valueOrDash(dateTime(refund.processedAt)) },
      { label: "Last error", value: valueOrDash(nullableText(refund.lastError, 500)) },
      { label: "Created", value: valueOrDash(dateTime(refund.createdAt)) }
    ],
    timeline
  };
}

export function parseDeliveryInvestigation(value: unknown, correlationId: string): AdminInvestigationResult | null {
  const root = record(value);
  const command = record(root?.command);
  const commandId = uuid(command?.commandId);
  const status = nullableText(command?.status, 80);
  if (!root || !command || !commandId || !status) return null;
  const job = record(root.job);

  const timeline: TimelineEntry[] = [
    { label: "Command created", status, occurredAt: dateTime(command.createdAt), detail: null },
    { label: "Dispatch target", status: null, occurredAt: dateTime(command.dispatchAt), detail: null }
  ];
  if (job) {
    timeline.push({
      label: "Provider booking",
      status: nullableText(job.status, 80),
      occurredAt: dateTime(job.bookedAt) ?? dateTime(job.createdAt),
      detail: nullableText(job.providerId, 100)
    });
    timeline.push({
      label: "Last provider observation",
      status: nullableText(job.providerStatus, 100),
      occurredAt: dateTime(job.lastStatusObservedAt),
      detail: nullableText(job.lastStatusSource, 100)
    });
  }

  return {
    resource: "delivery-command",
    resourceId: commandId,
    correlationId,
    title: "Delivery command investigation",
    status,
    summary: [
      { label: "Order", value: valueOrDash(uuid(command.orderId)) },
      { label: "Chef sub-order", value: valueOrDash(uuid(command.chefSubOrderId)) },
      { label: "Attempts", value: valueOrDash(numberText(command.attemptCount)) },
      { label: "Ready at", value: valueOrDash(dateTime(command.readyAt)) },
      { label: "Dispatch at", value: valueOrDash(dateTime(command.dispatchAt)) },
      { label: "Reconciliation provider", value: valueOrDash(nullableText(command.reconciliationProviderId, 100)) },
      { label: "Reconciliation attempts", value: valueOrDash(numberText(command.reconciliationAttemptCount)) },
      { label: "Last error", value: valueOrDash(nullableText(command.lastError, 500)) },
      { label: "Job status", value: valueOrDash(nullableText(job?.status, 80)) },
      { label: "Provider", value: valueOrDash(nullableText(job?.providerId, 100)) }
    ],
    timeline
  };
}

export function parseAdminInvestigationResult(
  resource: AdminInvestigationResource,
  value: unknown,
  correlationId: string
): AdminInvestigationResult | null {
  if (!uuid(correlationId)) return null;
  switch (resource) {
    case "order": return parseOrderInvestigation(value, correlationId);
    case "payment": return parsePaymentInvestigation(value, correlationId);
    case "refund": return parseRefundInvestigation(value, correlationId);
    case "delivery-command": return parseDeliveryInvestigation(value, correlationId);
  }
}
