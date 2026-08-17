# Craves Customer Mobile Authentication Foundation Handover

## 1. Status

Code complete on `feature/customer-mobile-auth-foundation`. No native bootstrap, pipeline, Firebase-console change or store action has been performed.

## 2. Parent dependency

This branch is stacked on the Next.js notification branch and transitively depends on PRs #25–#30.

## 3. Locked stack

React Native 0.86, TypeScript, native Firebase Authentication and Azure APIM-backed Craves Auth.

## 4. Application identity

```text
React Native name: CravesCustomer
Display name: Craves
Proposed Android package: in.craves.customer
```

The final iOS bundle ID must be confirmed during manual native setup.

## 5. Authentication flow

Native Firebase phone OTP produces a Firebase ID token. The app exchanges that token with Craves Auth and stores only the validated short-lived access session.

## 6. Firebase SDK

`@react-native-firebase/app` and `@react-native-firebase/auth` are pinned to 25.1.0.

## 7. Navigation

A native stack selects the OTP screen when no valid session exists and the customer home screen after authentication.

## 8. Session storage

`react-native-keychain` uses device-only, unlocked-device secure storage.

## 9. Forbidden storage

AsyncStorage, plain files, preferences, clipboard and logs are forbidden for access tokens.

## 10. Refresh token

The exchange parser discards any refresh token because an approved mobile refresh contract has not been established.

## 11. Session lifetime

The locally stored expiry is bounded to one hour and considered unusable thirty seconds early.

## 12. Identity checks

The response requires UUID identity, phone number, non-empty roles and `ACTIVE` status.

## 13. Network timeout

Craves Auth exchange is cancelled after ten seconds.

## 14. Error privacy

Raw backend and Firebase token contents are never displayed or logged.

## 15. Rate limiting

HTTP 429 maps to a user-safe retry-later message.

## 16. Firebase rejection

HTTP 401/403 maps to an expired OTP-session message.

## 17. Native OTP verification

Firebase performs Android/iOS app verification according to the registered application and signing identities.

## 18. Android fingerprints

Debug and release SHA-1/SHA-256 fingerprints must be registered manually in Firebase.

## 19. iOS prerequisites

macOS, Xcode, CocoaPods and the registered Firebase iOS application are required.

## 20. Firebase config files

`google-services.json` and `GoogleService-Info.plist` are gitignored and must not be committed.

## 21. Signing files

Keystores, `.jks`, `.p12` and provisioning profiles are gitignored.

## 22. Native shell state

Android and iOS directories are intentionally absent until the bootstrap is manually approved.

## 23. Bootstrap behavior

The script generates React Native 0.86 native shells in a temporary directory and copies only Android/iOS folders.

## 24. No-overwrite rule

The bootstrap fails when either native folder already exists.

## 25. Bootstrap confirmation

`CONFIRM_NATIVE_BOOTSTRAP=true` is mandatory.

## 26. Native review

Generated Gradle, Xcode, package and deployment target settings must be reviewed before committing a native-shell amendment.

## 27. CI scope

Current CI runs TypeScript checks, domain tests, Bash syntax and secret/config-file scans.

## 28. CI limitation

Android/iOS compilation is deferred until native shells and non-secret Firebase test configuration are available.

## 29. App Store limitation

Apple Developer enrollment, App Store Connect records, provisioning and signing are manual-only actions.

## 30. Play Store limitation

Google Play Console records, app signing and release tracks are manual-only actions.

## 31. Azure impact

None. Mobile clients call the existing APIM URL.

## 32. APIM impact

None in this module.

## 33. Database impact

None.

## 34. Service Bus impact

None.

## 35. Cashfree impact

None.

## 36. Delivery-provider impact

None.

## 37. Customer privacy

The temporary home screen displays only phone, display name and roles from the validated session.

## 38. Accessibility

Inputs include phone and OTP autocomplete hints, live status messages and platform keyboard handling.

## 39. Development SMS

Firebase test phone numbers must be used for initial development to avoid SMS cost and throttling.

## 40. Real-device requirement

Production-like phone authentication must be validated on real Android and iOS devices after Firebase registration.

## 41. Emulator use

Emulators may use Firebase test numbers; they must not be treated as proof of release signing integration.

## 42. Sign-out

Sign-out clears Keychain/Keystore data first and then attempts Firebase sign-out.

## 43. Failure recovery

Corrupted or expired secure-storage data is deleted automatically.

## 44. Configuration

The prod-low APIM address is currently a typed constant. Environment/flavor injection will be added with native build configurations.

## 45. Production flavors

Dev, staging and production API/Firebase flavors remain a later native-shell concern.

## 46. Monitoring

Crash and analytics SDKs are not added because consent, privacy and vendor decisions remain pending.

## 47. Push notifications

FCM/APNs registration and notification permissions are deferred.

## 48. Deep links

Order/tracking deep links are deferred until app identifiers and domains are finalized.

## 49. Merge condition

Do not merge until the exact branch head passes the mobile auth CI and parent stacked PRs merge in order.

## 50. Next module

Add authenticated delivery status fetching and a provider-neutral native tracking timeline without enabling provider execution.
