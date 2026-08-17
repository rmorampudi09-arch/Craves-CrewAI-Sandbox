import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

const layout = source("../app/layout.tsx");
const overrides = source("../control-border-overrides.css");

test("the neutral control override loads after all earlier theme files", () => {
  const themeImport = layout.indexOf('import "../craves-theme.css";');
  const otpImport = layout.indexOf('import "../otp-overrides.css";');
  const finalImport = layout.indexOf(
    'import "../control-border-overrides.css";',
  );

  assert.ok(themeImport >= 0);
  assert.ok(otpImport > themeImport);
  assert.ok(finalImport > otpImport);
});

test("customer and chef button-like controls have transparent borders", () => {
  assert.match(overrides, /\[role="button"\]/);
  assert.match(overrides, /\[role="tab"\]/);
  assert.match(overrides, /a\[class\*="border-primary"\]/);
  assert.match(overrides, /body \.chef-panel-theme :is\(button, a/);
  assert.match(overrides, /border-color:\s*transparent\s*!important/);
  assert.doesNotMatch(
    overrides,
    /border(?:-color)?:\s*(?:#f62e18|#c92716|#6930ca|var\(--color-(?:flame|contrast)-red\))/i,
  );
});

test("mouse and keyboard focus rings cannot restore a logo colour", () => {
  assert.match(
    overrides,
    /\[tabindex\]\):focus\s*\{[\s\S]*--tw-ring-color:\s*transparent\s*!important/,
  );
  assert.match(
    overrides,
    /outline:\s*2px solid var\(--color-grey-400\)\s*!important/,
  );
  assert.doesNotMatch(overrides, /outline:[^;]*(?:#f62e18|#c92716|#6930ca)/i);
});

test("OTP edges use neutral grey only", () => {
  assert.match(
    overrides,
    /input\.craves-otp-field[\s\S]*border-color:\s*var\(--color-grey-200\)\s*!important/,
  );
  assert.match(
    overrides,
    /\.craves-otp-slot\[data-active="true"\][\s\S]*border-color:\s*var\(--color-grey-400\)\s*!important/,
  );
});
