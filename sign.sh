#!/bin/bash
# Sign an aligned APK with the release key chain.
#
# The key material is kept outside the repository. The chain holds the current
# release key, the previous release key and the rotation lineage between them:
#
#   Android 5-8  read the v1/v2 signature (previous key)
#   Android 9+   read the v3 signature (current key) plus its lineage
#
# Configuration:
#   SKYBLE_SIGNING_DIR    directory with the key chain (default: $HOME/Keystores/skyble)
#   SKYBLE_LEGACY_KS, SKYBLE_LEGACY_PASS, SKYBLE_LEGACY_ALIAS
#   SKYBLE_CURRENT_KS, SKYBLE_CURRENT_PASS, SKYBLE_CURRENT_ALIAS
#   SKYBLE_LINEAGE
#
# The *_PASS and *_ALIAS values default to the matching .properties file in
# SKYBLE_SIGNING_DIR. Nothing here is committed.
set -e

IN="$1"
OUT="$2"
if [ -z "$IN" ] || [ -z "$OUT" ]; then
  echo "usage: sign.sh <aligned.apk> <output.apk>" >&2
  exit 2
fi

DIR="${SKYBLE_SIGNING_DIR:-$HOME/Keystores/skyble}"

prop() {
  [ -f "$1" ] || return 0
  sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*//p" "$1" | head -1
}

LEGACY_PROPS="$DIR/keystore.properties"
LEGACY_KS="${SKYBLE_LEGACY_KS:-$DIR/keystore.jks}"
LEGACY_PASS="${SKYBLE_LEGACY_PASS:-$(prop "$LEGACY_PROPS" storePassword)}"
LEGACY_ALIAS="${SKYBLE_LEGACY_ALIAS:-$(prop "$LEGACY_PROPS" keyAlias)}"

CURRENT_PROPS="$DIR/skyble-release.properties"
CURRENT_KS="${SKYBLE_CURRENT_KS:-$DIR/skyble-release.jks}"
CURRENT_PASS="${SKYBLE_CURRENT_PASS:-$(prop "$CURRENT_PROPS" storePassword)}"
CURRENT_ALIAS="${SKYBLE_CURRENT_ALIAS:-$(prop "$CURRENT_PROPS" keyAlias)}"

LINEAGE="${SKYBLE_LINEAGE:-$DIR/lineage.bin}"

for f in "$LEGACY_KS" "$CURRENT_KS" "$LINEAGE"; do
  if [ ! -f "$f" ]; then
    echo "signing material missing: $f" >&2
    echo "point SKYBLE_SIGNING_DIR at the key chain, or build with UNSIGNED=1" >&2
    exit 1
  fi
done
for v in LEGACY_PASS LEGACY_ALIAS CURRENT_PASS CURRENT_ALIAS; do
  if [ -z "${!v}" ]; then
    echo "signing setting missing: $v" >&2
    echo "set it in the environment or in the .properties file under SKYBLE_SIGNING_DIR" >&2
    exit 1
  fi
done

apksigner sign \
  --ks "$LEGACY_KS" --ks-key-alias "$LEGACY_ALIAS" \
  --ks-pass "pass:$LEGACY_PASS" --key-pass "pass:$LEGACY_PASS" \
  --next-signer \
  --ks "$CURRENT_KS" --ks-key-alias "$CURRENT_ALIAS" \
  --ks-pass "pass:$CURRENT_PASS" --key-pass "pass:$CURRENT_PASS" \
  --lineage "$LINEAGE" --rotation-min-sdk-version 28 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$IN"
