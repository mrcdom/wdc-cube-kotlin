# 0019. Metrics with Micrometer in Prometheus format, behind a token; live and ready health checks

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

The backend gave no picture of itself.

- `/health` always answered `UP`, whether or not the database was reachable.
- The stress tool's `/api/stress/metrics` was open even with security on, and reported the remote sessions, heap, threads and a request count with its average time.
  - Its request counter was never fed, so it always read 0.
  - The stress tool never read it either: its client for that route was never constructed.
- With several instances ([0017](0017-mesh-between-instances.md)), the mesh, the sessions, the sign-ins and the remote transactions were not visible from outside.

## Decision

**Micrometer, read by Prometheus.** The backend keeps a `PrometheusMeterRegistry` (`Observability`, in `:backend`) and adds it to Micrometer's global registry. It records:

- the JVM;
- every HTTP request, by method, the route as declared, and status;
- the WDC Store's own meters: sign-ins by outcome, sessions, remote view sessions and messages, the REST API's remote transactions, and the mesh.

What the Store's lower layer counts goes to the global registry: `persistence.impl` records sign-ins and sessions, and the mesh records its messages. **The framework modules take no metrics library.** They expose what they know, and the backend reads it:

- `RemoteTransactionCoordinator.stats()`, which already existed;
- the new `RemoteApplicationRegistry.onMessage`, told by the transport after each client message.

**`/metrics` on the public port, behind a token** (`management.token`, `Authorization: Bearer`). Without the token in the configuration, the route does not exist. The stress tool's route, its controller and its unused client are removed.

**Two health checks, open:**
- `/health/live`: the process answers. `/health` stays, as an alias of it.
- `/health/ready`: the database answers; 503 when it does not. With the mesh it also reports the mesh's connections, but does not require them: an instance alone still serves.

## Consequences

- Any Prometheus-compatible system can read the backend. Another backend for the metrics (OTLP, Datadog…) means swapping the registry, not the meters.
- Each instance reports its own numbers; Prometheus adds them up. Gauges such as `store_sessions_live` are per instance by nature.
- The token travels on every scrape: over a network that is not private, the backend has to be behind TLS, as for the API's tokens.
- A load balancer can take out an instance whose database is gone.
- HTTP routes are labeled by their template (tag `route`, not Micrometer's usual `uri`), so the number of series stays bounded. Requests that match no route — static files, redirects, 404s — share one label, `unmatched`.
- The meters count what their code sees, no more: the backend README says what each outcome covers.

## Alternatives considered

- **OpenTelemetry (OTLP push, metrics and traces):** the most complete, but nothing is visible without a collector, and it is heavier for a reference demo. Micrometer can export to OTLP later without touching the meters.
- **Prometheus text written by hand:** no dependency, but no JVM or HTTP meters unless they are written by hand too.
- **A separate management port**, bound to the local interface: it keeps the metrics away from the public port without a token, but it adds a second listener to configure and route. The maintainer chose the token on the public port.
- **Micrometer in the framework modules:** a dependency for every consumer of the framework, for meters the consumer may not want. The framework exposes numbers and hooks instead.
