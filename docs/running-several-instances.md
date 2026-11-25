# Running several instances

One backend instance needs nothing in this document. It is for putting several behind one address — for capacity, or so that one can stop while the others serve — and says what they must share, what each keeps in its own memory, and how requests have to be routed so that each reaches the instance that holds what it needs.

The demo was built and tested as one instance. Nothing below was run against several; it comes from reading what each part keeps and where.

## What every instance shares

- **The database.** All the data, and also what makes a user's session outlive one instance: the sessions (`EN_USER_SESSION`) and the intent signing secrets (`EN_USER_INTENT_SECRET`). An instance that does not know a session reads it from there.
- **The configuration**, in particular `security.jwt.secret`: it signs the access tokens, encrypts the sessions' private keys in the database, makes the salts answered for unknown users, and gives the key that signs the remote view's session ids. Instances with different secrets reject each other's tokens and session ids.
- **The deployed web clients** (`deployment/`), the same version everywhere.

## What each instance keeps in its own memory

| What | Where | If a request reaches another instance | What to route by |
|---|---|---|---|
| A **remote view session**: the presenters and views of one browser tab | `RemoteApplicationRegistry` | The registry there makes a new, empty session under the same id: what was on the screen is gone | The session id, the last segment of the WebSocket's path (`/dispatcher/<app_id>`) |
| The **key that signs remote session ids** | `RemoteAppSecurity`. The WDC Store's backend derives it from `security.jwt.secret` (`RemoteSigningKey`, given to `useKey`): the same on every instance of the same configuration. **Without the secret it is random per process** | With the secret, nothing: a page served by one instance and its socket on another accept the same id (checked with two instances). Without it, the signature does not check there, the host closes the socket with `4001`, and the page reloads | With the secret, nothing. Without it, the page (`/index.html`, which plants the `app_id` cookie) and its socket must reach the same instance |
| A **remote transaction** of the REST API, which holds a database connection | `RemoteTransactionCoordinatorImpl` | The calls that carry its `X-Tx-Id` get 410: the transaction is unknown there | The client, `X-Client-Id` (sent on every call, from the `begin` on) |
| The **nonce of a sign-in**, between `/api/auth/challenge` and `/api/auth/login` | `NonceStore`, in `AuthenticationServiceImpl` | The login is refused: the nonce is unknown there | The client, `X-Client-Id` |
| A **session's state in memory** (an access token's session) | `AccessContextCache` | It works: the instance reads the session from the database. But a session ended or renewed on one instance stays valid, until its access token expires (30 minutes), on another that had already read it | — |
| **Limits**: failed sign-ins per user, `/api/auth` requests per address and minute, the API's per-minute limit | `LoginThrottle`, `AuthRateLimit`, `rateLimit` | Each instance counts its own: the effective limit is the configured one times the number of instances | — |
| The **product images** already served | the image cache of `ProductApiController` | After an image changes, only the instance that saved it drops the old one; another can keep serving it | — |

## Routing

Two rules cover the table:

1. **A browser tab's socket stays on one instance**, reconnections included: hashing on the session id in the socket's path does it. The page itself can come from any instance, since every instance of the same configuration signs session ids with the same key. (Without `security.jwt.secret` each instance has its own key, and the page must land with its socket — a load balancer's own sticky cookie does that; hashing on the id does not, because the page is requested before the id exists.)
2. **An API client stays on one instance**, for its sign-in and its remote transactions: route by `X-Client-Id`.

A sketch for nginx, **not tested** (rule 1 by the id in the socket's path, rule 2 by `X-Client-Id`):

```nginx
map $uri $affinity_from_path {
    ~^/dispatcher/(?<sid>[^/]+)$  $sid;
    default                        "";
}
map $affinity_from_path $affinity {
    ""       $http_x_client_id;   # API calls: the client's id
    default  $affinity_from_path; # remote view: the session id
}

upstream store {
    hash $affinity consistent;
    server store-a:8080;
    server store-b:8080;
}

server {
    location / {
        proxy_pass http://store;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;     # the WebSocket
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 300s;                    # the host closes idle sockets after 5 minutes
    }
}
```

When an instance stops, what it held in memory is gone: its remote view sessions (a tab starts over on another instance — reloading, if the instances sign ids with different keys — and its user signs in again), its open remote transactions (rolled back; their clients get 410 on the next call), and the sign-ins halfway through. The sessions of the REST API's users are in the database and survive.

## What to change for a real deployment

- **Nonces in the database** (or in a shared store), so a sign-in does not depend on landing twice on the same instance.
- **Ending sessions everywhere**: telling the other instances when a session ends or is renewed, or not keeping sessions in memory at all.
- **Shared counts** for the limits.
- **Invalidating the image cache** on every instance, or not caching images in the instance.

See also: [security-architecture.md](security-architecture.md) (sessions, limits), [architecture-react.md](architecture-react.md) (the remote view's sessions), [architecture-persistence.md](architecture-persistence.md) (remote transactions).
