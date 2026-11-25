# 0010. Server-driven remote views, independent of UI and server technology

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

Besides running presenters on the device, the architecture offers a **remote view**: presenters and data stay on the server, and the browser only draws scopes and sends events. The first version lived inside the WDC Store, tied to React on the client and to Javalin on the server, with protocol code mixed with configuration.

## Decision

The protocol is a framework concern, with each side split from its technology bindings:

- **Server:** `framework-cube-remote-backend` (sessions, views, dispatch, signed history, no web server), and `framework-cube-remote-backend-javalin` (WebSocket routes, the page that plants the session cookie).
- **Client:** `framework-cube-remote-bridge` (Kotlin/JS) and `@wdc/remote-bridge` (TypeScript), both without UI; `framework-cube-remote-bridge-react` and `@wdc/remote-bridge-react` add the React bindings.

The protocol over WebSocket:

- Each scope class has a fixed view id (`VID`, in its companion object); a view instance is `vid:number`.
- The client sends events as `vsid:eventCode` with the form data of each view (sensitive fields ciphered, see [0011](0011-remote-channel-security.md)).
- The server answers with `scopes` — only the views that changed, each written whole (every field that is sent), with `…ViewId`/`…ViewIds` linking to child views — then `releasedViews`, the views whose scopes left the screen, which the client drops.
- The address goes back signed, for the browser history.

The application on the server implements `RemoteApplication` and holds a `RemoteApplicationSupport`; the WDC Store's `RemoteRootPresenter` is the complete example. Two clients implement the same views, in TypeScript and in Kotlin/JS.

## Consequences

- A new UI technology for the remote view only needs a client binding; the server does not change. A new web server only needs a binding of the backend.
- The browser holds no data and no business logic, and the server holds the state of each session: sessions need affinity to an instance, and memory grows with users (see [architecture-react.md](../architecture-react.md)).
- A scope that goes to the remote view declares its `VID`; the remote host catalog reads it from there.

## Alternatives considered

- **Keep it inside the application.** Rejected: most of it was generic, and other applications could not reuse it.
