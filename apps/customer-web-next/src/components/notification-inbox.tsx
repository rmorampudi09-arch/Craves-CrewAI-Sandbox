"use client";

import { useEffect, useMemo, useState } from "react";
import type { CustomerNotification } from "@/lib/notification-contract";

const dateTime = new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" });

export function NotificationInbox() {
  const [notices, setNotices] = useState<CustomerNotification[] | null>(null);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");
  const [reload, setReload] = useState(0);
  const unread = useMemo(() => notices?.filter(notice => !notice.readAt).length ?? 0, [notices]);

  useEffect(() => {
    const controller = new AbortController();
    setError("");
    fetch("/api/notifications?limit=50", { cache: "no-store", signal: controller.signal })
      .then(async response => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.code ?? `HTTP_${response.status}`);
        return body as CustomerNotification[];
      })
      .then(setNotices)
      .catch(error => { if (error.name !== "AbortError") setError(error.message || "NOTIFICATIONS_UNAVAILABLE"); });
    return () => controller.abort();
  }, [reload]);

  async function markRead(notice: CustomerNotification) {
    if (notice.readAt || busyId) return;
    setBusyId(notice.id);
    try {
      const response = await fetch(`/api/notifications/${notice.id}/read`, { method: "PATCH" });
      if (!response.ok) throw new Error();
      const readAt = new Date().toISOString();
      setNotices(current => current?.map(item => item.id === notice.id ? { ...item, readAt } : item) ?? null);
    } catch {
      setError("NOTICE_UPDATE_FAILED");
    } finally {
      setBusyId("");
    }
  }

  if (error === "SESSION_EXPIRED" || error === "AUTHENTICATION_REQUIRED") return <section className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-950"><h2 className="text-2xl font-bold">Please sign in again</h2><p className="mt-3 text-sm text-slate-600">Your secure customer session has expired.</p><a className="mt-6 inline-flex rounded-full bg-[#6930CA] px-5 py-3 font-semibold text-white" href="/sign-in?returnTo=/notifications">Sign in</a></section>;
  if (error) return <section className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-950"><h2 className="text-2xl font-bold">Notifications are unavailable</h2><button className="mt-6 rounded-full bg-[#6930CA] px-5 py-3 font-semibold text-white" onClick={() => setReload(value => value + 1)}>Try again</button></section>;
  if (!notices) return <div className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-600">Loading notifications…</div>;

  return <section className="rounded-[30px] bg-[#FFF8EC] p-5 text-slate-950 shadow-xl shadow-black/20 sm:p-7">
    <div className="flex items-center justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Inbox</p><h2 className="mt-2 text-2xl font-bold">{unread} unread</h2></div><button className="rounded-full border border-[#6930CA] px-4 py-2 text-sm font-semibold text-[#6930CA]" onClick={() => setReload(value => value + 1)}>Refresh</button></div>
    {notices.length === 0 ? <p className="mt-6 text-sm text-slate-600">No notifications yet.</p> : <div className="mt-6 divide-y divide-slate-200">{notices.map(notice => <article key={notice.id} className={`py-5 ${notice.readAt ? "opacity-65" : ""}`}>
      <div className="flex gap-4"><span className={`mt-2 h-2.5 w-2.5 shrink-0 rounded-full ${notice.readAt ? "bg-slate-300" : "bg-[#F6B545]"}`} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-start justify-between gap-2"><h3 className="font-bold">{notice.title}</h3><time className="text-xs text-slate-500">{dateTime.format(new Date(notice.createdAt))}</time></div><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-600">{notice.body}</p>{!notice.readAt && <button className="mt-3 text-sm font-semibold text-[#6930CA] disabled:opacity-50" disabled={busyId === notice.id} onClick={() => void markRead(notice)}>{busyId === notice.id ? "Updating…" : "Mark as read"}</button>}</div></div>
    </article>)}</div>}
  </section>;
}
