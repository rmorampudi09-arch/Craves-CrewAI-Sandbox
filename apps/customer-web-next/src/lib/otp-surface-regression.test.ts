import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("customer and chef OTP flows hide visible captcha and enforce one border", () => {
  const modal = source("../components/auth/AuthModal.tsx");
  const standalone = source("../components/phone-auth-form.tsx");
  const slots = source("../components/ui/forms/input-otp.tsx");
  const overrides = source("../otp-overrides.css");
  const layout = source("../app/layout.tsx");

  for (const contents of [modal, standalone]) {
    assert.match(contents, /RESEND_DELAY_SECONDS = 30/);
    assert.match(contents, /type RecaptchaMode = "visible" \| "invisible"/);
    assert.match(contents, /craves-recaptcha-resend/);
    assert.match(contents, /size: visible \? "normal" : "invisible"/);
    assert.match(contents, /craves-otp-field/);
  }

  assert.match(modal, /Resend verification code/);
  assert.doesNotMatch(modal, /Resend OTP/);
  assert.match(standalone, /Resend OTP/);

  assert.match(modal, /!otpSent && \(/);
  assert.match(standalone, /!otpStage && \(/);
  assert.match(modal, /clearVerifier\(\);\s*setOtp\(""\);\s*setOtpSent\(true\)/s);
  assert.match(standalone, /clearVerifier\(\);\s*setOtp\(""\);\s*setStage\("otp"\)/s);

  assert.match(layout, /import "\.\.\/otp-overrides\.css"/);
  assert.match(overrides, /input\.craves-otp-field:focus-visible/);
  assert.match(overrides, /border: 1px solid #f62e18 !important/);
  assert.match(overrides, /outline: 0 !important/);
  assert.match(overrides, /box-shadow: none !important/);
  assert.match(overrides, /--tw-ring-shadow: 0 0 #0000 !important/);

  assert.match(slots, /className=\{cn\([\s\S]*craves-otp-slot/);
  assert.match(slots, /data-active=\{isActive \? "true" : "false"\}/);
  assert.doesNotMatch(slots, /shadow-sm|ring-1 ring-ring/);
});

test("signed-in discovery uses a pure white page surface behind product cards", () => {
  const browse = source("../screens/public/BrowseFoods/BrowseFoods.tsx");

  assert.match(browse, /min-h-screen bg-white pb-24 text-ink/);
  assert.match(
    browse,
    /flex min-h-screen items-center justify-center bg-white/,
  );
  assert.doesNotMatch(browse, /min-h-screen bg-cream pb-24/);
});
