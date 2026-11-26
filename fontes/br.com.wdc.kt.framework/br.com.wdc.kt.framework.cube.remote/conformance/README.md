# Remote protocol: specification and conformance fixtures

The messages between a remote Cube host (presenters on the server) and its clients (the browser draws scopes and sends events), version **1**, and the fixtures any implementation — a host or a client, in any language — runs to show it speaks it. The Kotlin host (`remote.backend`), the Kotlin client (`remote.bridge.kotlin`) and the TypeScript client (`remote.bridge.typescript`) run them in their tests; the Java and C# versions of the architecture can run the same files.

## The protocol, version 1

One WebSocket per page, at `/dispatcher/<session id>`, with the subprotocol `wdc`. Every message is one JSON object.

### Client → host

| Field | When | What |
|---|---|---|
| `requestId` | always | A number, growing; the host answers up to the last one it read. The handshake carries the last one the client saw answered (`-1` at first). |
| `ping` | handshake, keep-alive | `true`: the host answers even if nothing changed. |
| `clientKey` | handshake | The client's ephemeral ECDH P-256 public key, Base64 SubjectPublicKeyInfo. |
| `protocol` | handshake | The version the client speaks: `1`. |
| `path` | handshake | The page's address (its hash). |
| `event` | events | A list of `<vsid>:<code>`: the view instance and the code of its action. The same event twice in one message runs once. |
| `<vsid>` | form data | An object with what the user typed in that view (`{"p.label": "…"}`), read before the events run. Sensitive values are ciphered with the session key (AES-256-GCM, a random 12-byte IV in front, Base64). |

The **handshake** is the first message on every socket (a reconnection makes a new one). The key is derived from the ECDH secret with HKDF-SHA256, the session id as salt and `wdc-cube-remote/aes-256-gcm/v1` as info. The client sends nothing else on that socket before the host answers the handshake.

The browser's view is `7b32e816a191:0`. Its codes: `-1` start (form `p.path`), `-2` the history changed (form `p.path`), `1` the alert's OK, `2` keep-alive.

### Host → client

| Field | When | What |
|---|---|---|
| `requestId` | always | The last client request read. |
| `ping` | answer to a ping | `true`. |
| `serverKey` | answer to a handshake | The host's ECDH P-256 public key, Base64 SubjectPublicKeyInfo. |
| `protocol` | answer to a handshake | The version the host speaks with this client. |
| `uri` | when the address changed | The address the client shows, signed by the host (it carries a `sign=…` parameter). |
| `scopes` | when views changed | Only the views that changed, each whole: `{"id": "<vsid>", <field>: <value>, …}`. A field `<name>ViewId` holds a child view's vsid; `<name>ViewIds`, a list of them (empty included). A view created while answering goes out in the same answer. |
| `releasedViews` | after `scopes` | The vsids of the views the host let go of since its last message; the client drops each one (at once, or when it leaves the screen). |
| `error`, `protocols` | refusal | `"unsupported-protocol"`, and the versions the host speaks. |

A vsid is `<vid>:<instance>`: the vid names the client component that draws that kind of scope (12 hex digits, declared by the scope class), the instance numbers the view in the session.

### The version

The host answers a handshake with a version it does not speak — or with none — and any message before an accepted handshake with `{"requestId": …, "error": "unsupported-protocol", "protocols": [1]}`, and reads nothing in it. The client then sends nothing more, does not reconnect, and tells the user the page is out of date (the browser's alert, `{"id": -1, "args": ["This page is out of date. Reload it to continue."]}`; its OK reloads the page). That text is the client's default: an application may supply its own (translated); the fixtures check the default. The version goes up only when the wire changes in a way the other side cannot read.

## The fixtures

`host/*.json` are for hosts: messages to send, and the answers to expect. `client/*.json` are for clients: messages to receive, what the user does, and what to expect. Each file is `{"description": "…", "steps": [ … ]}`, run in order, on a new session.

### Host steps

| Step | What the runner does |
|---|---|
| `{"send": {…}, "expect": {…}}` | Sends the message; the next answer must match `expect`. |
| `{"send": {…}, "expectNothing": true}` | Sends the message; no answer may come. |

Host fixtures run against the **conformance application**, which each host implements:

- Its root scope (the browser's content) has vid `c0ffee000001`, a field `count` (a number, `0` at first) and a list `rows` of row scopes (`rowsViewIds`, empty at first). Event `1` adds one to `count`; event `2` adds a row whose `label` is the form value `p.label`.
- A row scope has vid `c0ffee000002`, a field `label`, and event `1`, which removes the row.

### Client steps

| Step | What the runner does |
|---|---|
| `{"start": {"hash": "…"}}` | Starts the client on a page with that hash; its socket (a fake one, in the runner) opens. |
| `{"expectSent": {…}}` | The next message the client sends must match. The browser's keep-alives are left out: the runner removes the event `7b32e816a191:0:2` from every message (a client may send it at any time, alone or merged with other requests), and skips a message left with nothing but its `requestId`. For the same reason a client fixture captures request ids instead of naming them. |
| `{"expectNothingSent": true}` | The client sends nothing (other than keep-alives) for a while. |
| `{"receive": {…}}` | The host's message arrives. |
| `{"setField": {"vsid", "name", "value"}}` | The user typed in a view's form. |
| `{"fire": {"vsid", "event"}}` | The user fired a view's event. |
| `{"expectScope": {"vsid", "scope": {…}}}` | The client's scope of that view must match. |
| `{"expectNoView": "<vsid>"}` | The client has no view of that vsid (it was dropped). |
| `{"expectOutdated": true}` | The client knows the host refused its version. |

### Matching

- An expected **object** matches when each of its fields matches the actual one; other actual fields are allowed. `"$absent"` as a value means the field must not be there.
- `"$any"` matches any present value; `"$string"` and `"$number"`, any of that type.
- `"$capture:<name>"` matches any value and keeps it; `${<name>}` later — in a value, a key, or inside a string (`"${counter}:1"`) — stands for it, in expectations and in what is sent.
- **Arrays** match element by element, with the same length; except `scopes`, matched by `id` (each expected scope against the actual one with the same id, and the same number of scopes), and `releasedViews`, matched as a set.
- In what a host fixture sends, `"$clientKey"` stands for a new P-256 public key the runner makes; in what a client fixture receives, `"$serverKey"` does the same for the host's.
