# ADR-0006: Code-first OpenAPI with springdoc

## Status

Accepted

## Context

The service exposes a small HTTP API (5 endpoints). External consumers and
reviewers benefit from a machine-readable contract: request / response shapes,
status codes, security schemes, examples. Two approaches:

- **Spec-first** — hand-write `openapi.yaml`, generate stubs / clients, keep
  the spec authoritative. Strong for API-as-product teams with many client
  languages; the spec is the source of truth.
- **Code-first** — annotate controllers and DTOs with `@Operation`, `@Schema`,
  etc.; the spec is rendered from the running application. springdoc-openapi
  is the de-facto Spring implementation.

For `keystone-auth` the controllers + DTOs are the contract. They are already
test-covered (`@WebMvcTest`, MockMvc); divergence between a hand-written spec
and the running code would be a constant correctness risk. The service is the
product; there is no external SDK that would push us towards spec-first.

## Decision

Use **springdoc-openapi-starter-webmvc-ui** to render OpenAPI 3 + Swagger UI
from controller annotations:

- `OpenApiConfiguration` supplies document-level metadata (title, version,
  description, server, security schemes — `bearerJwt` + `refreshCookie`).
- Each handler in `AuthController` and `JwksController` carries `@Operation`
  + `@ApiResponse` entries.
- DTOs (`RegisterRequest`, `LoginRequest`, `AccessTokenResponse`) carry
  `@Schema` with examples.
- The OpenAPI JSON is served at `/v3/api-docs` and the Swagger UI at
  `/swagger-ui.html`; both are added to `SecurityConfiguration`'s public
  allow-list.

## Consequences

**Positive**

- The spec cannot drift from the code — the code is the spec.
- Reviewers and consumers get a runnable, click-through UI at
  `http://localhost:8080/swagger-ui.html`. No README hunt for examples.
- The OpenAPI document is consumed by SDK generators, contract test tools,
  and API gateways the day a consumer needs them.

**Negative**

- Annotations live alongside code. We have already accepted Spring annotations
  in `:infrastructure` (the layer's whole job is adapters), so the cost is
  zero there — but the same approach in `:domain` would breach ADR-0003.
  Annotations stay in `:infrastructure` DTOs / controllers only.
- springdoc adds ~3 MB to the runtime classpath. Acceptable.
- Generated examples need maintenance; a stale example survives only until a
  reviewer asks why.

**Trade-off accepted.** The contract belongs next to the code that ships it.
A spec-first regime would require a contract test (drift detection) and a CI
job to fail builds when controller and spec disagree. The cost is much
higher than the cost of keeping annotations honest in a 5-endpoint service.
