# Craves Customer Mobile Delivery Tracking Handover

## 1. Status

Application code complete on `feature/customer-mobile-delivery-tracking`. No CI, native build, Azure operation or provider activation has occurred.

## 2. Parent dependency

This branch is stacked on the React Native auth foundation and transitively depends on PRs #25–#31.

## 3. Public contract

```text
GET /api/v1/orders/{orderId}/delivery-status
```

## 4. Authentication

The app uses the short-lived Craves session stored in the device secure keychain.

## 5. Ownership

Order Service remains authoritative for customer role and order ownership.

## 6. UUID validation

Invalid order IDs are rejected before a network request.

## 7. Timeout

Delivery reads are cancelled after ten seconds.

## 8. Expired session

HTTP 401 clears the mobile session and returns the app to phone sign-in.

## 9. Ownership privacy

HTTP 403/404 share the same customer-safe missing-order message.

## 10. Upstream errors

Raw APIM/Order Service response bodies are not displayed.

## 11. Response parser

The parser accepts only the documented delivery projection fields.

## 12. Status allow-list

Fourteen provider-neutral delivery states are supported.

## 13. Unknown statuses

Unknown values fail the response rather than being displayed as trusted status.

## 14. Tracking URL

Only HTTPS URLs survive parsing.

## 15. Link opening

React Native verifies that the device can open the URL before launch.

## 16. Provider ID

A short provider identifier may be retained in the typed response but is not required for UI decisions.

## 17. Provider delivery ID

Provider transaction IDs are excluded.

## 18. Raw webhook payload

Raw callbacks are excluded.

## 19. Internal worker data

Inbox, outbox, retries, dead letters and lease data are excluded.

## 20. Pickup privacy

Chef-private pickup location and contact fields are not part of this contract.

## 21. Current status

The screen shows provider-neutral status and last observed time.

## 22. Progress

A presentation-only progress percentage maps normalised statuses; it does not change backend state or promise an ETA.

## 23. Timeline

Up to one hundred sanitized chronological history entries may be rendered.

## 24. Poll interval

Thirty seconds.

## 25. Foreground condition

Automatic polling runs only while React Native AppState is `active`.

## 26. Background condition

Background and inactive application states stop polling.

## 27. Terminal states

Automatic polling stops for delivered, cancelled, returned and failed.

## 28. Manual refresh

Pull-to-refresh remains available.

## 29. Request ordering

A monotonically increasing request identifier prevents an older response from overwriting a newer request result.

## 30. Screen cleanup

Unmount increments the request identifier and intervals/listeners are removed.

## 31. Entry point

The signed-in Home screen opens Tracking Lookup.

## 32. Temporary lookup

The customer currently enters a chef-specific order UUID manually.

## 33. Future order integration

A native order-list module should pass the UUID directly and remove manual entry from the primary UX.

## 34. Navigation

`TrackingLookup` and `DeliveryTracking` are authenticated native stack routes.

## 35. Accessibility

Status text uses a live region and buttons/text inputs use native accessibility defaults.

## 36. Formatting

Dates use `en-IN` locale.

## 37. Map limitation

No map, courier coordinate or route geometry is shown because the current approved contract does not contain those fields.

## 38. ETA limitation

No ETA is invented.

## 39. SLA limitation

No delivery SLA or compensation rule is invented.

## 40. Cancellation limitation

No cancellation action is offered.

## 41. Refund limitation

No refund decision is inferred from delivery state.

## 42. Provider limitation

No Borzo create, track or callback operation is called directly by the mobile app.

## 43. Azure impact

None.

## 44. Database impact

None.

## 45. Service Bus impact

None.

## 46. CI

`azure-pipelines-customer-mobile-delivery-tracking-ci.yml` runs typecheck, tests and privacy/polling/HTTPS checks.

## 47. Native build dependency

Android/iOS compilation remains dependent on the manually reviewed native-shell amendment.

## 48. Acceptance test

Use one owned order with delivery history, one pre-delivery order, one unowned UUID and one expired session.

## 49. Merge condition

Merge only after parent PRs and both mobile CI pipelines succeed at exact heads.

## 50. Rollout condition

Do not release the app until backend consumer, status publisher, APIM route and authenticated web/API smoke tests have already passed.
