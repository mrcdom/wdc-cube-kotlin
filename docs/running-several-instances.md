# Running several instances

One backend instance needs nothing in this document. It is for putting several behind one address — for capacity, or so that one can stop while the others serve — and says what they must share, what each keeps in its own memory, and how requests have to be routed so that each reaches the instance that holds what it needs.

The demo is tested with two instances behind nginx, in the compose setup of [`br.com.wdc.kt.store.docker`](../fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.docker/README.md), on every push. What it does not exercise comes from reading what each part keeps and where.

## What every instance shares

- **The database.** All the data, and also what makes a user's session outlive one instance: the sessions (`EN_USER_SESSION`), the intent signing secrets (`EN_USER_INTENT_SECRET`) the sign-in challenges in flight (`EN_AUTH_NONCE`: a challenge answered by one instance and the login sent to another find the same nonce, used up once) and the failed sign-ins per user (`EN_LOGIN_FAILURE`: attempts spread over the instances count as one). An instance that does not know a session reads it from there.
- **The configuration**, in particular `security.jwt.secret`: it signs the access tokens, encrypts the sessions' private keys in the database, makes the salts answered for unknown users, and gives the keys that sign the remote view's session ids and that the instances of the mesh prove to each other. Instances with different secrets reject each other's tokens, session ids and mesh connections.
- **The deployed web clients** (`deployment/`), the same version everywhere.

## What each instance keeps in its own memory

| What | Where | If a request reaches another instance | What to route by |
|---|---|---|---|
| A **remote view session**: the presenters and views of one browser tab | `RemoteApplicationRegistry` | The registry there makes a new, empty session under the same id: what was on the screen is gone. With the mesh, the instance the tab left lets go of its copy (unless a socket still holds it there); without it, the copy waits to expire | The session id, the last segment of the WebSocket's path (`/dispatcher/<app_id>`) |
| The **key that signs remote session ids** | `RemoteAppSecurity`. The WDC Store's backend derives it from `security.jwt.secret` (`RemoteSigningKey`, given to `useKey`): the same on every instance of the same configuration. **Without the secret it is random per process** | With the secret, nothing: a page served by one instance and its socket on another accept the same id (checked with two instances). Without it, the signature does not check there, the host closes the socket with `4001`, and the page reloads | With the secret, nothing. Without it, the page (`/index.html`, which plants the `app_id` cookie) and its socket must reach the same instance |
| A **remote transaction** of the REST API, which holds a database connection | `RemoteTransactionCoordinatorImpl` | The calls that carry its `X-Tx-Id` get 410: the transaction is unknown there | The client, `X-Client-Id` (sent on every call, from the `begin` on) |
| A **session's state in memory** (an access token's session) | `AccessContextCache` | It works: the instance reads the session from the database. A session ended or renewed on one instance is dropped by the others within a moment, told by the **mesh** when there is one, otherwise **on PostgreSQL** by the database (`PostgresSessionEvents`, `LISTEN`/`NOTIFY`); on H2 without the mesh it would stay valid on another instance that had already read it, until its access token expires (30 minutes) | — |
| **Limits per address**: `/api/auth` requests per address and minute | `AuthRateLimit` | Each instance counts its own: the effective limit is the configured one times the number of instances. That is on purpose: it guards against load, and counting every request in the database would cost a write each; the proxy is the place for it (below). Guessing one account's password is slowed down per user, in the database, however many instances there are | — |
| The **product images** already served | the image cache of `ProductApiController` | With the mesh, every instance drops it when it changes. Without it, only the instance that saved it does; another can keep serving the old one | — |

## The mesh between instances

With `cluster.peers` in `application.toml`, the instances keep a WebSocket open to each other (`ClusterMesh`, route `/cluster`) and tell each other what the table above says the others must know: a session that ended, a product image that changed, a remote view session that moved. One list can serve every instance — each one finds itself in it (it must be able to reach its own address to recognize itself) and leaves that address alone:

```toml
[cluster]
peers = ["ws://store-a:8080", "ws://store-b:8080", "ws://store-c:8080"]
```

- **It needs `security.jwt.secret`**: the two ends of each connection prove to each other a key derived from it (HMAC-SHA256 over a challenge each one makes), and nothing is read or sent before that. The messages travel as they are, and carry session ids: between instances not on a private network, use `wss://` addresses.
- **What is missed is made up for.** A connection that drops is opened again (1 s, doubling up to 30 s), and what was published meanwhile waits for it (the last 1000 messages). Each time another instance connects — at its start, or after a drop — the instance it reaches drops every user session and product image it holds, which are read again from the database; remote view sessions are left to expire. A connection is taken as dropped when a send fails, or after about a minute without an answer to its pings (60 to 80 s).
- **Every instance lists every other one.** An instance only hears from the ones that list it, and one without `cluster.peers` has no `/cluster` route: the others keep trying to reach it.
- Without the mesh, on PostgreSQL the database still tells the instances of the sessions that end; the images and the remote view sessions are told only by the mesh.

The decision, and what else was weighed, is in [ADR 0017](adr/0017-mesh-between-instances.md).

## Routing

Two rules cover the table:

1. **A browser tab's socket stays on one instance**, reconnections included: hashing on the session id in the socket's path does it. The page itself can come from any instance, since every instance of the same configuration signs session ids with the same key. (Without `security.jwt.secret` each instance has its own key, and the page must land with its socket — a load balancer's own sticky cookie does that; hashing on the id does not, because the page is requested before the id exists.)
2. **An API client stays on one instance** while it has a remote transaction open: route by `X-Client-Id`. (Its sign-in does not need it: the challenges are in the database.)

Each instance answers `GET /health/ready` with 503 when its database does not answer: point the load balancer's health check there. nginx open source has no active checks and never calls it: it takes an instance out only after connection errors, or the statuses listed in `proxy_next_upstream`.

The compose setup in [`br.com.wdc.kt.store.docker`](../fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.docker/README.md) runs this: two instances on their mesh, PostgreSQL, Prometheus, and nginx in front with [`nginx.conf`](../fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.docker/nginx.conf) — rule 1 by the id in the socket's path, rule 2 by `X-Client-Id`, and the `/api/auth` limit per address. The CI brings it up on every push and checks it end to end: the browser tests, both remote clients included, run through nginx; a session ended on one instance is refused by the other; nginx limits `/api/auth`; Prometheus scrapes both.

When an instance stops, what it held in memory is gone: its remote view sessions (a tab starts over on another instance — reloading, if the instances sign ids with different keys — and its user signs in again) and its open remote transactions (rolled back; their clients get 410 on the next call). The sessions of the REST API's users are in the database and survive.

## What to change for a real deployment

- **Limiting requests per address at the proxy**, where every instance's traffic passes, with nginx's `limit_req` as the compose setup's `nginx.conf` does. Behind a proxy, every client also reaches the backend from the proxy's address, so the backend's own per-address limit counts them all together: raise `security.auth.requestsPerMinute`, or set it to `0`, and let the proxy limit.

See also: [security-architecture.md](security-architecture.md) (sessions, limits), [architecture-react.md](architecture-react.md) (the remote view's sessions), [architecture-persistence.md](architecture-persistence.md) (remote transactions).
