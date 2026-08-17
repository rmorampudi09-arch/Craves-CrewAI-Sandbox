import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const layout = readFileSync(
  new URL("../app/chef/layout.tsx", import.meta.url),
  "utf8",
);
const navigation = readFileSync(
  new URL("../components/chef-workspace-navigation.tsx", import.meta.url),
  "utf8",
);
const styles = readFileSync(new URL("../styles.css", import.meta.url), "utf8");

test("every chef route inherits the responsive Craves workspace shell", () => {
  assert.match(layout, /className="chef-panel-theme"/);
  assert.match(layout, /ChefWorkspaceNavigation/);
  assert.match(layout, /Customer mode/);
  for (const route of [
    "/chef/application",
    "/chef/kitchen",
    "/chef/menu",
    "/chef/orders",
    "/chef/earnings",
    "/chef/operations",
  ]) {
    assert.match(navigation, new RegExp(route.replaceAll("/", "\\/")));
  }
  assert.match(navigation, /aria-current=\{active \? "page" : undefined\}/);
});

test("chef theme still maps legacy form classes to canonical customer tokens", () => {
  assert.match(styles, /\.chef-panel-theme \[class\*="bg-\[#FFF8EC\]"\]/);
  assert.match(styles, /\.chef-panel-theme \[class\*="text-\[#6930CA\]"\]/);
  assert.match(styles, /background: var\(--gradient-primary\) !important/);
  assert.match(styles, /color: var\(--primary\) !important/);
  assert.match(styles, /box-shadow: var\(--shadow-card\)/);
});

test("chef forms always use readable customer-side control colours", () => {
  assert.match(styles, /\.chef-panel-theme :is\(input, textarea, select\)/);
  assert.match(styles, /color: var\(--ink\) !important/);
  assert.match(styles, /background: var\(--card\) !important/);
  assert.match(styles, /:is\(input, textarea, select\):disabled/);
  assert.doesNotMatch(
    styles,
    /\.chef-panel-theme \.text-white\s*\{/,
    "Do not globally recolour white action text; primary buttons must remain readable.",
  );
});
