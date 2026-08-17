#!/usr/bin/env bash
set -euo pipefail
set +x

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_DIR="$ROOT/apps/mobile/customer-app"
RN_VERSION="0.86.0"
CONFIRM="${CONFIRM_NATIVE_BOOTSTRAP:-false}"

fail() { echo "ERROR: $*" >&2; exit 1; }
[[ "${CONFIRM,,}" == "true" ]] || fail "Set CONFIRM_NATIVE_BOOTSTRAP=true after reviewing the native setup steps."
command -v node >/dev/null || fail "Node.js is required"
command -v npm >/dev/null || fail "npm is required"
[[ -d "$APP_DIR" ]] || fail "Customer app directory is missing"
[[ ! -d "$APP_DIR/android" && ! -d "$APP_DIR/ios" ]] || fail "android/ or ios/ already exists; this script never overwrites native projects"

TMP=$(mktemp -d)
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

pushd "$TMP" >/dev/null
npx --yes @react-native-community/cli@latest init CravesCustomerNative \
  --version "$RN_VERSION" \
  --package-name in.craves.customer \
  --skip-install
popd >/dev/null

GENERATED="$TMP/CravesCustomerNative"
[[ -d "$GENERATED/android" && -d "$GENERATED/ios" ]] || fail "React Native native projects were not generated"
cp -R "$GENERATED/android" "$APP_DIR/android"
cp -R "$GENERATED/ios" "$APP_DIR/ios"

cat <<'MESSAGE'
Native Android and iOS shells were generated without overwriting the TypeScript application layer.

Manual steps still required:
1. Download google-services.json from the existing Firebase Android app and place it at apps/mobile/customer-app/android/app/google-services.json.
2. Download GoogleService-Info.plist from the existing Firebase iOS app and add it to the Xcode application target.
3. Apply the React Native Firebase Gradle/CocoaPods integration documented in the module README.
4. Run npm install and, on macOS, cd ios && pod install.
5. Do not commit Firebase config files, signing keystores, provisioning profiles or private keys.
MESSAGE
