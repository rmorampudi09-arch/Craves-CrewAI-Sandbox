"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  Auth,
  ConfirmationResult,
  RecaptchaVerifier,
  signInWithPhoneNumber
} from "firebase/auth";
import {
  FirebaseBrowserConfig,
  getDefaultFirebaseConfig,
  getFirebaseClient,
  isFirebaseConfigReady
} from "../lib/firebase";

type ExchangeResponse = {
  tokenType?: string;
  accessToken?: string;
  expiresIn?: number;
  refreshToken?: string;
  refreshTokenExpiresAt?: string;
  identity?: Record<string, unknown>;
  code?: string;
  message?: string;
  [key: string]: unknown;
};

type NotificationItem = {
  id: string;
  title: string;
  body: string;
  noticeType: string;
  targetType: string;
  targetId: string | null;
  readAt: string | null;
  createdAt: string;
};

type ApiResult = {
  status: number;
  ok: boolean;
  body: unknown;
  raw: string;
};

type StatusType = "idle" | "ok" | "error";

type LogItem = {
  title: string;
  value: unknown;
};

const CONFIG_STORAGE_KEY = "craves.firebase.web.config.v2";
const DEFAULT_APIM_BASE_URL = "https://api.craves.in";
const DEFAULT_PROFILE_PAYLOAD = `{
  "displayName": "Raviteja Test Customer",
  "email": "raviteja.test@example.com"
}`;
const DEFAULT_ADDRESS_PAYLOAD = `{
  "label": "Home",
  "line1": "Test address line 1",
  "line2": "Test address line 2",
  "city": "Hyderabad",
  "state": "Telangana",
  "postalCode": "500001",
  "latitude": 17.385,
  "longitude": 78.4867,
  "isDefault": true
}`;

function formatJson(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value, null, 2);
}

function maskToken(token: string): string {
  if (!token) {
    return "";
  }

  if (token.length <= 28) {
    return token;
  }

  return `${token.slice(0, 14)}...${token.slice(-14)}`;
}

function apimBaseUrl(): string {
  const value = process.env.NEXT_PUBLIC_APIM_BASE_URL || DEFAULT_APIM_BASE_URL;
  return value.replace(/\/$/, "");
}

async function parseApiResponse(response: Response): Promise<ApiResult> {
  const raw = await response.text();
  let body: unknown = null;

  if (raw) {
    try {
      body = JSON.parse(raw);
    } catch {
      body = raw;
    }
  }

  return {
    status: response.status,
    ok: response.ok,
    body,
    raw
  };
}

function readAccessToken(payload: unknown): string {
  if (!payload || typeof payload !== "object") {
    return "";
  }

  const root = payload as Record<string, unknown>;
  const direct = root.accessToken || root.access_token || root.jwt || root.token;
  return typeof direct === "string" ? direct : "";
}

function readRefreshToken(payload: unknown): string {
  if (!payload || typeof payload !== "object") {
    return "";
  }

  const root = payload as Record<string, unknown>;
  const direct = root.refreshToken || root.refresh_token;
  return typeof direct === "string" ? direct : "";
}

function selectedClass(isSelected: boolean): string {
  return isSelected ? "notification selected" : "notification";
}

function parseJsonPayload(input: string, label: string): unknown {
  try {
    return JSON.parse(input);
  } catch (error) {
    const detail = error instanceof Error ? error.message : "Invalid JSON.";
    throw new Error(`${label} is not valid JSON. ${detail}`);
  }
}

function readIdFromObject(value: unknown): string {
  if (!value || typeof value !== "object") {
    return "";
  }

  const root = value as Record<string, unknown>;
  const direct = root.id || root.addressId || root.customerAddressId;
  return typeof direct === "string" ? direct : "";
}

