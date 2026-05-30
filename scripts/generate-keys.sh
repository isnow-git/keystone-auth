#!/usr/bin/env bash
#
# Bootstrap the RS256 keypair used by keystone-auth.
#
# Idempotent: if keys/private.pem and keys/public.pem already exist, the script
# is a no-op so it can be wired into automated start-up paths without clobbering
# operator-managed keys.
#
# Output files:
#   keys/private.pem  PKCS#8, 2048-bit RSA, mode 0600
#   keys/public.pem   X.509 SubjectPublicKeyInfo, mode 0644
#
# Override the destination with KEYS_DIR=... if needed.
set -euo pipefail

KEYS_DIR="${KEYS_DIR:-keys}"
PRIVATE_KEY="${KEYS_DIR}/private.pem"
PUBLIC_KEY="${KEYS_DIR}/public.pem"

if ! command -v openssl >/dev/null 2>&1; then
  echo "error: openssl is required but not installed" >&2
  exit 1
fi

mkdir -p "${KEYS_DIR}"

if [[ -f "${PRIVATE_KEY}" && -f "${PUBLIC_KEY}" ]]; then
  echo "keys already present at ${KEYS_DIR}/ — nothing to do"
  exit 0
fi

# Generate to temporary paths first so a half-finished run never leaves a
# broken file in place.
TMP_PRIVATE="$(mktemp "${KEYS_DIR}/.private.XXXXXX")"
TMP_PUBLIC="$(mktemp "${KEYS_DIR}/.public.XXXXXX")"
trap 'rm -f "${TMP_PRIVATE}" "${TMP_PUBLIC}"' EXIT

openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out "${TMP_PRIVATE}" \
  >/dev/null 2>&1

openssl rsa \
  -pubout \
  -in "${TMP_PRIVATE}" \
  -out "${TMP_PUBLIC}" \
  >/dev/null 2>&1

mv "${TMP_PRIVATE}" "${PRIVATE_KEY}"
mv "${TMP_PUBLIC}" "${PUBLIC_KEY}"
trap - EXIT

chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

echo "generated ${PRIVATE_KEY} (0600) and ${PUBLIC_KEY} (0644)"
