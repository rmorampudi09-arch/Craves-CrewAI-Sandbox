# Craves Mobile Customer Addresses Handover

Date: 2026-07-30
Status: Code complete; CI, native location amendment and device testing pending.

## 1. Purpose
Manage customer-owned delivery addresses on mobile.
## 2. Platform
React Native and TypeScript.
## 3. Session
Keychain/Keystore-backed Craves access token.
## 4. Authentication
Bearer token on every address request.
## 5. Authorization
User/Chef Service verifies address ownership.
## 6. List
GET /api/v1/customer/addresses.
## 7. Create
POST /api/v1/customer/addresses.
## 8. Read
GET /api/v1/customer/addresses/{addressId}.
## 9. Update
PUT /api/v1/customer/addresses/{addressId}.
## 10. Delete
DELETE /api/v1/customer/addresses/{addressId}.
## 11. Recommendation
GET /api/v1/customer/addresses/recommendation.
## 12. Identifier
Address IDs must be UUIDs.
## 13. Labels
HOME, WORK and OTHER.
## 14. Phone
Ten to fifteen digits with optional plus.
## 15. Coordinates
Latitude and longitude use global bounds.
## 16. Postal fields
Backend-compatible technical length bounds.
## 17. Default flag
Customer can mark an address default.
## 18. Identity privacy
identityId is removed.
## 19. Storage
Address data is not stored in AsyncStorage.
## 20. Logging
Addresses, tokens and coordinates are not logged.
## 21. Session expiry
HTTP 401 signs the customer out.
## 22. Missing address
HTTP 404 receives a safe message.
## 23. Timeout
Shared mobile timeout applies.
## 24. List screen
Pull-to-refresh and delete confirmation.
## 25. Create screen
Complete postal and coordinate form.
## 26. Edit screen
Owned address is loaded by UUID.
## 27. Recommendation UI
Coordinates can be compared to saved addresses.
## 28. Device GPS
Not implemented before native shells.
## 29. Android permission
ACCESS_FINE_LOCATION review is pending.
## 30. iOS permission
NSLocationWhenInUseUsageDescription review is pending.
## 31. Privacy copy
Final location consent copy is pending.
## 32. No geocoding
No geocoding provider is invented.
## 33. No serviceability
Address save does not promise delivery.
## 34. No maps SDK
No maps billing or API key is introduced.
## 35. CI typecheck
Full mobile TypeScript is checked.
## 36. CI tests
Address contract tests run.
## 37. CI identity scan
Runtime identity fields are blocked.
## 38. CI storage scan
Insecure storage is blocked.
## 39. CI location gate
Unreviewed geolocation APIs are blocked.
## 40. Branch
feature/customer-mobile-addresses.
## 41. Stack base
feature/customer-mobile-notifications.
## 42. APIM dependency
Customer address APIM from PR #35 must exist.
## 43. Native shell
Android/iOS shell amendment remains pending.
## 44. Firebase files
No native Firebase files are committed.
## 45. Signing
No keystore or provisioning profile is committed.
## 46. Azure state
No Azure change occurred during coding.
## 47. Test customer
Use one non-production customer.
## 48. CRUD test
Create, read, update and delete one exact fixture.
## 49. Cleanup
Confirm the fixture is deleted and list is restored.
## 50. Acceptance
CI, ownership, CRUD, recommendation, privacy and cleanup must pass.
