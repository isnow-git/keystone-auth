# ADR-0005: Refresh token rotation with family-wide reuse detection

## Status

Accepted

## Context

Refresh tokens trade long-lived authentication for short-lived access tokens.
A leaked refresh token gives the attacker access for its entire lifetime
unless the system can detect the leak.

The naïve approach — one long-lived refresh token, reused on every refresh —
has no signal to distinguish legitimate use from theft. The attacker and the
victim look identical to the server.

OAuth 2.0 Security BCP (RFC 6749 + draft `oauth-v2.1`) recommends
**rotation with reuse detection** for confidential refresh tokens.

## Decision

Implement the following protocol:

1. On `/login`, issue a new refresh token. Assign it a fresh `family_id`.
   Persist `(id, user_id, token_hash, family_id, expires_at, used=false,
   revoked=false)`.
2. On `/refresh`, look up the presented token by hash:
   - If **not found** → reject.
   - If `revoked=true` → reject.
   - If `expires_at` in the past → reject.
   - If `used=true` → **theft detected**. Revoke the entire `family_id`
     (`UPDATE refresh_tokens SET revoked = true WHERE family_id = ?`).
     Reject. Emit a structured log event so ops can investigate.
   - Else → mark the current token `used=true`, issue a new token in the
     same family, return it. Atomic transaction.
3. On `/logout`, revoke the family.

The token sent to the client is a 256-bit random value. Only its SHA-256 hash
is stored. A DB leak does not produce usable tokens.

The refresh token rides in an `HttpOnly; Secure; SameSite=Strict` cookie. The
access token rides in `Authorization: Bearer` headers and is never stored in
cookies.

## Consequences

**Positive**

- Two requests with the same refresh token mean one of them is the attacker.
  The system detects this and locks the family — both the attacker and the
  legitimate user are forced to re-authenticate. The window of compromise is
  bounded by how soon the legitimate user attempts to refresh again.
- The cookie attributes (`HttpOnly`, `SameSite=Strict`) eliminate the
  classes of attacks (XSS reading the token, CSRF replaying it) that would
  trigger reuse in the first place. Rotation is the fallback when those
  controls fail.
- The DB stores only hashes, so a backup leak does not yield live tokens.

**Negative**

- A legitimate client losing the response to `/refresh` (network blip after
  the server rotated the token) appears as reuse on retry, locking the
  family. The UX cost is one forced re-login. Mitigations (e.g. idempotency
  keys, short grace windows) trade off security for fewer false positives;
  we accept the false positive.
- Every refresh writes to the database. With virtual threads and the small
  per-request work, the contention point is the index on `token_hash`, not
  the cost of the write itself.
- The protocol is more complex than "issue once, accept until expiry". The
  test surface grows accordingly.

**Trade-off accepted.** The security property — *we will detect refresh-token
theft* — is the headline feature of the service. The forced re-login on the
rare race is a price worth paying.
