# Architecture decision records

Each record states one decision that shapes this reference architecture: the problem it answered, what was decided, and what follows from it — including what it costs. They are written for engineers who adopt the architecture, or who wonder why the code is the way it is before changing it.

A record is not edited once accepted, except to fix a mistake or a link. A decision that changes gets a new record, and the old one's status says which record supersedes it.

| # | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-the-cube-knows-only-scopes.md) | The Cube knows only scopes; each rendering technology owns its views | Accepted |
| [0003](0003-places-declared-by-keys-classes.md) | Places are declared by keys classes, in a tree | Accepted |
| [0004](0004-actions-are-guarded-scope-fields.md) | Actions are guarded fields of the scope, run one at a time | Accepted |
| [0005](0005-computed-state-once-per-paint.md) | Computed state is committed once per paint cycle | Accepted |
| [0006](0006-access-control-at-the-rest-boundary.md) | Access control lives at the REST boundary, as data policies | Accepted |
| [0007](0007-repositories-and-resource-calls.md) | Read-only and read-write repositories; the presentation uses resource calls | Accepted |
| [0008](0008-remote-transactions-over-rest.md) | Remote transactions over REST | Accepted |
| [0009](0009-suspending-http-on-every-client.md) | HTTP suspends on every client; the remote transaction goes with the coroutine | Accepted |
| [0010](0010-server-driven-remote-views.md) | Server-driven remote views, independent of UI and server technology | Accepted |
| [0011](0011-remote-channel-security.md) | The remote channel: a key agreed per connection, signatures by HMAC | Accepted |
| [0012](0012-scram-sign-in.md) | Sign-in with SCRAM-SHA-256 | Accepted |
| [0013](0013-the-server-prices-items.md) | The server decides the price of an item | Accepted |
| [0014](0014-explicit-work-directory.md) | The backend runs from an explicit work directory | Accepted |
| [0015](0015-action-handlers-carry-the-action-name.md) | The method an action runs carries the action's name | Accepted |
| [0016](0016-versioned-remote-protocol.md) | The remote protocol is versioned, and specified by shared fixtures | Accepted |
| [0017](0017-mesh-between-instances.md) | The backend instances tell each other over a WebSocket mesh | Accepted |
| [0018](0018-failed-sign-ins-in-the-database.md) | Failed sign-ins are counted in the database; the per-address limit stays per instance | Accepted |
| [0019](0019-metrics-and-health.md) | Metrics with Micrometer in Prometheus format, behind a token; live and ready health checks | Accepted |

New record: copy the shape of an existing one, take the next number, and add it to this table.
