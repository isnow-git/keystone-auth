# ADR-0001: RS256 over HS256 for JWT signing

## Status

Accepted

## Context

`keystone-auth` issues access tokens consumed by independent backend services.
Two practical choices exist for signing JWTs:

- **HS256** — HMAC with SHA-256. A single shared secret signs and verifies.
- **RS256** — RSA with SHA-256. A private key signs; a public key verifies.

The token consumers are separate processes — potentially owned by different
teams, deployed on different schedules. They must be able to verify tokens
without coordinating with `keystone-auth` on every request.

## Decision

Sign access tokens with **RS256**. Publish the verification key as a JWK Set at
`/auth/.well-known/jwks.json` so resource servers can fetch and cache it.

## Consequences

**Positive**

- Resource servers verify tokens locally with the public key. No round trip to
  `keystone-auth` per request, no shared-secret distribution problem.
- The signing key never leaves `keystone-auth`. A compromised consumer cannot
  mint new tokens.
- Key rotation is operational, not architectural: publish the new `kid`
  alongside the old one, sign with the new, retire the old after token TTL.
- The JWKS endpoint is the same shape OIDC providers expose, so any standard
  OAuth2 Resource Server (Spring Security included) works out of the box.

**Negative**

- RSA verification is measurably slower than HMAC — typically <1 ms per token
  on commodity hardware, acceptable for an auth boundary.
- Operational surface area is higher: PEM files, key IDs, JWKS caching, key
  rotation runbook.
- Bigger tokens (signature is ~342 bytes for RSA-2048 vs ~44 bytes for HMAC),
  marginally heavier `Authorization` headers.

**Trade-off accepted.** The architecture is multi-service, so the secret
distribution and "blast radius on key leak" properties of HS256 are
disqualifying. RS256's cost is a one-time setup, not a per-request tax of any
meaningful size.
