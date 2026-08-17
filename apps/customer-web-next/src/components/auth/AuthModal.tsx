"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { ChefHat, UserRound, X } from "lucide-react";
import {
  type ConfirmationResult,
  RecaptchaVerifier,
  signInWithPhoneNumber,
} from "firebase/auth";
import { getFirebaseBrowserClient } from "@/lib/firebase-client";
import {
  loadSession,
  setSessionIdentity,
  setSessionProfile,
  type CravesUser,
} from "@/services/auth/cravesAuth";
import type { CravesIdentity } from "@/lib/auth-contract";
import { parseCustomerProfile } from "@/lib/profile-contract";
import { CravesLogo } from "@/components/brand/CravesLogo";

type Mode = "login" | "register";
type RecaptchaMode = "visible" | "invisible";
export type AccountMode = "customer" | "chef";

interface AuthModalProps {
  open: boolean;
  mode: Mode;
  initialAccountMode?: AccountMode;
  lockAccountMode?: boolean;
  onClose: () => void;
  onSwitchMode: (mode: Mode) => void;
  onAuthenticated?: (user: CravesUser, accountMode: AccountMode) => void;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const RESEND_DELAY_SECONDS = 30;

export function AuthModal({
  open,
  mode,
  initialAccountMode = "customer",
  lockAccountMode = false,
  onClose,
  onSwitchMode,
  onAuthenticated,
}: AuthModalProps) {
  const fieldPrefix = useId();
  const [phone, setPhone] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [accountMode, setAccountMode] =
    useState<AccountMode>(initialAccountMode);
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [resendIn, setResendIn] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const confirmation = useRef<ConfirmationResult | null>(null);
  const verifier = useRef<RecaptchaVerifier | null>(null);

  const clearVerifier = useCallback(() => {
    verifier.current?.clear();
    verifier.current = null;
  }, []);

  const reset = useCallback(() => {
    clearVerifier();
    confirmation.current = null;
    setPhone("");
    setFirstName("");
    setLastName("");
    setEmail("");
    setOtp("");
    setOtpSent(false);
    setBusy(false);
    setResendIn(0);
    setError(null);
    setInfo(null);
  }, [clearVerifier]);

  const handleClose = useCallback(() => {
    reset();
    onClose();
  }, [onClose, reset]);

  useEffect(() => () => clearVerifier(), [clearVerifier]);

  useEffect(() => {
    if (open) setAccountMode(initialAccountMode);
  }, [initialAccountMode, open]);

  useEffect(() => {
    if (!otpSent || resendIn <= 0) return;
    const timer = window.setTimeout(
      () => setResendIn((current) => Math.max(0, current - 1)),
      1_000,
    );
    return () => window.clearTimeout(timer);
  }, [otpSent, resendIn]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) handleClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, handleClose, open]);

  if (!open) return null;

  const isChef = accountMode === "chef";
  const roleName = isChef ? "Home Chef" : "Customer";
  const modalEyebrow =
    mode === "login"
      ? isChef
        ? "Welcome back, Chef"
        : "Welcome back"
      : isChef
        ? "Your cooking journey starts here"
        : "Good food starts here";
  const modalTitle =
    mode === "login"
      ? isChef
        ? "Home Chef sign in"
        : "Customer sign in"
      : isChef
        ? "Join Craves as a Home Chef"
        : "Create your customer account";
  const motivationalCopy = isChef
    ? mode === "login"
      ? "Welcome back. Sign in to continue your Craves journey and stay connected to the people who value real homemade food."
      : "Turn your passion for cooking into opportunity. Join Craves and bring the food you love making to more tables."
    : mode === "login"
      ? "Your next homemade favourite is waiting. Sign in to discover trusted home chefs and enjoy food that feels like home."
      : "Join Craves to discover fresh homemade food from trusted home chefs near you, made with care and delivered to your door.";

  async function recaptcha(mode: RecaptchaMode): Promise<RecaptchaVerifier> {
    clearVerifier();
    const { auth } = getFirebaseBrowserClient();
    const visible = mode === "visible";
    const instance = new RecaptchaVerifier(
      auth,
      visible ? "craves-recaptcha" : "craves-recaptcha-resend",
      {
        size: visible ? "normal" : "invisible",
        callback: () => {
          if (visible)
            setInfo("You’re all set. Request your verification code when you’re ready.");
        },
        "expired-callback": () => {
          if (visible)
            setInfo("Your verification check expired. Please complete it again.");
        },
      },
    );
    await instance.render();
    verifier.current = instance;
    return instance;
  }

  function validateRegistration(): string | null {
    if (firstName.trim().length < 2)
      return "Enter your first name using at least two characters.";
    if (lastName.trim().length < 1) return "Enter your last name.";
    if (email.trim() && !EMAIL_PATTERN.test(email.trim()))
      return "Enter a valid email address or leave it blank.";
    return null;
  }

  async function sendOtp(isResend: boolean) {
    setError(null);
    setInfo(null);
    if (!/^\d{10}$/.test(phone)) {
      setError("Enter a valid 10-digit mobile number.");
      return;
    }
    if (mode === "register") {
      const validationError = validateRegistration();
      if (validationError) {
        setError(validationError);
        return;
      }
    }

    setBusy(true);
    try {
      const { auth } = getFirebaseBrowserClient();
      confirmation.current = await signInWithPhoneNumber(
        auth,
        `+91${phone}`,
        await recaptcha(isResend ? "invisible" : "visible"),
      );
      clearVerifier();
      setOtp("");
      setOtpSent(true);
      setResendIn(RESEND_DELAY_SECONDS);
      setInfo(
        isResend
          ? `A new verification code is on its way. Enter the latest six-digit code to continue as a ${roleName}.`
          : isChef
            ? "Your verification code is on its way. One quick step, then you can continue your Home Chef journey with Craves."
            : "Your verification code is on its way. One quick step, then you can get back to discovering homemade food you’ll love.",
      );
    } catch (caught) {
      clearVerifier();
      const code =
        caught && typeof caught === "object" && "code" in caught
          ? String(caught.code)
          : "";
      setError(
        code.includes("too-many-requests")
          ? "Too many verification attempts. Please try again later."
          : "We couldn’t send the verification code. Complete the security check and try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  const handleGenerateOtp = async (event: React.FormEvent) => {
    event.preventDefault();
    await sendOtp(false);
  };

  const handleResendOtp = async () => {
    if (busy || resendIn > 0) return;
    await sendOtp(true);
  };

  const handleVerify = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!confirmation.current || !/^\d{6}$/.test(otp))
      return setError("Enter the six-digit verification code.");
    setBusy(true);
    try {
      const credential = await confirmation.current.confirm(otp);
      const firebaseIdToken = await credential.user.getIdToken(true);
      const response = await fetch("/api/auth/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({ firebaseIdToken }),
      });
      const body = (await response.json().catch(() => null)) as {
        identity?: CravesIdentity;
        message?: string;
      } | null;
      if (!response.ok || !body?.identity)
        throw new Error(body?.message ?? "Sign-in failed.");

      let user = setSessionIdentity(body.identity);

      if (mode === "register") {
        const profileResponse = await fetch("/api/customer/profile", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({
            firstName: firstName.trim(),
            lastName: lastName.trim(),
            email: email.trim() || null,
          }),
        });
        const rawProfile = await profileResponse.json().catch(() => null);
        if (!profileResponse.ok) {
          const message =
            rawProfile &&
            typeof rawProfile === "object" &&
            "message" in rawProfile &&
            typeof rawProfile.message === "string"
              ? rawProfile.message
              : "Your verified phone was saved, but the profile could not be completed. Please try again.";
          throw new Error(message);
        }
        const profile = parseCustomerProfile(rawProfile);
        if (!profile)
          throw new Error("Craves returned an invalid profile response.");
        user = setSessionProfile(profile) ?? user;
      } else {
        user = (await loadSession()) ?? user;
      }

      onAuthenticated?.(user, accountMode);
      reset();
      onClose();
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The verification code could not be confirmed.",
      );
    } finally {
      setBusy(false);
    }
  };

  const switchTo = (next: Mode) => {
    reset();
    onSwitchMode(next);
  };

  const switchAccountMode = (next: AccountMode) => {
    if (next === accountMode || otpSent || busy) return;
    clearVerifier();
    confirmation.current = null;
    setAccountMode(next);
    setOtp("");
    setError(null);
    setInfo(null);
  };

  const useAnotherNumber = () => {
    clearVerifier();
    confirmation.current = null;
    setOtp("");
    setOtpSent(false);
    setResendIn(0);
    setError(null);
    setInfo("Enter your number and request a new verification code.");
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-espresso/70 px-0 md:items-center md:px-4"
      onClick={handleClose}
      role="presentation"
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${fieldPrefix}-title`}
        className="relative max-h-[95vh] w-full max-w-lg overflow-y-auto rounded-t-2xl border border-border bg-white shadow-[var(--shadow-pop)] md:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-border px-6 py-4">
          <div className="flex items-center gap-3">
            <CravesLogo size="sm" />
            <div>
              <p className="craves-overline">{modalEyebrow}</p>
              <h2
                id={`${fieldPrefix}-title`}
                className="font-display text-xl font-semibold text-ink"
              >
                {modalTitle}
              </h2>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="flex h-11 w-11 items-center justify-center rounded-full text-ink transition-colors hover:bg-secondary"
            aria-label="Close sign-in dialog"
            disabled={busy}
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </header>

        <div className="px-6 py-6">
          <p className="text-sm leading-6 text-muted-foreground">
            {motivationalCopy}
          </p>

          {lockAccountMode ? (
            <div className="mt-6 flex items-center gap-3 rounded-xl border border-primary/15 bg-secondary/70 px-4 py-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-primary shadow-sm">
                {isChef ? (
                  <ChefHat className="h-5 w-5" aria-hidden="true" />
                ) : (
                  <UserRound className="h-5 w-5" aria-hidden="true" />
                )}
              </span>
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.14em] text-muted-foreground">
                  You’re continuing as
                </p>
                <p className="font-display text-lg font-semibold text-ink">{roleName}</p>
              </div>
            </div>
          ) : (
            <fieldset className="mt-6" disabled={otpSent || busy}>
              <legend className="craves-overline text-ink">Choose your Craves role</legend>
              <p className="mt-1 text-xs leading-5 text-muted-foreground">
                Pick the experience you want now. You can switch before requesting your verification code.
              </p>
              <div className="mt-3 grid grid-cols-2 gap-3">
                <button
                  type="button"
                  aria-pressed={accountMode === "customer"}
                  onClick={() => switchAccountMode("customer")}
                  className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl border px-3 py-3 text-center transition-all ${
                    accountMode === "customer"
                      ? "border-primary bg-secondary text-ink shadow-sm"
                      : "border-border bg-white/80 text-muted-foreground hover:border-primary hover:bg-white"
                  }`}
                >
                  <span className="flex items-center gap-2 text-sm font-bold">
                    <UserRound className="h-5 w-5" aria-hidden="true" /> Customer
                  </span>
                  <span className="text-[11px] font-medium">Order homemade food</span>
                </button>
                <button
                  type="button"
                  aria-pressed={accountMode === "chef"}
                  onClick={() => switchAccountMode("chef")}
                  className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl border px-3 py-3 text-center transition-all ${
                    accountMode === "chef"
                      ? "border-primary bg-secondary text-ink shadow-sm"
                      : "border-border bg-white/80 text-muted-foreground hover:border-primary hover:bg-white"
                  }`}
                >
                  <span className="flex items-center gap-2 text-sm font-bold">
                    <ChefHat className="h-5 w-5" aria-hidden="true" /> Home Chef
                  </span>
                  <span className="text-[11px] font-medium">Cook and grow with Craves</span>
                </button>
              </div>
            </fieldset>
          )}

          {isChef && (
            <p className="mt-3 rounded-lg bg-secondary/70 p-3 text-xs leading-5 text-muted-foreground">
              Cook from home, grow with Craves. New chefs complete a short application after verification, and chef tools open after approval.
            </p>
          )}

          <form
            onSubmit={otpSent ? handleVerify : handleGenerateOtp}
            className="mt-6 space-y-4"
          >
            {mode === "register" && !otpSent && (
              <div className="grid gap-4 sm:grid-cols-2">
                <label
                  htmlFor={`${fieldPrefix}-first-name`}
                  className="text-sm font-semibold text-ink"
                >
                  First name <span className="text-destructive">*</span>
                  <input
                    id={`${fieldPrefix}-first-name`}
                    type="text"
                    autoComplete="given-name"
                    maxLength={100}
                    value={firstName}
                    onChange={(event) => setFirstName(event.target.value)}
                    className="mt-2 min-h-12 w-full rounded-lg border border-border bg-white px-3 text-base text-ink placeholder:text-grey-400 focus:border-primary"
                    disabled={busy}
                    required
                  />
                </label>
                <label
                  htmlFor={`${fieldPrefix}-last-name`}
                  className="text-sm font-semibold text-ink"
                >
                  Last name <span className="text-destructive">*</span>
                  <input
                    id={`${fieldPrefix}-last-name`}
                    type="text"
                    autoComplete="family-name"
                    maxLength={100}
                    value={lastName}
                    onChange={(event) => setLastName(event.target.value)}
                    className="mt-2 min-h-12 w-full rounded-lg border border-border bg-white px-3 text-base text-ink placeholder:text-grey-400 focus:border-primary"
                    disabled={busy}
                    required
                  />
                </label>
              </div>
            )}

            {mode === "register" && !otpSent && (
              <label
                htmlFor={`${fieldPrefix}-email`}
                className="block text-sm font-semibold text-ink"
              >
                Email <span className="font-normal text-muted-foreground">(optional)</span>
                <input
                  id={`${fieldPrefix}-email`}
                  type="email"
                  autoComplete="email"
                  maxLength={320}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@example.com"
                  className="mt-2 min-h-12 w-full rounded-lg border border-border bg-white px-3 text-base text-ink placeholder:text-grey-400 focus:border-primary"
                  disabled={busy}
                />
              </label>
            )}

            <label
              htmlFor={`${fieldPrefix}-phone`}
              className="block text-sm font-semibold text-ink"
            >
              Mobile number <span className="text-destructive">*</span>
              <span className="mt-2 flex min-h-12 overflow-hidden rounded-lg border border-border bg-white focus-within:border-primary">
                <span className="flex items-center border-r border-border px-3 text-sm font-semibold text-ink">
                  +91
                </span>
                <input
                  id={`${fieldPrefix}-phone`}
                  type="tel"
                  inputMode="numeric"
                  maxLength={10}
                  placeholder="10-digit mobile number"
                  value={phone}
                  onChange={(event) =>
                    setPhone(event.target.value.replace(/\D/g, ""))
                  }
                  className="w-full bg-white px-3 text-base text-ink outline-none placeholder:text-grey-400"
                  disabled={otpSent || busy}
                  autoComplete="tel-national"
                  required
                />
              </span>
            </label>

            {!otpSent && (
              <div
                id="craves-recaptcha"
                className="min-h-20 overflow-hidden rounded-lg border border-border bg-white p-2"
              />
            )}
            <div
              id="craves-recaptcha-resend"
              className="hidden"
              aria-hidden="true"
            />

            {otpSent && (
              <label
                htmlFor={`${fieldPrefix}-otp`}
                className="block text-sm font-semibold text-ink"
              >
                Six-digit verification code
                <input
                  id={`${fieldPrefix}-otp`}
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="000000"
                  value={otp}
                  onChange={(event) =>
                    setOtp(event.target.value.replace(/\D/g, ""))
                  }
                  className="craves-otp-field mt-2 min-h-12 w-full rounded-lg bg-white px-3 text-center text-lg tracking-widest text-ink placeholder:text-grey-400"
                  autoComplete="one-time-code"
                  autoFocus
                  required
                />
              </label>
            )}

            {error && (
              <p
                role="alert"
                className="rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm font-medium text-destructive"
              >
                {error}
              </p>
            )}
            {info && !error && (
              <p
                role="status"
                className="rounded-lg border border-info/20 bg-info/5 px-3 py-2 text-sm font-medium text-info"
              >
                {info}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="btn-primary w-full"
            >
              {busy
                ? "Please wait…"
                : otpSent
                  ? mode === "login"
                    ? `Sign in as ${roleName}`
                    : `Verify and join as ${roleName}`
                  : "Send verification code"}
            </button>

            {otpSent && (
              <div className="grid gap-2 sm:grid-cols-2">
                <button
                  type="button"
                  disabled={busy || resendIn > 0}
                  onClick={() => void handleResendOtp()}
                  className="min-h-11 w-full rounded-lg border border-[#F62E18] bg-white px-4 text-sm font-semibold text-black"
                >
                  {resendIn > 0
                    ? `Resend code in ${resendIn}s`
                    : "Resend verification code"}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={useAnotherNumber}
                  className="min-h-11 w-full rounded-lg border border-[#F62E18] bg-white px-4 text-sm font-semibold text-black"
                >
                  Use another number
                </button>
              </div>
            )}
          </form>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            {mode === "login" ? (
              <>
                {isChef ? "New to Craves as a Home Chef? " : "New to Craves? "}
                <button
                  type="button"
                  onClick={() => switchTo("register")}
                  className="font-semibold text-contrast-red underline-offset-4 hover:underline"
                >
                  {isChef ? "Start your chef journey" : "Create a customer account"}
                </button>
              </>
            ) : (
              <>
                {isChef ? "Already registered as a Home Chef? " : "Already have a customer account? "}
                <button
                  type="button"
                  onClick={() => switchTo("login")}
                  className="font-semibold text-contrast-red underline-offset-4 hover:underline"
                >
                  Sign in
                </button>
              </>
            )}
          </p>
        </div>
      </section>
    </div>
  );
}