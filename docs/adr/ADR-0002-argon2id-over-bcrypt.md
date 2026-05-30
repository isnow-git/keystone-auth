# ADR-0002: Argon2id over BCrypt for password hashing

## Status

Accepted

## Context

Passwords are stored as one-way hashes. The candidates considered:

- **BCrypt** — 1999, Blowfish-based, configurable cost factor.
- **scrypt** — 2009, memory-hard.
- **Argon2id** — 2015, winner of the Password Hashing Competition. The `id`
  variant blends Argon2i (side-channel resistant) and Argon2d (GPU-resistant).

BCrypt is everywhere because it shipped with Spring Security for years and is
"good enough" for most projects. But it has a hard cap of 72 bytes on input
length and is **not memory-hard** — modern GPUs and ASICs crack it
significantly faster than CPUs can verify it.

## Decision

Hash passwords with **Argon2id** via `spring-security-crypto`'s
`Argon2PasswordEncoder`. Default parameters tuned for ~250 ms verification on
the target hardware (saltLength=16, hashLength=32, parallelism=1, memory=65536
KiB, iterations=3) — adjust after capacity testing.

## Consequences

**Positive**

- Memory-hard by design: the attack cost on GPUs/ASICs is closer to the cost
  on the defender's CPU. This is the property BCrypt lacks.
- OWASP's current recommendation for new projects.
- Spring Security ships first-class support — `PasswordEncoder` bean swap, no
  custom code.
- No 72-byte input limit.

**Negative**

- Verification is intentionally expensive (~250 ms). Login throughput per core
  is bounded by this. Mitigation: virtual threads (Project Loom) absorb the
  blocking nature; capacity is RAM, not threads.
- Parameter selection requires benchmarking on production-class hardware.
- Less interview folklore than BCrypt — engineers used to BCrypt occasionally
  question the choice, which the ADR exists to settle.

**Trade-off accepted.** The cost of verification is the security property we
are buying. Tuning Argon2 lower than ~100 ms per attempt undoes the point.
