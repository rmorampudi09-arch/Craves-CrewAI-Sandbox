# Customer and Chef Web Precise UI and Checkout Address Changes

Date: 2026-08-06  
Scope: `apps/customer-web-next` only

## Requested outcome

1. Replace the landing-page food picture with solid `#F62E18`.
2. Remove Espresso Brown `#261A15` from customer and chef web surfaces and use white instead.
3. Use solid `#C92716` for buttons and tabs with black text. On hover or keyboard focus, use `#F62E18`, white text and bold lettering.
4. Make the signed-in welcome banner solid `#C92716`, remove the decorative gradient/glow and keep its copy white.
5. Preserve the previous meal-plan cards, wording and navigation flow.
6. Show only the current delivery address on checkout. Open an in-page dialog to load all saved addresses, select another address or add a new mapped address.
7. Remove the double-line focus appearance from chef accept/reject input fields.

## Files changed

- `apps/customer-web-next/src/craves-theme.css`
  - Removes the Espresso Brown token.
  - Establishes white and black as the shared base surface/text colors.
  - Applies the requested button and tab rest/hover states.
  - Keeps chef form controls at one visible 1px border.

- `apps/customer-web-next/src/components/sections/HeroSection.tsx`
  - Removes the landing artwork import and image element.
  - Uses a solid `#F62E18` hero and header.

- `apps/customer-web-next/src/components/home/WelcomeBanner.tsx`
  - Uses solid `#C92716` with white copy and no gradient/glow layer.

- `apps/customer-web-next/src/components/home/BrowseHeader.tsx`
  - Applies the requested colors to customer service tabs, profile action and header controls.
  - Removes the remaining brown-tinted header shadow.

- `apps/customer-web-next/src/components/sections/FooterSection.tsx`
  - Replaces the former dark-brown footer with a white surface.

- `apps/customer-web-next/src/components/cart/CartCheckoutBar.tsx`
- `apps/customer-web-next/src/components/order/DishBottomBar.tsx`
  - Replace brown-tinted shadows with neutral black transparency.

- `apps/customer-web-next/src/components/subscription-plan-browser.tsx`
- `apps/customer-web-next/src/app/subscriptions/plans/page.tsx`
  - Preserve the previous meal-plan card structure, dark page presentation, plan data and enrollment link.
  - Only the plan action adopts the new button interaction.

- `apps/customer-web-next/src/screens/Checkout/Checkout.tsx`
  - Shows only the selected delivery address.
  - Opens the address manager without leaving checkout.
  - Continues submitting only the selected saved-address ID to Order Service.

- `apps/customer-web-next/src/components/checkout/CheckoutAddressDialog.tsx`
  - Loads `/api/customer/addresses` on open.
  - Lists saved addresses and disables incomplete historical addresses for checkout selection.
  - Creates a new address through `POST /api/customer/addresses`.
  - Supports browser geolocation for latitude and longitude.
  - Selects a newly created delivery-ready address for the current checkout.

- `apps/customer-web-next/src/components/chef-page-header.tsx`
  - Replaces chef dark-brown page headings with white surfaces.

- `apps/customer-web-next/src/components/chef-order-actions.tsx`
  - Gives preparation time, acceptance note and rejection reason one border at rest and one border on focus.

- `apps/customer-web-next/src/lib/chef-brand-theme.test.ts`
- `apps/customer-web-next/src/lib/precise-customer-chef-ui.test.ts`
  - Lock the palette, hero, welcome banner, meal-plan preservation, checkout dialog and single-border chef controls.

## Business and backend behavior

No pricing, commission, delivery-radius, Cashfree, Firebase, APIM, database or backend-service behavior was changed. Checkout still sends `deliveryAddressId` to the existing Order Service contract. Address creation uses the already-deployed Customer Address API.

## Manual deployment required

Run the existing Azure DevOps customer-web pipeline after the pull request is merged:

- YAML: `/azure-pipelines-customer-web-next-delivery-tracking.yml`
- Branch: `main`
- `confirmReplaceCurrentCustomerWeb=true`
- `cashfreeMode=sandbox`
- Pipeline variable: `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`

The address APIM pipeline must not be run again for this UI change.

## Runtime verification

1. Open the landing page in a private browser window and confirm the hero has no food photograph and is solid `#F62E18`.
2. Verify normal buttons/tabs are `#C92716` with black text and hover to `#F62E18` with white bold text.
3. Sign in and confirm the welcome banner is solid `#C92716` with white copy.
4. Open Meal Plans and confirm the previous cards and enrollment flow remain.
5. Open checkout and confirm only one current address is displayed.
6. Open Manage address, select a saved address, then create one temporary mapped address and verify it becomes the checkout address.
7. Open a chef order awaiting acceptance and confirm each action input has only one visible border at rest and focus.
