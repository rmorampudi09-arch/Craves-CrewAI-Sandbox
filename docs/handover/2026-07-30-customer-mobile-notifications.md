# Craves Mobile Customer Notifications Handover

Date: 2026-07-30
Status: Code complete; CI, native build and device testing pending.

## 1. Purpose
Show customer-owned in-app notifications.
## 2. Platform
React Native and TypeScript.
## 3. Session
Keychain/Keystore-backed access token.
## 4. Authentication
Bearer token on list and mark-read.
## 5. Authorization
Notification Service resolves identity from JWT.
## 6. List route
GET /api/v1/notifications/in-app.
## 7. Limit
One through one hundred, default fifty.
## 8. Mark-read route
PATCH /api/v1/notifications/in-app/{noticeId}/read.
## 9. Identifier
Notice id must be UUID.
## 10. Timeout
Shared mobile timeout applies.
## 11. Session expiry
HTTP 401 clears local session.
## 12. Response validation
Every notice is allow-listed.
## 13. Title
Bounded customer-visible title.
## 14. Body
Bounded customer-visible body.
## 15. Notice type
Presentation label only.
## 16. Target type
Used only for safe navigation.
## 17. Target id
Must be UUID when present.
## 18. Read time
Nullable valid timestamp.
## 19. Created time
Required valid timestamp.
## 20. Raw payload
Never accepted or rendered.
## 21. Event key
Never accepted or rendered.
## 22. Provider data
Never accepted or rendered.
## 23. Retry metadata
Never accepted or rendered.
## 24. Unread count
Calculated from readAt only.
## 25. Pull refresh
Inbox supports refresh.
## 26. Mark read
Unread item is updated after successful PATCH.
## 27. Order navigation
ORDER targets open owned order detail.
## 28. Delivery navigation
DELIVERY targets open owned tracking.
## 29. Unknown target
Notice remains readable without navigation.
## 30. Empty state
No notifications is valid.
## 31. Loading state
Customer-safe loading copy.
## 32. Error state
Network and service errors are safe.
## 33. Date locale
Timestamps use en-IN presentation.
## 34. CI typecheck
Full mobile application is checked.
## 35. CI tests
Notification parser tests run.
## 36. CI auth
Authorization header is verified.
## 37. CI privacy
Runtime source is scanned for internal fields.
## 38. Branch
feature/customer-mobile-notifications.
## 39. Stack base
feature/customer-mobile-orders.
## 40. APIM dependency
Existing notification APIM routes must remain available.
## 41. Backend dependency
Notification Service JWT configuration must be healthy.
## 42. Native shell
Android/iOS projects remain pending.
## 43. Firebase files
No native config is committed.
## 44. Azure state
No Azure change occurred during coding.
## 45. Provider state
No provider call occurred.
## 46. Test account
Use a customer with read and unread notices.
## 47. Ownership test
Another customer's notice must not be readable or markable.
## 48. Navigation test
Known order and delivery targets must open safely.
## 49. Rollback
Remove screen, route and home entry only.
## 50. Acceptance
CI, ownership, unread, mark-read, navigation and privacy checks must pass.
