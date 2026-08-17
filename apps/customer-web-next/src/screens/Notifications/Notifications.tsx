"use client";

import { useEffect, useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, Bell, CheckCheck } from "lucide-react";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import type { CustomerNotification } from "@/lib/notification-contract";
import { loadSession } from "@/services/auth/cravesAuth";

export default function NotificationsPage() {
  const navigate = useNavigate();
  const [notices, setNotices] = useState<CustomerNotification[]>([]);
  const [message, setMessage] = useState("Loading notifications…");
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      if (!(await loadSession())) {
        navigate({ to: "/" });
        return;
      }
      const response = await fetch("/api/notifications/in-app?limit=50", {
        cache: "no-store",
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(body?.message || "Notifications could not be loaded.");
      }
      setNotices(body);
      const unread = body.filter(
        (notice: CustomerNotification) => !notice.readAt,
      ).length;
      setMessage(
        body.length
          ? `${unread} unread notification${unread === 1 ? "" : "s"}.`
          : "No notifications yet.",
      );
    })().catch((error) =>
      setMessage(
        error instanceof Error
          ? error.message
          : "Notifications could not be loaded.",
      ),
    );
  }, [navigate]);

  async function markRead(notice: CustomerNotification) {
    if (notice.readAt) return;
    setBusyId(notice.id);
    try {
      const response = await fetch(
        `/api/notifications/in-app/${notice.id}/read`,
        { method: "PATCH" },
      );
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message || "Notification could not be updated.");
      }
      const readAt = new Date().toISOString();
      setNotices((current) =>
        current.map((item) =>
          item.id === notice.id ? { ...item, readAt } : item,
        ),
      );
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Notification could not be updated.",
      );
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="min-h-screen bg-white pb-12">
      <header className="border-b border-border bg-white/95">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-4">
          <Link to="/home" className="rounded-full border border-border bg-white p-2">
            <ArrowLeft className="h-5 w-5 text-ink" />
          </Link>
          <div>
            <p className="font-script text-primary">Stay updated</p>
            <h1 className="font-display text-xl font-bold text-ink">
              Notifications
            </h1>
          </div>
        </div>
        <div className="mx-auto max-w-3xl px-4 pb-3">
          <PersistentCustomerServiceNav />
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 pt-6">
        {notices.length ? (
          <ul className="space-y-3">
            {notices.map((notice) => (
              <li key={notice.id}>
                <button
                  type="button"
                  disabled={busyId === notice.id}
                  onClick={() => void markRead(notice)}
                  className={`w-full rounded-2xl border p-4 text-left shadow-sm ${
                    notice.readAt
                      ? "border-border bg-card"
                      : "border-primary/40 bg-primary/5"
                  }`}
                >
                  <div className="flex items-start gap-3">
                    <span
                      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                        notice.readAt
                          ? "bg-secondary text-muted-foreground"
                          : "bg-primary text-white"
                      }`}
                    >
                      {notice.readAt ? (
                        <CheckCheck className="h-5 w-5" />
                      ) : (
                        <Bell className="h-5 w-5" />
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-display text-base font-bold text-ink">
                        {notice.title}
                      </span>
                      <span className="mt-1 block text-sm leading-6 text-muted-foreground">
                        {notice.body}
                      </span>
                      <span className="mt-2 block text-[11px] text-muted-foreground">
                        {new Date(notice.createdAt).toLocaleString("en-IN")} · {notice.readAt ? "Read" : "Tap to mark read"}
                      </span>
                    </span>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center">
            <Bell className="mx-auto h-10 w-10 text-muted-foreground" />
            <h2 className="mt-3 font-display text-xl font-bold text-ink">
              No notifications yet
            </h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Order and account updates will appear here.
            </p>
          </div>
        )}
        <p role="status" className="mt-4 text-sm text-muted-foreground">
          {message}
        </p>
      </main>
    </div>
  );
}
