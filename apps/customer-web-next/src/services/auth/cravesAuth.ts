"use client";

import type { CravesIdentity } from "@/lib/auth-contract";
import type {
  CustomerAddress,
  DeliveryReadyAddress,
} from "@/lib/address-contract";
import { selectActiveDeliveryAddress } from "@/lib/address-selection";
import {
  parseCustomerProfile,
  type CustomerProfile,
} from "@/lib/profile-contract";

export type CravesUser = {
  id: string;
  phone: string;
  phoneNumber: string;
  username: string;
  firstName: string | null;
  lastName: string | null;
  profileComplete: boolean;
  createdAt: number;
  email?: string;
  roles: string[];
  status: string;
};

export type CravesAddress = {
  id?: string;
  label?: string;
  hno: string;
  street?: string;
  city: string;
  mandal: string;
  district: string;
  pincode?: string;
  lat?: number;
  lng?: number;
};

let session: CravesUser | null = null;
let selectedLocation: CravesAddress | null = null;
let roleSynchronization: Promise<CravesUser | null> | null = null;
const listeners = new Set<() => void>();

function fromIdentity(identity: CravesIdentity): CravesUser {
  const digits = identity.phoneNumber.replace(/\D/g, "");
  const displayName = identity.displayName?.trim() || identity.phoneNumber;
  return {
    id: identity.id,
    phone: digits.length > 10 ? digits.slice(-10) : digits,
    phoneNumber: identity.phoneNumber,
    username: displayName,
    firstName: null,
    lastName: null,
    profileComplete: false,
    createdAt: Date.now(),
    email: identity.email ?? undefined,
    roles: identity.roles,
    status: identity.status,
  };
}

function notify() {
  for (const listener of listeners) listener();
}

function withCustomerProfile(current: CravesUser, profile: CustomerProfile): CravesUser {
  const username = `${profile.firstName} ${profile.lastName}`.trim();
  return {
    ...current,
    username,
    firstName: profile.firstName,
    lastName: profile.lastName,
    profileComplete: true,
    email: profile.email ?? undefined,
    phone: profile.registeredPhoneNumber.replace(/\D/g, "").slice(-10),
    phoneNumber: profile.registeredPhoneNumber,
  };
}

async function hydrateCustomerProfile(current: CravesUser): Promise<CravesUser> {
  const isCustomer = current.roles.some((role) => role.toUpperCase() === "CUSTOMER");
  if (!isCustomer) return current;

  const response = await fetch("/api/customer/profile", {
    cache: "no-store",
    credentials: "same-origin",
  }).catch(() => null);
  if (!response?.ok) return current;

  const profile = parseCustomerProfile(await response.json().catch(() => null));
  if (!profile) return current;

  session = withCustomerProfile(current, profile);
  notify();
  return session;
}

export function setSessionIdentity(identity: CravesIdentity): CravesUser {
  session = fromIdentity(identity);
  notify();
  return session;
}

export function setSessionProfile(profile: CustomerProfile): CravesUser | null {
  if (!session) return null;
  session = withCustomerProfile(session, profile);
  notify();
  return session;
}

export function getSession(): CravesUser | null {
  return session;
}

export async function loadSession(): Promise<CravesUser | null> {
  const lookup = async () => fetch("/api/auth/me", { cache: "no-store", credentials: "same-origin" });
  let response = await lookup();
  if (response.status === 401) {
    const refreshed = await fetch("/api/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
    }).catch(() => null);
    if (refreshed?.ok) response = await lookup();
  }
  if (!response.ok) {
    session = null;
    notify();
    return null;
  }
  const identity = (await response.json().catch(() => null)) as CravesIdentity | null;
  if (!identity?.id) return null;
  const current = setSessionIdentity(identity);
  return hydrateCustomerProfile(current);
}

export async function synchronizeSessionRoles(): Promise<CravesUser | null> {
  if (roleSynchronization) return roleSynchronization;
  roleSynchronization = (async () => {
    const response = await fetch("/api/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
    }).catch(() => null);
    if (!response?.ok) return null;
    const body = (await response.json().catch(() => null)) as { identity?: CravesIdentity } | null;
    if (!body?.identity?.id) return null;
    const current = setSessionIdentity(body.identity);
    return hydrateCustomerProfile(current);
  })().finally(() => {
    roleSynchronization = null;
  });
  return roleSynchronization;
}

export async function clearSession(): Promise<void> {
  try {
    await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
  } finally {
    session = null;
    selectedLocation = null;
    notify();
  }
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function saveAddress(address: CravesAddress) {
  selectedLocation = address;
}

export function getAddress(): CravesAddress | null {
  return selectedLocation;
}

function fromCustomerAddress(address: DeliveryReadyAddress): CravesAddress {
  return {
    id: address.id,
    label: address.addressLabel,
    hno: address.addressLine1,
    street: address.addressLine2 ?? address.landmark ?? undefined,
    city: address.city,
    mandal: address.areaName,
    district: address.districtName ?? address.city,
    pincode: address.postalCode,
    lat: address.latitude,
    lng: address.longitude,
  };
}

export async function loadSelectedAddress(): Promise<CravesAddress | null> {
  const response = await fetch("/api/customer/addresses", {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok) throw new Error("Saved delivery addresses could not be loaded.");
  const addresses = (await response.json().catch(() => null)) as CustomerAddress[] | null;
  if (!Array.isArray(addresses)) throw new Error("Saved delivery addresses returned an invalid response.");
  const selected = selectActiveDeliveryAddress(addresses);
  selectedLocation = selected ? fromCustomerAddress(selected) : null;
  return selectedLocation;
}
