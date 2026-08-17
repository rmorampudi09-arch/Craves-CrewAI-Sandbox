"use client";

import { useCallback, useEffect, useState } from "react";
import type { AdminChefApplication, AdminChefApplicationStatus } from "@/lib/admin-chef-review-contract";

export function AdminChefReviewList() {
  const [status, setStatus] = useState<AdminChefApplicationStatus>("PENDING");
  const [items, setItems] = useState<AdminChefApplication[]>([]);
  const [message, setMessage] = useState("Loading chef applications…");

  const load = useCallback(async () => {
    const response = await fetch(`/api/admin/chef-reviews?status=${status}`, { cache: "no-store" });
    const body = await response.json().catch(() => null);
    if (response.status === 401) throw new Error("Administrator session expired.");
    if (response.status === 403) throw new Error("Administrator access is required.");
    if (!response.ok) throw new Error("Chef applications are temporarily unavailable.");
    const applications = body as AdminChefApplication[];
    setItems(applications); setMessage(applications.length ? "" : `No ${status.toLowerCase()} chef applications.`);
  }, [status]);

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Chef applications are unavailable.")); }, [load]);

  return <section>
    <div className="flex flex-wrap gap-3">{(["PENDING", "APPROVED", "REJECTED"] as AdminChefApplicationStatus[]).map(value => <button key={value} type="button" onClick={() => setStatus(value)} className={`rounded-2xl px-4 py-2 font-bold ${status === value ? "bg-[#6930CA] text-white" : "border border-[#cfc4d7] bg-white text-[#5f506b]"}`}>{value}</button>)}</div>
    {message && <div className="mt-6 rounded-[24px] bg-[#FFF8EC] p-6 text-slate-950" role="status">{message}</div>}
    <div className="mt-6 space-y-4">{items.map(item => <a key={item.id} href={`/admin/chef-reviews/${item.id}`} className="block rounded-[26px] bg-[#FFF8EC] p-6 text-slate-950"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{item.status}</p><h2 className="mt-2 text-2xl font-bold">{item.firstName} {item.lastName}</h2><p className="mt-2 text-sm text-slate-600">{item.email} · {item.phoneNumber}</p></div><span className="text-sm text-slate-500">{new Date(item.submittedAt).toLocaleString("en-IN")}</span></div><p className="mt-4 text-sm text-slate-700">{item.city}, {item.state} · {item.documents.length} proof file(s)</p></a>)}</div>
  </section>;
}
