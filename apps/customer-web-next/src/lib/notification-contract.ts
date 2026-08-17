export type CustomerNotification = {
  id: string; title: string; body: string; noticeType: string; targetType: string | null;
  targetId: string | null; readAt: string | null; createdAt: string;
};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function text(value: unknown, max: number): string | null { if (typeof value !== "string") return null; const result = value.trim(); return result && result.length <= max ? result : null; }
function instant(value: unknown): string | null { if (value == null) return null; return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
export function parseCustomerNotification(value: unknown): CustomerNotification | null {
  if (!value || typeof value !== "object") return null; const notice = value as Record<string, unknown>;
  const id = text(notice.id, 64); const title = text(notice.title, 180); const body = text(notice.body, 1_500);
  const noticeType = text(notice.noticeType, 80); const createdAt = instant(notice.createdAt); const readAt = instant(notice.readAt);
  const targetId = text(notice.targetId, 64);
  if (!id || !UUID.test(id) || !title || !body || !noticeType || !createdAt || (targetId && !UUID.test(targetId))) return null;
  return { id, title, body, noticeType, targetType: text(notice.targetType, 80), targetId, readAt, createdAt };
}
export function parseCustomerNotifications(value: unknown): CustomerNotification[] | null {
  if (!Array.isArray(value) || value.length > 100) return null; const notices = value.map(parseCustomerNotification);
  return notices.some((notice) => notice === null) ? null : notices as CustomerNotification[];
}
