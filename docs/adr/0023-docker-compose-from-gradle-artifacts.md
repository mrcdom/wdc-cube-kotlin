# 0023. A compose setup with two instances, its image made from what Gradle built

- **Date:** 2026-11-26
- **Status:** Accepted

## Context

[running-several-instances.md](../running-several-instances.md) described how to run the backend as several instances:
- the mesh ([0017](0017-mesh-between-instances.md));
- the shared database;
- nginx in front, for affinity and the per-address limit;
- Prometheus ([0019](0019-metrics-and-health.md)).

None of it had been run together, and the nginx configuration was marked "not tested".

## Decision

**A compose setup in the repository** (`fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.docker`), with five services:
- PostgreSQL;
- two backend instances on their mesh;
- nginx in front, with the routing and the `/api/auth` limit of the guide;
- Prometheus, scraping both instances with the management token.

**The image takes what Gradle built:**
- `prepare.sh` builds the backend's distribution and the web clients, and stages them;
- the Dockerfile copies them onto a JRE.
- The configuration, secrets included, comes from the environment at start, so no secret is in the image.

**Tested on every push** by a CI job:
- `check.py` checks, from outside: readiness, the mesh connected, a session ended on one instance refused by the other, nginx's limit, and Prometheus's targets.
- Then the browser tests run through nginx.

## Consequences

- The guide's nginx configuration is the one the CI runs. It is no longer a sketch.
- The image is small, with no JDK, Node or Android SDK, but `docker build` alone does not build it: `prepare.sh` comes first, and it needs the project's toolchain.
- The setup is a reference to start from, not a production deployment: no TLS, ports open for checks, secrets in a local `.env`.

## Alternatives considered

- **Everything built inside Docker** (multi-stage): self-contained, but the multiplatform configuration needs the Android SDK even for the backend, which makes a heavy and slow build.
- **The files as documentation, untested:** less work, but "not tested" was exactly the problem.
