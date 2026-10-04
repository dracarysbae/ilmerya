#!/bin/sh
set -eu

if [ "${CONFIGURATION:-Debug}" = "Debug" ]; then
    if [ "${ADMOB_APP_ID:-}" != "ca-app-pub-3940256099942544~1458002511" ] ||
       [ "${ADMOB_INTERSTITIAL_ID:-}" != "ca-app-pub-3940256099942544/4411468910" ]; then
        echo 'error: Debug must use the dedicated Google iOS test AdMob IDs.' >&2
        exit 1
    fi
    exit 0
fi

if ! printf '%s' "${ADMOB_APP_ID:-}" | /usr/bin/grep -Eq '^ca-app-pub-[0-9]{16}~[0-9]{10}$' ||
   ! printf '%s' "${ADMOB_INTERSTITIAL_ID:-}" | /usr/bin/grep -Eq '^ca-app-pub-[0-9]{16}/[0-9]{10}$'; then
    echo 'error: Set real iOS ADMOB_APP_ID and ADMOB_INTERSTITIAL_ID in Config/Production.xcconfig.' >&2
    exit 1
fi
case "$ADMOB_APP_ID $ADMOB_INTERSTITIAL_ID" in
    *ca-app-pub-3940256099942544*)
        echo 'error: Google test AdMob IDs cannot be archived in Release.' >&2
        exit 1 ;;
esac
