"use client";

import { useEffect, useState } from "react";
import type { ChefMenuItem, ChefMenuItemInput, FoodType, MenuItemStatus, SpiceLevel } from "@/lib/chef-menu-contract";

type FormState = {
  id: string | null;
  itemName: string;
  description: string;
  category: string;
  foodType: FoodType;
  price: string;
  currency: string;
  servesCount: string;
  preparationTimeMinutes: string;
  spiceLevel: SpiceLevel | "";
  unitPackageWeightGrams: string;
  thermoboxRequired: boolean;
  available: boolean;
  status: MenuItemStatus;
};

const EMPTY: FormState = { id: null, itemName: "", description: "", category: "", foodType: "VEG", price: "", currency: "INR", servesCount: "", preparationTimeMinutes: "", spiceLevel: "", unitPackageWeightGrams: "", thermoboxRequired: false, available: false, status: "DRAFT" };

function toForm(item: ChefMenuItem): FormState {
  return { id: item.id, itemName: item.itemName, description: item.description ?? "", category: item.category, foodType: item.foodType, price: String(item.price), currency: item.currency, servesCount: item.servesCount === null ? "" : String(item.servesCount), preparationTimeMinutes: item.preparationTimeMinutes === null ? "" : String(item.preparationTimeMinutes), spiceLevel: item.spiceLevel ?? "", unitPackageWeightGrams: String(item.unitPackageWeightGrams), thermoboxRequired: item.thermoboxRequired, available: item.available, status: item.status };
}

function toInput(form: FormState): ChefMenuItemInput {
  return { itemName: form.itemName, description: form.description || null, category: form.category, foodType: form.foodType, price: Number(form.price), currency: form.currency, servesCount: form.servesCount ? Number(form.servesCount) : null, preparationTimeMinutes: form.preparationTimeMinutes ? Number(form.preparationTimeMinutes) : null, spiceLevel: form.spiceLevel || null, unitPackageWeightGrams: Number(form.unitPackageWeightGrams), thermoboxRequired: form.thermoboxRequired, available: form.available, status: form.status };
}

export function ChefMenuManager() {
  const [items, setItems] = useState<ChefMenuItem[]>([]);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [message, setMessage] = useState("Loading menu items…");
  const [busy, setBusy] = useState(false);

  async function load() {
    const response = await fetch("/api/chef/menu", { cache: "no-store" });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(response.status === 403 ? "An approved chef kitchen is required." : "Menu is temporarily unavailable.");
    setItems(body as ChefMenuItem[]);
    setMessage("");
  }
  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Menu is temporarily unavailable.")); }, []);

  function update<K extends keyof FormState>(name: K, value: FormState[K]) { setForm(current => ({ ...current, [name]: value })); }

  async function save() {
    setBusy(true); setMessage(form.id ? "Updating menu item…" : "Creating menu item…");
    try {
      const response = await fetch(form.id ? `/api/chef/menu/${form.id}` : "/api/chef/menu", { method: form.id ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(toInput(form)) });
      if (!response.ok) throw new Error(response.status === 400 ? "Complete the required dish fields using valid values." : "Menu item could not be saved.");
      await load(); setForm(EMPTY); setMessage("Menu item saved by Catalog Service.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Menu item could not be saved."); }
    finally { setBusy(false); }
  }

  return <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950"><div className="flex items-center justify-between gap-3"><h2 className="text-2xl font-bold">Your menu</h2><button type="button" onClick={() => setForm(EMPTY)} className="rounded-full border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA]">New dish</button></div><p role="status" className="mt-3 text-sm text-slate-600">{message}</p><div className="mt-5 space-y-3">{items.map(item => <button key={item.id} type="button" onClick={() => setForm(toForm(item))} className="w-full rounded-2xl bg-white p-4 text-left"><div className="flex justify-between gap-3"><strong>{item.itemName}</strong><span>{item.currency} {item.price.toFixed(2)}</span></div><div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-600"><span>{item.category}</span><span>·</span><span>{item.foodType.replace("_", " ")}</span><span>·</span><span>{item.status}</span><span>·</span><span>{item.available ? "Available" : "Unavailable"}</span></div></button>)}</div></section>

    <section className="rounded-[30px] bg-white p-6 text-slate-950"><h2 className="text-2xl font-bold">{form.id ? "Edit dish" : "Create dish"}</h2><div className="mt-5 grid gap-4 md:grid-cols-2">
      <label className="text-sm font-semibold">Item name *<input value={form.itemName} onChange={e => update("itemName", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Category *<input value={form.category} onChange={e => update("category", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold md:col-span-2">Description<textarea value={form.description} onChange={e => update("description", e.target.value)} className="mt-2 min-h-24 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Food type<select value={form.foodType} onChange={e => update("foodType", e.target.value as FoodType)} className="mt-2 w-full rounded-2xl border bg-white px-4 py-3"><option value="VEG">Veg</option><option value="NON_VEG">Non-veg</option><option value="EGG">Egg</option></select></label>
      <label className="text-sm font-semibold">Spice level<select value={form.spiceLevel} onChange={e => update("spiceLevel", e.target.value as SpiceLevel | "")} className="mt-2 w-full rounded-2xl border bg-white px-4 py-3"><option value="">Not specified</option><option value="MILD">Mild</option><option value="MEDIUM">Medium</option><option value="SPICY">Spicy</option></select></label>
      <label className="text-sm font-semibold">Price *<input inputMode="decimal" value={form.price} onChange={e => update("price", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Currency *<input value={form.currency} onChange={e => update("currency", e.target.value.toUpperCase())} maxLength={3} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Serves count<input inputMode="numeric" value={form.servesCount} onChange={e => update("servesCount", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Preparation minutes<input inputMode="numeric" value={form.preparationTimeMinutes} onChange={e => update("preparationTimeMinutes", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Package weight grams *<input inputMode="numeric" value={form.unitPackageWeightGrams} onChange={e => update("unitPackageWeightGrams", e.target.value)} className="mt-2 w-full rounded-2xl border px-4 py-3" /></label>
      <label className="text-sm font-semibold">Status<select value={form.status} onChange={e => update("status", e.target.value as MenuItemStatus)} className="mt-2 w-full rounded-2xl border bg-white px-4 py-3"><option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></label>
    </div><div className="mt-5 flex flex-wrap gap-5"><label className="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={form.available} onChange={e => update("available", e.target.checked)} />Available</label><label className="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={form.thermoboxRequired} onChange={e => update("thermoboxRequired", e.target.checked)} />Thermobox required</label></div><button type="button" disabled={busy} onClick={() => void save()} className="mt-6 rounded-full bg-[#6930CA] px-6 py-3 font-bold text-white disabled:opacity-50">Save dish</button></section>
  </div>;
}
