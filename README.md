# keystone-auth

Production-grade authentication microservice. Issues short-lived RS256 JWT
access tokens and rotation-protected refresh tokens; publishes its verification
key at `/.well-known/jwks.json` so any number of resource servers can verify
tokens locally.

> **Status:** scaffolding. Implementation lands incrementally via pull requests
> tracked against the issues in the [project backlog](../../issues).

See [`docs/adr/`](docs/adr) for the design decisions behind every layer
(landing as the documentation PRs are merged).
