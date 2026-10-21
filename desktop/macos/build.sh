#!/bin/bash
# Builds Perform Library for macOS: runs the desktop tests, then makes a .dmg that is
# signed with your Developer ID and notarized by Apple, so it opens without warnings.
#
# One-time setup on the Mac:
#   1. JDK 17 or newer (with jpackage):   brew install --cask temurin@17
#   2. A "Developer ID Application" certificate in your login keychain
#      (Xcode > Settings > Accounts > Manage Certificates > + > Developer ID Application).
#   3. Notarization credentials saved in the keychain (asks for an app-specific password
#      from appleid.apple.com; nothing is written to the project):
#        xcrun notarytool store-credentials perform-notary --apple-id <you@example.com> --team-id <TEAMID>
#
# Run it in Terminal on the Mac itself: macOS only lets your logged-in session use the
# signing key, so it can't sign over SSH. The first time, macOS asks to let codesign
# use the key: enter your login password and choose Always Allow.
#
# Usage:  desktop/macos/build.sh            signed + notarized .dmg
#         desktop/macos/build.sh --unsigned  just a .dmg, for a quick local try
set -euo pipefail

cd "$(dirname "$0")/../.."
unsigned=false
[[ "${1:-}" == "--unsigned" ]] && unsigned=true

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
fail() { printf '\n\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

# Java 17+ with jpackage.
if [[ -z "${JAVA_HOME:-}" ]]; then
    # The long-term-support releases first: 21, then 17, then anything newer.
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 17+ 2>/dev/null || true)"
fi
[[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/jpackage" ]] ||
    fail "Needs JDK 17 or newer with jpackage. Install it with: brew install --cask temurin@17"
export JAVA_HOME
say "Using Java at $JAVA_HOME ($(uname -m))"

if ! $unsigned; then
    identity="$(security find-identity -v -p codesigning | sed -n 's/.*"\(Developer ID Application: .*\)"/\1/p' | head -1)"
    [[ -n "$identity" ]] ||
        fail "No \"Developer ID Application\" certificate in the keychain. See the setup notes at the top of this script, or run with --unsigned."
    notary=perform-notary
    if ! xcrun notarytool history --keychain-profile "$notary" >/dev/null 2>&1; then
        # Saved before the app was renamed from Preform.
        notary=preform-notary
        xcrun notarytool history --keychain-profile "$notary" >/dev/null 2>&1 ||
            fail "No notarization credentials saved as \"perform-notary\". See the setup notes at the top of this script."
    fi
    # jpackage wants the name without the "Developer ID Application: " prefix.
    export PERFORM_SIGN_IDENTITY="${identity#Developer ID Application: }"
    say "Signing as: $identity"
    # A Gradle daemon left over from another session (e.g. SSH) can't reach this
    # session's keychain, so signing would fail: start from a fresh one.
    ./gradlew --stop -q >/dev/null 2>&1 || true
fi

say "Testing"
./gradlew -PdesktopOnly :desktop:test --console=plain -q

say "Packaging"
./gradlew -PdesktopOnly :desktop:packageDmg --console=plain -q
dmg="$(ls -t desktop/build/compose/binaries/main/dmg/*.dmg | head -1)"
[[ -f "$dmg" ]] || fail "No .dmg was made."

if ! $unsigned; then
    say "Signing the disk image"
    codesign --force --timestamp --sign "$identity" "$dmg"
    say "Notarizing (Apple usually takes a few minutes)"
    xcrun notarytool submit "$dmg" --keychain-profile "$notary" --wait
    xcrun stapler staple "$dmg"
    say "Checking Gatekeeper accepts it"
    spctl --assess --type open --context context:primary-signature -v "$dmg"
fi

say "Done: $dmg"
