"use client";

import { useEffect, useState } from "react";
import type { ChefMenuItem } from "@/lib/chef-menu-contract";

export function ChefMenuMediaManager() {
  const [items, setItems] = useState<ChefMenuItem[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [reason, setReason] = useState("");
  const [image, setImage] = useState<File | null>(null);
  const [primary, setPrimary] = useState(true);
  const [message, setMessage] = useState("Loading menu items…");
  const [busy, setBusy] = useState(false);

  async function load() {
    const response = await fetch("/api/chef/menu", { cache: "no-store" });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error("Menu is temporarily unavailable.");
    const next = body as ChefMenuItem[];
    setItems(next);
    setSelectedId(current => current || next[0]?.id || "");
    setMessage("");
  }
  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Menu is temporarily unavailable.")); }, []);

  const selected = items.find(item => item.id === selectedId) ?? null;

  async function setAvailability(available: boolean) {
    if (!selected) return;
    setBusy(true); setMessage(available ? "Marking dish available…" : "Marking dish unavailable…");
    try {
      const response = await fetch(`/api/chef/menu/${selected.id}/availability`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ available, reason: reason || null }) });
      if (!response.ok) throw new Error("Availability could not be updated.");
      await load(); setReason(""); setMessage("Availability updated by Catalog Service.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Availability could not be updated."); }
    finally { setBusy(false); }
  }

  async function upload() {
    if (!selected || !image) { setMessage("Choose a menu item and image first."); return; }
    setBusy(true); setMessage("Uploading menu image…");
    try {
      const data = new FormData(); data.set("file", image); data.set("primary", String(primary));
      const response = await fetch(`/api/chef/menu/${selected.id}/images`, { method: "POST", body: data });
      if (!response.ok) throw new Error("Image upload failed. Use JPG, PNG or WebP under 10 MB.");
      setImage(null); await load(); setMessage("Menu image uploaded by Catalog Service.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Image upload failed."); }
    finally { setBusy(false); }
  }

  return <div className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950"><h2 className="text-2xl font-bold">Select dish</h2><p role="status" className="mt-3 text-sm text-slate-600">{message}</p><div className="mt-5 space-y-3">{items.map(item => <button type="button" key={item.id} onClick={() => setSelectedId(item.id)} className={`w-full rounded-2xl p-4 text-left ${selectedId === item.id ? "bg-[#6930CA] text-white" : "bg-white"}`}><div className="flex justify-between gap-3"><strong>{item.itemName}</strong><span>{item.available ? "Available" : "Unavailable"}</span></div><p className={`mt-2 text-sm ${selectedId === item.id ? "text-white/80" : "text-slate-600"}`}>{item.status} · {item.images.length} image{item.images.length === 1 ? "" : "s"}</p></button>)}</div></section>
    <section className="rounded-[30px] bg-white p-6 text-slate-950"><h2 className="text-2xl font-bold">{selected?.itemName ?? "Dish operations"}</h2>{selected ? <>
      <div className="mt-5 grid gap-3 sm:grid-cols-2">{selected.images.map(itemImage => <div key={itemImage.id} className="overflow-hidden rounded-2xl border">{itemImage.publicUrl ? <img src={itemImage.publicUrl} alt="" className="h-44 w-full object-cover" referrerPolicy="no-referrer" /> : <div className="flex h-44 items-center justify-center bg-slate-100">Image unavailable</div>}<p className="p-3 text-sm text-slate-600">{itemImage.primary ? "Primary image" : `Image ${itemImage.sortOrder + 1}`}</p></div>)}</div>
      <div className="mt-6 rounded-2xl bg-[#FFF8EC] p-5"><h3 className="font-bold">Availability</h3><label className="mt-3 block text-sm font-semibold">Optional reason<input value={reason} onChange={event => setReason(event.target.value)} maxLength={500} className="mt-2 w-full rounded-2xl border bg-white px-4 py-3" /></label><div className="mt-4 flex flex-wrap gap-3"><button disabled={busy} onClick={() => void setAvailability(true)} className="rounded-full bg-[#6930CA] px-5 py-3 font-bold text-white disabled:opacity-50">Mark available</button><button disabled={busy} onClick={() => void setAvailability(false)} className="rounded-full border border-[#6930CA] px-5 py-3 font-bold text-[#6930CA] disabled:opacity-50">Mark unavailable</button></div></div>
      <div className="mt-6 rounded-2xl bg-[#FFF8EC] p-5"><h3 className="font-bold">Upload image</h3><input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => setImage(event.target.files?.[0] ?? null)} className="mt-3 block w-full rounded-2xl border bg-white px-4 py-3" /><label className="mt-4 flex items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={primary} onChange={event => setPrimary(event.target.checked)} />Set as primary</label><button disabled={busy || !image} onClick={() => void upload()} className="mt-4 rounded-full bg-[#6930CA] px-5 py-3 font-bold text-white disabled:opacity-50">Upload image</button></div>
    </> : <p className="mt-4 text-slate-600">Create a menu item before managing images or availability.</p>}</section>
  </div>;
}
