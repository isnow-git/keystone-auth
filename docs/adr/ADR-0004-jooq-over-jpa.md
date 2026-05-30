# ADR-0004: jOOQ over Spring Data JPA

## Status

Accepted

## Context

Persistence options on the JVM cluster around two philosophies:

- **ORMs (JPA/Hibernate, Spring Data JPA)** — map objects to rows; the
  framework writes the SQL.
- **SQL-first libraries (jOOQ, JdbcTemplate, MyBatis)** — the developer writes
  SQL (or a type-safe DSL); the library handles plumbing.

For an auth service, the persistence surface is small (two tables) but each
query matters:

- The hot path is a single `SELECT … WHERE token_hash = ?` on `/refresh`.
- Reuse detection runs a conditional `UPDATE` on the whole token family.
- The domain types (`User`, `RefreshToken`) are not anaemic JavaBeans — they
  carry invariants enforced in constructors.

JPA's strengths (lazy loading across graphs, dirty checking, cascading) bring
costs (N+1 traps, EntityManager lifecycle, `@Transactional` semantics) that we
do not need and that would leak into the domain types if we let them.

## Decision

Use **jOOQ** for all data access. Generate the `Tables`/`Records` classes from
the Flyway-migrated schema. Repository adapters in `:infrastructure` translate
between jOOQ records and domain types.

## Consequences

**Positive**

- The SQL is in the source, where reviewers can find it. `EXPLAIN ANALYZE` is
  a copy-paste away.
- Domain types stay free of `@Entity`, `@Id`, `@Column`. No risk of an
  annotation leaking the framework into `:domain`.
- The compiler catches typos in column names, mismatched types, wrong table
  references. Refactors propagate through the codegen.
- No surprise queries from lazy loading. No `EntityManager` flush semantics to
  reason about.

**Negative**

- More boilerplate than JPA for simple CRUD — every repository method is an
  explicit query.
- The codegen step needs an up-to-date schema; CI must run Flyway against a
  throwaway DB before the build. (Spring Boot's jOOQ starter handles this in
  Testcontainers-style integration tests; codegen can be wired the same way.)
- Junior contributors who only know JPA need a brief on the DSL.

**Trade-off accepted.** The boilerplate is a one-time cost per query in a
service with a small persistence surface. The clarity and the absence of
framework leakage into the domain are worth it.
