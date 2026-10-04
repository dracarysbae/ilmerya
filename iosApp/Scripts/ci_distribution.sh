#!/bin/bash
# Signed App Store build on a disposable CI runner. Secrets arrive as environment variables,
# are written only to a temporary directory and keychain, and are removed on exit.
set -euo pipefail
: "${BUILD_NUMBER:?}" "${APPLE_TEAM_ID:?}"
[[ "$BUILD_NUMBER" =~ ^[0-9]+$ ]] || { echo "Build number must be numeric" >&2; exit 1; }
cd "$(dirname "$0")/../.."
WORK="${RUNNER_TEMP:-/tmp}/ilmerya-sign"
KEYCHAIN="$WORK/ilmerya.keychain-db"
PROFILES="$HOME/Library/MobileDevice/Provisioning Profiles"
mkdir -p "$WORK" "$PROFILES" build
KC_PASS="$(openssl rand -hex 24)"
cleanup() {
  security delete-keychain "$KEYCHAIN" 2>/dev/null || true
  rm -f "$WORK"/* iosApp/Config/Production.xcconfig
  rm -f "$HOME"/.appstoreconnect/private_keys/AuthKey_*.p8
  if [ -n "${UUID:-}" ]; then rm -f "$PROFILES/$UUID.mobileprovision"; fi
}
trap cleanup EXIT

printf '%s' "$APPLE_DISTRIBUTION_P12_B64" | base64 --decode > "$WORK/distribution.p12"
printf '%s' "$APPLE_PROFILE_B64" | base64 --decode > "$WORK/profile.mobileprovision"
unset APPLE_DISTRIBUTION_P12_B64 APPLE_PROFILE_B64

security create-keychain -p "$KC_PASS" "$KEYCHAIN"
security set-keychain-settings -lut 3600 "$KEYCHAIN"
security unlock-keychain -p "$KC_PASS" "$KEYCHAIN"
security import "$WORK/distribution.p12" -k "$KEYCHAIN" -P "$APPLE_DISTRIBUTION_P12_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security >/dev/null
unset APPLE_DISTRIBUTION_P12_PASSWORD
security set-key-partition-list -S apple-tool:,apple: -s -k "$KC_PASS" "$KEYCHAIN" >/dev/null
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '"')

security cms -D -i "$WORK/profile.mobileprovision" > "$WORK/profile.plist"
UUID=$(/usr/libexec/PlistBuddy -c 'Print UUID' "$WORK/profile.plist")
TEAM=$(/usr/libexec/PlistBuddy -c 'Print TeamIdentifier:0' "$WORK/profile.plist")
APPID=$(/usr/libexec/PlistBuddy -c 'Print Entitlements:application-identifier' "$WORK/profile.plist")
GAMECENTER=$(/usr/libexec/PlistBuddy -c 'Print Entitlements:com.apple.developer.game-center' "$WORK/profile.plist" 2>/dev/null || echo missing)
BUNDLE="${APPID#*.}"
EXPECTED=$(awk -F': ' '/PRODUCT_BUNDLE_IDENTIFIER/ {print $2; exit}' iosApp/project.yml)
[ "$TEAM" = "$APPLE_TEAM_ID" ] || { echo "Profile team does not match" >&2; exit 1; }
[ "$BUNDLE" = "$EXPECTED" ] || { echo "Profile is for $BUNDLE, project uses $EXPECTED" >&2; exit 1; }
[ "$GAMECENTER" = "true" ] || { echo "Profile lacks the Game Center entitlement" >&2; exit 1; }
cp "$WORK/profile.mobileprovision" "$PROFILES/$UUID.mobileprovision"

# Applied only to the app target through Release.xcconfig; Swift packages keep their own settings.
# xcconfig treats "//" as a comment, so the URL scheme is written as https:/$()/host.
URL_VALUE="${ILMERYA_API_URL:-}"
URL_VALUE="${URL_VALUE/https:\/\//https:\/\$()\/}"
cat > iosApp/Config/Production.xcconfig <<EOF
DEVELOPMENT_TEAM = $APPLE_TEAM_ID
CODE_SIGN_STYLE = Manual
CODE_SIGN_IDENTITY = Apple Distribution
PROVISIONING_PROFILE_SPECIFIER = $UUID
OTHER_CODE_SIGN_FLAGS = --keychain $KEYCHAIN
CURRENT_PROJECT_VERSION = $BUILD_NUMBER
ILMERYA_API_URL = $URL_VALUE
EOF

(cd iosApp && xcodegen generate)
xcodebuild -project iosApp/Ilmerya.xcodeproj -scheme Ilmerya -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Ilmerya.xcarchive archive | tail -n 60

cat > "$WORK/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>teamID</key><string>$APPLE_TEAM_ID</string>
  <key>signingStyle</key><string>manual</string>
  <key>signingCertificate</key><string>Apple Distribution</string>
  <key>provisioningProfiles</key><dict><key>$BUNDLE</key><string>$UUID</string></dict>
  <key>uploadSymbols</key><true/>
  <key>manageAppVersionAndBuildNumber</key><false/>
</dict></plist>
EOF
xcodebuild -exportArchive -archivePath build/Ilmerya.xcarchive -exportPath build/export \
  -exportOptionsPlist "$WORK/ExportOptions.plist" | tail -n 30
IPA=$(ls build/export/*.ipa)
codesign -d --entitlements :- "build/Ilmerya.xcarchive/Products/Applications/Ilmerya.app" 2>/dev/null | grep -q game-center
echo "Signed $IPA for $BUNDLE build $BUILD_NUMBER"

if [ "${UPLOAD:-false}" = "true" ]; then
  mkdir -p "$HOME/.appstoreconnect/private_keys"
  printf '%s' "$ASC_PRIVATE_KEY_B64" | base64 --decode > "$HOME/.appstoreconnect/private_keys/AuthKey_${ASC_KEY_ID}.p8"
  unset ASC_PRIVATE_KEY_B64
  xcrun altool --upload-app -f "$IPA" -t ios --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID"
  echo "Uploaded to App Store Connect. Processing and TestFlight review are separate steps."
fi
