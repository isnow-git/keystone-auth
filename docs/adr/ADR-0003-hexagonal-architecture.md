# ADR-0003: Hexagonal architecture (ports & adapters)

## Status

Accepted

## Context

A Spring Boot service can be written end-to-end in `@RestController` +
`@Service` + `@Repository` classes. That style ships features quickly and is
familiar to every Spring developer.

It also entangles business rules with framework concerns. Domain code ends up
importing `org.springframework.*` and `jakarta.persistence.*`. Tests need a
Spring context or a database to exercise rules that are pure functions on
data. Replacing Spring Security, swapping JPA for jOOQ, or porting a use case
to another runtime becomes a refactor instead of a config change.

For an auth service whose rules (token rotation, reuse detection, password
invariants) are exactly the parts we want to verify in isolation, that
coupling is a liability.

## Decision

Adopt **hexagonal architecture** with four Gradle modules:

- `:domain` — entities, value objects, sealed result types. Pure Java,
  **zero framework dependencies**. Cannot compile against Spring even by
  accident.
- `:application` — use cases (`RegisterUseCase`, `LoginUseCase`,
  `RefreshUseCase`, `LogoutUseCase`). Depends on `:domain`. Defines **ports**
  — interfaces it needs the outside world to implement (e.g. `UserRepository`,
  `PasswordHasher`, `TokenIssuer`).
- `:infrastructure` — **adapters** that implement those ports: jOOQ-backed
  repositories, Argon2 hasher, Nimbus JWT issuer, REST controllers, Spring
  Security wiring.
- `:boot` — thin Spring Boot launcher. Composition root.

The build enforces the dependency direction: `:domain` declares no Spring on
the classpath, so a wrong import fails the build.

## Consequences

**Positive**

- Domain rules are testable as plain JUnit tests — sub-second feedback, no
  Spring context, no Testcontainers.
- Swapping persistence (jOOQ ↔ JPA ↔ in-memory fake) touches one module.
- Use cases read like the spec: orchestrate ports, return a sealed result.
- Code review can locate the security-critical logic in one place
  (`:domain` + `:application`) instead of tracing through annotations.

**Negative**

- More upfront ceremony than a single-module Spring app: four modules, ports,
  manual wiring of adapters in `:boot`.
- Junior contributors may put logic in the wrong layer until they internalize
  the rule "domain knows nothing about Spring."
- Adapter code feels like boilerplate — translating between domain types and
  jOOQ records, between use case results and HTTP responses.

**Trade-off accepted.** This service is small enough that the ceremony is
cheap, and the parts that are worth getting right (auth rules) are exactly
what the architecture isolates.
