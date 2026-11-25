# 0017. The backend instances tell each other over a WebSocket mesh

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

Several instances of the backend can serve the same clients ([running-several-instances.md](../running-several-instances.md)). Each one keeps some things in memory that another instance can make stale:

- the sessions it has read (`AccessContextCache`): a session signed out, or renewed, on another instance stayed valid here until its access token expired, up to 30 minutes;
- the product images it has served: an image changed on another instance went on being served here;
- the remote view sessions: when a tab moves to another instance, its copy here waits to expire.

The first fix used the database: on PostgreSQL, `LISTEN`/`NOTIFY` tells the instances of the sessions that end (`PostgresSessionEvents`). It needs nothing more than the database, but only PostgreSQL has it, and it ties the instances' messages to the database.

## Decision

**The instances keep a WebSocket open to each other**, when `cluster.peers` in `application.toml` names them (`ClusterMesh`, in `:backend`, route `/cluster`). Each one opens a connection to every peer and publishes on it; it receives on the connections the others open to it. Every message is a type and an id: `session-ended`, `image-changed`, `remote-session-made` (`StoreCluster`).

- **A static list**, which can be the same for every instance: each one recognizes its own address (the other end answers with its own node id; so it must reach its own address) and leaves it alone.
- **Both ends prove a key** derived from `security.jwt.secret` (HMAC-SHA256 over a challenge each one makes), before anything is read or sent. So the mesh needs the secret, and the backend does not start with `cluster.peers` and without it.
- **What is missed is made up for, not resent.** A connection that drops is opened again; each time another instance connects (at its start, or after a drop), the receiving instance drops every user session and product image it holds, because they are read again from the database. Messages published while the connection was down also wait for it (the last 1000). A remote view session whose message was missed waits to expire, as without the mesh.
- **With the mesh, it carries the sessions too**; without it, PostgreSQL still does. The sessions are told through one interface (`SessionEvents`), with three implementations: the mesh, PostgreSQL, and none.

## Consequences

- Sessions and images are kept consistent on any database, and the copy of a remote view session is released where its tab left. That includes H2 if every instance opens the same file, for example with `AUTO_SERVER=TRUE` in `database.url` (not the default); that is how two instances were checked.
- Every instance has to know every other one: adding an instance means changing the list everywhere.
- The messages are not encrypted by the mesh: session ids go as they are. Between instances that are not on a private network, the addresses must be `wss://`.
- A message published as a connection breaks, before anyone notices, can be lost. For sessions and images that costs nothing, because the receiving instance drops what it holds when the connection comes back; a missed remote view session waits to expire. But the window is real: a connection that fails silently is noticed after a failed send, or after 60 to 80 s without answers to its pings.
- Starting or restarting an instance makes every instance it lists drop its cached sessions and images: a burst of reads from the database.

## Alternatives considered

- **Webhooks** (an HTTP POST to each peer per message): no connections to keep, plain HTTP through any proxy, the usual per-request HMAC signature. But only the sender knows that a delivery failed. The receiver would need sequence numbers per sender, the sender a retry queue, and both a heartbeat, just to learn what a connection that drops and comes back says by itself. Each request would also need its own protection against replay, and a burst of sign-ins becomes a burst of requests.
- **PostgreSQL `LISTEN`/`NOTIFY` only:** already done, and kept as the fallback. It does not cover H2, and every kind of message would go through the database.
- **A broker** (Redis pub/sub, NATS): the usual answer at scale, but it is one more service to run for a reference demo.
- **Discovery through the database** (each instance writes its address, with a heartbeat): no list to keep, but a table and the cleanup of instances that died. A static list is simpler and explicit.
- **Not keeping sessions in memory:** every request would read the database.
