#!/bin/sh

./gradlew clean assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || exit 1

CERT_SHA256="4296b8b202e8f3336eaeabc61ab5655d8b6931dadaaabdd4cd81941952d3ef84"
apksigner verify --print-certs "$APK" | grep -q "$CERT_SHA256" || exit 1

VERSION_CODE=$(git rev-list --count HEAD)
VERSION_NAME=$(git rev-parse --short HEAD)
RELEASE="ButcherPDA-$VERSION_CODE-$VERSION_NAME.apk"
[ -f "$RELEASE" ] && rm "$RELEASE"

cp "$APK" "$RELEASE"