function readFirstAddressId(value: unknown): string {
  if (Array.isArray(value)) {
    return readIdFromObject(value[0]);
  }

  if (!value || typeof value !== "object") {
    return "";
  }

  const root = value as Record<string, unknown>;
  const possibleArrays = [root.addresses, root.items, root.data, root.content];

  for (const possibleArray of possibleArrays) {
    if (Array.isArray(possibleArray)) {
      const id = readIdFromObject(possibleArray[0]);
      if (id) {
        return id;
      }
    }
  }

  return readIdFromObject(value);
}

function readArrayFromResponse(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }

  if (!value || typeof value !== "object") {
    return [];
  }

  const root = value as Record<string, unknown>;
  const possibleArrays = [root.addresses, root.items, root.data, root.content];

  for (const possibleArray of possibleArrays) {
    if (Array.isArray(possibleArray)) {
      return possibleArray;
    }
  }

  return [];
}

export default function ApiTestDashboardPage() {
  const [firebaseConfig, setFirebaseConfig] = useState<FirebaseBrowserConfig>(getDefaultFirebaseConfig);
  const [phoneNumber, setPhoneNumber] = useState("+91");
  const [otp, setOtp] = useState("");
  const [confirmationResult, setConfirmationResult] = useState<ConfirmationResult | null>(null);
  const [firebaseIdToken, setFirebaseIdToken] = useState("");
  const [cravesAccessToken, setCravesAccessToken] = useState("");
  const [cravesRefreshToken, setCravesRefreshToken] = useState("");
  const [exchangeResponse, setExchangeResponse] = useState<unknown>(null);
  const [meResponse, setMeResponse] = useState<unknown>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [selectedNoticeId, setSelectedNoticeId] = useState("");
  const [customerProfileResponse, setCustomerProfileResponse] = useState<unknown>(null);
  const [customerAddressesResponse, setCustomerAddressesResponse] = useState<unknown>(null);
  const [customerAddresses, setCustomerAddresses] = useState<unknown[]>([]);
  const [profilePayload, setProfilePayload] = useState(DEFAULT_PROFILE_PAYLOAD);
  const [addressPayload, setAddressPayload] = useState(DEFAULT_ADDRESS_PAYLOAD);
  const [addressId, setAddressId] = useState("");
  const [status, setStatus] = useState<{ type: StatusType; message: string }>({ type: "idle", message: "Ready." });
  const [busy, setBusy] = useState(false);
  const [logs, setLogs] = useState<LogItem[]>([]);

  const baseUrl = useMemo(() => apimBaseUrl(), []);
  const configReady = isFirebaseConfigReady(firebaseConfig);
  const unreadCount = useMemo(() => notifications.filter((notice) => !notice.readAt).length, [notifications]);

  useEffect(() => {
    const saved = window.localStorage.getItem(CONFIG_STORAGE_KEY);
    if (!saved) {
      return;
    }

    try {
      setFirebaseConfig(JSON.parse(saved) as FirebaseBrowserConfig);
    } catch {
      window.localStorage.removeItem(CONFIG_STORAGE_KEY);
    }
  }, []);

  function addLog(title: string, value: unknown): void {
    setLogs((current) => [{ title, value }, ...current].slice(0, 16));
  }

  function updateConfigField(field: keyof FirebaseBrowserConfig, value: string): void {
    setFirebaseConfig((current) => ({ ...current, [field]: value.trim() }));
  }

  function saveFirebaseConfig(): void {
    window.localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(firebaseConfig));
    setStatus({ type: "ok", message: "Firebase Web Config saved in this browser." });
  }

  function clearFirebaseConfig(): void {
    window.localStorage.removeItem(CONFIG_STORAGE_KEY);
    setFirebaseConfig(getDefaultFirebaseConfig());
    setStatus({ type: "idle", message: "Firebase Web Config reset to environment values." });
  }

  async function ensureRecaptcha(auth: Auth): Promise<RecaptchaVerifier> {
    const windowWithVerifier = window as typeof window & { cravesRecaptchaVerifier?: RecaptchaVerifier };

    if (windowWithVerifier.cravesRecaptchaVerifier) {
      return windowWithVerifier.cravesRecaptchaVerifier;
    }

    const verifier = new RecaptchaVerifier(auth, "recaptcha-container", {
      size: "normal",
      callback: () => {
        setStatus({ type: "ok", message: "reCAPTCHA verified. You can send OTP now." });
      },
      "expired-callback": () => {
        setStatus({ type: "error", message: "reCAPTCHA expired. Refresh the page and try again." });
      }
    });

    await verifier.render();
    windowWithVerifier.cravesRecaptchaVerifier = verifier;
    return verifier;
  }

  async function callCravesApi(path: string, options: RequestInit = {}): Promise<ApiResult> {
    if (!cravesAccessToken) {
      throw new Error("Exchange with Craves first to get access token.");
    }

    const headers = new Headers(options.headers);
    headers.set("Authorization", `Bearer ${cravesAccessToken}`);

    if (options.body && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers
    });

    return parseApiResponse(response);
  }

  async function sendOtp(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();

    if (!configReady) {
      setStatus({ type: "error", message: "Fill and save Firebase Web Config first." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Sending OTP through Firebase Phone Auth..." });

    try {
      const { auth } = getFirebaseClient(firebaseConfig);
      const verifier = await ensureRecaptcha(auth);
      const result = await signInWithPhoneNumber(auth, phoneNumber.trim(), verifier);
      setConfirmationResult(result);
      setStatus({ type: "ok", message: "OTP sent. Enter OTP and click Verify OTP." });
      addLog("OTP Sent", { phoneNumber });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to send OTP.";
      setStatus({ type: "error", message });
      addLog("OTP Send Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function verifyOtp(): Promise<void> {
    if (!confirmationResult) {
      setStatus({ type: "error", message: "Send OTP first." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Verifying OTP and reading Firebase ID token..." });

    try {
      const credential = await confirmationResult.confirm(otp.trim());
      const idToken = await credential.user.getIdToken(true);
      setFirebaseIdToken(idToken);
      setStatus({ type: "ok", message: "Firebase OTP verified. Firebase ID token is ready." });
      addLog("Firebase OTP Verified", {
        uid: credential.user.uid,
        phoneNumber: credential.user.phoneNumber,
        firebaseIdTokenPreview: maskToken(idToken)
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to verify OTP.";
      setStatus({ type: "error", message });
      addLog("OTP Verify Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function exchangeWithCraves(): Promise<void> {
    if (!firebaseIdToken) {
      setStatus({ type: "error", message: "Verify Firebase OTP first." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Exchanging Firebase token with Craves Auth through APIM..." });

    try {
      const response = await fetch(`${baseUrl}/api/v1/auth/firebase/exchange`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ firebaseIdToken })
      });

      const result = await parseApiResponse(response);
      const accessToken = readAccessToken(result.body);
      const refreshToken = readRefreshToken(result.body);

      setExchangeResponse(result.body);
      setCravesAccessToken(accessToken);
      setCravesRefreshToken(refreshToken);

      if (!response.ok) {
        setStatus({ type: "error", message: `Craves token exchange failed with HTTP ${response.status}.` });
      } else if (!accessToken) {
        setStatus({ type: "error", message: "Exchange succeeded, but accessToken was not found in the response." });
      } else {
        setStatus({ type: "ok", message: "Craves access token received." });
      }

      addLog("Craves Token Exchange", {
        status: result.status,
        ok: result.ok,
        accessTokenPreview: maskToken(accessToken),
        response: result.body
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to exchange with Craves Auth Service.";
      setStatus({ type: "error", message });
      addLog("Craves Token Exchange Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function testMe(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Calling Auth /me through APIM..." });

    try {
      const result = await callCravesApi("/api/v1/auth/me", { method: "GET" });
      setMeResponse(result.body);
      setStatus(result.ok
        ? { type: "ok", message: "Auth /me returned current Craves identity." }
        : { type: "error", message: `Auth /me failed with HTTP ${result.status}.` }
      );
      addLog("Auth /me", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to call Auth /me.";
      setStatus({ type: "error", message });
      addLog("Auth /me Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function loadNotifications(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Loading Notification Inbox through APIM..." });

    try {
      const result = await callCravesApi("/api/v1/notifications/in-app", { method: "GET" });

      if (Array.isArray(result.body)) {
        const items = result.body as NotificationItem[];
        setNotifications(items);
        setSelectedNoticeId(items[0]?.id || "");
      }

      setStatus(result.ok
        ? { type: "ok", message: "Notification Inbox loaded successfully." }
        : { type: "error", message: `Notification Inbox failed with HTTP ${result.status}.` }
      );
      addLog("Notification Inbox", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to load Notification Inbox.";
      setStatus({ type: "error", message });
      addLog("Notification Inbox Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function markSelectedRead(): Promise<void> {
    if (!selectedNoticeId) {
      setStatus({ type: "error", message: "Select or paste a notification ID first." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Marking notification as read..." });

    try {
      const result = await callCravesApi(`/api/v1/notifications/in-app/${selectedNoticeId}/read`, { method: "PATCH" });
      setStatus(result.ok
        ? { type: "ok", message: `Notification marked as read. HTTP ${result.status} is success.` }
        : { type: "error", message: `Mark as read failed with HTTP ${result.status}.` }
      );
      addLog("Mark Notification as Read", result);

      if (result.ok) {
        await loadNotifications();
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to mark notification as read.";
      setStatus({ type: "error", message });
      addLog("Mark Notification Read Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function getCustomerProfile(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer GET /profile..." });

    try {
      const result = await callCravesApi("/api/v1/customer/profile", { method: "GET" });
      setCustomerProfileResponse(result.body);
      setStatus(result.ok
        ? { type: "ok", message: "Customer profile loaded." }
        : { type: "error", message: `Customer profile GET failed with HTTP ${result.status}.` }
      );
      addLog("Customer Profile GET", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to get customer profile.";
      setStatus({ type: "error", message });
      addLog("Customer Profile GET Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function saveCustomerProfile(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer PUT /profile..." });

    try {
      const payload = parseJsonPayload(profilePayload, "Customer profile payload");
      const result = await callCravesApi("/api/v1/customer/profile", {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      setCustomerProfileResponse(result.body);
      setStatus(result.ok
        ? { type: "ok", message: "Customer profile create/update completed." }
        : { type: "error", message: `Customer profile PUT failed with HTTP ${result.status}.` }
      );
      addLog("Customer Profile PUT", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to save customer profile.";
      setStatus({ type: "error", message });
      addLog("Customer Profile PUT Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function listCustomerAddresses(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer GET /addresses..." });

    try {
      const result = await callCravesApi("/api/v1/customer/addresses", { method: "GET" });
      const addresses = readArrayFromResponse(result.body);
      const firstAddressId = readFirstAddressId(result.body);
      setCustomerAddresses(addresses);
      setCustomerAddressesResponse(result.body);
      if (firstAddressId) {
        setAddressId(firstAddressId);
      }
      setStatus(result.ok
        ? { type: "ok", message: "Customer addresses loaded." }
        : { type: "error", message: `Customer addresses GET failed with HTTP ${result.status}.` }
      );
      addLog("Customer Addresses GET", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to list customer addresses.";
      setStatus({ type: "error", message });
      addLog("Customer Addresses GET Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function addCustomerAddress(): Promise<void> {
    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer POST /addresses..." });

    try {
      const payload = parseJsonPayload(addressPayload, "Customer address payload");
      const result = await callCravesApi("/api/v1/customer/addresses", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      const newAddressId = readFirstAddressId(result.body);
      if (newAddressId) {
        setAddressId(newAddressId);
      }
      setCustomerAddressesResponse(result.body);
      setStatus(result.ok
        ? { type: "ok", message: "Customer address added." }
        : { type: "error", message: `Customer address POST failed with HTTP ${result.status}.` }
      );
      addLog("Customer Address POST", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to add customer address.";
      setStatus({ type: "error", message });
      addLog("Customer Address POST Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function updateCustomerAddress(): Promise<void> {
    if (!addressId.trim()) {
      setStatus({ type: "error", message: "Address ID is required for update." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer PUT /addresses/{addressId}..." });

    try {
      const payload = parseJsonPayload(addressPayload, "Customer address payload");
      const result = await callCravesApi(`/api/v1/customer/addresses/${addressId.trim()}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      setCustomerAddressesResponse(result.body);
      setStatus(result.ok
        ? { type: "ok", message: "Customer address updated." }
        : { type: "error", message: `Customer address PUT failed with HTTP ${result.status}.` }
      );
      addLog("Customer Address PUT", result);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to update customer address.";
      setStatus({ type: "error", message });
      addLog("Customer Address PUT Failed", message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteCustomerAddress(): Promise<void> {
    if (!addressId.trim()) {
      setStatus({ type: "error", message: "Address ID is required for delete." });
      return;
    }

    setBusy(true);
    setStatus({ type: "idle", message: "Calling Customer DELETE /addresses/{addressId}..." });

    try {
      const result = await callCravesApi(`/api/v1/customer/addresses/${addressId.trim()}`, { method: "DELETE" });
      setStatus(result.ok
        ? { type: "ok", message: `Customer address deleted. HTTP ${result.status} is success.` }
        : { type: "error", message: `Customer address DELETE failed with HTTP ${result.status}.` }
      );
      addLog("Customer Address DELETE", result);
      if (result.ok) {
        setAddressId("");
        await listCustomerAddresses();
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to delete customer address.";
      setStatus({ type: "error", message });
      addLog("Customer Address DELETE Failed", message);
    } finally {
      setBusy(false);
    }
  }

  function copyValue(label: string, value: string): void {
    if (!value) {
      return;
    }

    void navigator.clipboard.writeText(value);
    setStatus({ type: "ok", message: `${label} copied to clipboard.` });
  }

  return (
    <main>
      <section className="header">
        <div className="kicker">Craves Developer Test</div>
        <h1>Craves API Test Dashboard</h1>
        <p className="lead">
          This page replaces manual curl testing. It verifies Firebase Phone OTP, Craves token exchange, APIM routing,
          Auth /me, Notification Inbox, Mark Notification as Read, and Customer Profile APIs from one browser page.
        </p>
        <div className="pill-row">
          <span className="pill gold">API Base: {baseUrl}</span>
          <span className="pill">Auth /me</span>
          <span className="pill">Notification Inbox</span>
          <span className="pill">Customer Profile</span>
          <span className="pill">Customer Addresses</span>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>0. Firebase Web Config</h2>
          <p className="small">
            This uses Azure Pipeline environment values by default. If anything is blank, paste the Firebase Web Config values here and save them in this browser.
          </p>

          <div className="field">
            <label htmlFor="apiKey">apiKey</label>
            <input id="apiKey" value={firebaseConfig.apiKey} onChange={(event) => updateConfigField("apiKey", event.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="authDomain">authDomain</label>
            <input id="authDomain" value={firebaseConfig.authDomain} onChange={(event) => updateConfigField("authDomain", event.target.value)} placeholder="your-project.firebaseapp.com" />
          </div>
          <div className="field">
            <label htmlFor="projectId">projectId</label>
            <input id="projectId" value={firebaseConfig.projectId} onChange={(event) => updateConfigField("projectId", event.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="appId">appId</label>
            <input id="appId" value={firebaseConfig.appId} onChange={(event) => updateConfigField("appId", event.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="storageBucket">storageBucket</label>
            <input id="storageBucket" value={firebaseConfig.storageBucket ?? ""} onChange={(event) => updateConfigField("storageBucket", event.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="messagingSenderId">messagingSenderId</label>
            <input id="messagingSenderId" value={firebaseConfig.messagingSenderId ?? ""} onChange={(event) => updateConfigField("messagingSenderId", event.target.value)} />
          </div>

          <div className="button-row">
            <button type="button" onClick={saveFirebaseConfig}>Save Firebase Config</button>
            <button type="button" className="secondary" onClick={clearFirebaseConfig}>Reset</button>
          </div>

          <div className={`status ${configReady ? "ok" : "error"}`}>
            {configReady ? "Firebase config is ready." : "Firebase config is incomplete. apiKey, authDomain, projectId, and appId are mandatory."}
          </div>
        </div>

        <div className="card">
          <h2>1. Firebase OTP Login</h2>
          <form onSubmit={sendOtp}>
            <div className="field">
              <label htmlFor="phoneNumber">Phone number in E.164 format</label>
              <input
                id="phoneNumber"
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                placeholder="+918019166645"
                autoComplete="tel"
              />
            </div>
            <div id="recaptcha-container" />
            <div className="button-row">
              <button type="submit" disabled={busy || !configReady}>Send OTP</button>
            </div>
          </form>

          <div className="field">
            <label htmlFor="otp">OTP</label>
            <input
              id="otp"
              value={otp}
              onChange={(event) => setOtp(event.target.value)}
              placeholder="123456"
              inputMode="numeric"
            />
          </div>

          <div className="button-row">
            <button type="button" onClick={verifyOtp} disabled={busy || !confirmationResult}>Verify OTP</button>
            <button type="button" className="secondary" onClick={exchangeWithCraves} disabled={busy || !firebaseIdToken}>Exchange with Craves</button>
          </div>

          <div className={`status ${status.type === "ok" ? "ok" : status.type === "error" ? "error" : ""}`}>
            {status.message}
          </div>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>2. Token Results</h2>
          <div className="field">
            <label>Firebase ID Token</label>
            <textarea readOnly value={firebaseIdToken} placeholder="Firebase ID token appears here after OTP verification." />
          </div>
          <div className="button-row">
            <button type="button" className="ghost" onClick={() => copyValue("Firebase ID token", firebaseIdToken)} disabled={!firebaseIdToken}>Copy Firebase ID Token</button>
          </div>

          <div className="field" style={{ marginTop: 16 }}>
            <label>Craves Access Token</label>
            <textarea readOnly value={cravesAccessToken} placeholder="Craves access token appears here after exchange." />
          </div>
          <div className="button-row">
            <button type="button" className="ghost" onClick={() => copyValue("Craves access token", cravesAccessToken)} disabled={!cravesAccessToken}>Copy Craves Access Token</button>
          </div>

          <div className="field" style={{ marginTop: 16 }}>
            <label>Craves Refresh Token</label>
            <textarea readOnly value={cravesRefreshToken} placeholder="Craves refresh token appears here after exchange." />
          </div>
        </div>

        <div className="card">
          <h2>3. Auth and Notification Checks</h2>
          <p className="small">
            These buttons use the Craves Access Token, not the Firebase ID Token.
          </p>
          <div className="button-row">
            <button type="button" onClick={testMe} disabled={busy || !cravesAccessToken}>Test Auth /me</button>
            <button type="button" onClick={loadNotifications} disabled={busy || !cravesAccessToken}>Load Notification Inbox</button>
          </div>

          <div className="pill-row">
            <span className="pill">Total: {notifications.length}</span>
            <span className="pill gold">Unread: {unreadCount}</span>
            <span className="pill">Read: {notifications.length - unreadCount}</span>
          </div>

          <div className="field" style={{ marginTop: 16 }}>
            <label htmlFor="noticeId">Selected notification ID</label>
            <input id="noticeId" value={selectedNoticeId} onChange={(event) => setSelectedNoticeId(event.target.value)} />
          </div>

          <div className="button-row">
            <button type="button" className="secondary" onClick={markSelectedRead} disabled={busy || !cravesAccessToken || !selectedNoticeId}>
              Mark Selected Notification as Read
            </button>
          </div>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>4. Customer Profile</h2>
          <p className="small">
            Use this to validate Customer Service through APIM. Payload is editable so we do not hardcode product/business rules into the dashboard.
          </p>
          <div className="field">
            <label htmlFor="profilePayload">PUT /api/v1/customer/profile payload</label>
            <textarea id="profilePayload" value={profilePayload} onChange={(event) => setProfilePayload(event.target.value)} />
          </div>
          <div className="button-row">
            <button type="button" onClick={getCustomerProfile} disabled={busy || !cravesAccessToken}>Get Profile</button>
            <button type="button" className="secondary" onClick={saveCustomerProfile} disabled={busy || !cravesAccessToken}>Create / Update Profile</button>
          </div>
          <pre>{customerProfileResponse ? formatJson(customerProfileResponse) : "No customer profile response yet."}</pre>
        </div>

        <div className="card">
          <h2>5. Customer Addresses</h2>
          <p className="small">
            Add, list, update, and delete customer addresses. If list/add response contains an address ID, the dashboard will auto-fill it.
          </p>
          <div className="field">
            <label htmlFor="addressPayload">Address payload</label>
            <textarea id="addressPayload" value={addressPayload} onChange={(event) => setAddressPayload(event.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="addressId">Address ID for update/delete</label>
            <input id="addressId" value={addressId} onChange={(event) => setAddressId(event.target.value)} placeholder="Address UUID returned by add/list" />
          </div>
          <div className="button-row">
            <button type="button" onClick={listCustomerAddresses} disabled={busy || !cravesAccessToken}>List Addresses</button>
            <button type="button" className="secondary" onClick={addCustomerAddress} disabled={busy || !cravesAccessToken}>Add Address</button>
            <button type="button" className="ghost" onClick={updateCustomerAddress} disabled={busy || !cravesAccessToken || !addressId}>Update Address</button>
            <button type="button" className="ghost" onClick={deleteCustomerAddress} disabled={busy || !cravesAccessToken || !addressId}>Delete Address</button>
          </div>
          <div className="pill-row">
            <span className="pill gold">Loaded addresses: {customerAddresses.length}</span>
          </div>
          <pre>{customerAddressesResponse ? formatJson(customerAddressesResponse) : "No customer address response yet."}</pre>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>6. Notifications</h2>
          {notifications.length === 0 ? (
            <p className="small">No notifications loaded yet. Click “Load Notification Inbox”.</p>
          ) : (
            notifications.map((notice) => (
              <button
                key={notice.id}
                type="button"
                className={selectedClass(selectedNoticeId === notice.id)}
                onClick={() => setSelectedNoticeId(notice.id)}
              >
                <div className="notification-title">
                  <strong>{notice.title}</strong>
                  <span className={notice.readAt ? "badge read" : "badge"}>{notice.readAt ? "Read" : "Unread"}</span>
                </div>
                <div className="small">{notice.body}</div>
                <div className="small" style={{ marginTop: 8 }}>{notice.noticeType} • {notice.targetType}</div>
                <div className="small">{notice.id}</div>
              </button>
            ))
          )}
        </div>

        <div className="card">
          <h2>7. Auth Response Panels</h2>
          <h3 className="small">Craves exchange response</h3>
          <pre>{exchangeResponse ? formatJson(exchangeResponse) : "No exchange response yet."}</pre>
          <h3 className="small" style={{ marginTop: 16 }}>Auth /me response</h3>
          <pre>{meResponse ? formatJson(meResponse) : "No /me response yet."}</pre>
        </div>
      </section>

      <section className="card full" style={{ marginTop: 20 }}>
        <h2>8. Test Log</h2>
        {logs.length === 0 ? (
          <p className="small">No test has been run yet.</p>
        ) : (
          logs.map((log, index) => (
            <div key={`${log.title}-${index}`} style={{ marginBottom: 14 }}>
              <strong style={{ color: "var(--gold)" }}>{log.title}</strong>
              <pre>{formatJson(log.value)}</pre>
            </div>
          ))
        )}
      </section>
    </main>
  );
}
