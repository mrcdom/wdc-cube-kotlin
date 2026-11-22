# React Architecture — Remote View with Server-Side Presenters

## Contents

- [Overview](#overview)
- [Comparison: Local View vs Remote View](#comparison-local-view-vs-remote-view)
- [Module Structure](#module-structure)
- [Communication Cycle](#communication-cycle)
- [Session Establishment](#session-establishment)
- [WebSocket Protocol](#websocket-protocol)
- [Skeleton — The Bridge Between Presenter and Client](#skeleton--the-bridge-between-presenter-and-client)
- [State Synchronization](#state-synchronization)
- [Security](#security)
- [Session Management and Lifecycle](#session-management-and-lifecycle)
- [Advantages of the Remote View](#advantages-of-the-remote-view)
- [Ideal Use Cases](#ideal-use-cases)
- [Complete Example: Login Flow](#complete-example-login-flow)

---

## Overview

The React architecture implements a **remote view** in which the presentation layer (presenters) runs entirely on the server. The React client is responsible only for **rendering the interface**, with no business or presentation logic. All user interaction is sent to the server over WebSocket, and the server responds with the new state serialized as JSON.

```mermaid
graph LR
    subgraph "Browser (Client)"
        REACT["React + MUI<br/>(pure rendering)"]
    end

    subgraph "Server (JVM)"
        SKEL["Skeleton<br/>(bridge view)"]
        PRES["Presenters<br/>(presentation logic)"]
        REPO["Repositories<br/>(JDBC data access)"]
        DB[(H2 Database)]
    end

    REACT <-->|"WebSocket<br/>bidirectional JSON"| SKEL
    SKEL <--> PRES
    PRES <--> REPO
    REPO <--> DB
```

This contrasts with the Compose Multiplatform architecture, where the presenters run on the client and access data over REST:

```mermaid
graph LR
    subgraph "Client (Desktop/iOS/Web/Android)"
        COMP["Compose UI"]
        PRES2["Presenters"]
        REST_C["REST Client"]
    end

    subgraph "Server (JVM)"
        REST_S["REST API"]
        REPO2["Repositories"]
        DB2[(H2 Database)]
    end

    COMP <--> PRES2
    PRES2 <--> REST_C
    REST_C <-->|"HTTP/REST"| REST_S
    REST_S <--> REPO2
    REPO2 <--> DB2
```

---

## Comparison: Local View vs Remote View

| Aspect | Compose (Local View) | React (Remote View) |
|---------|---------------------|---------------------|
| **Presenter execution** | On the client | On the server |
| **Data access** | Via REST (network) | Direct JDBC (in-process) |
| **Data latency** | HTTP round trip | Zero (same JVM) |
| **Session state** | On the device | On the server |
| **Client logic** | Presenters + UI | UI only |
| **Protocol** | HTTP/REST | WebSocket |
| **Data security** | Exposed API | Data never leaves the server |
| **Scalability** | Processing on the client | Processing on the server |
| **Offline operation** | Possible | Requires a connection |

---

## Module Structure

```mermaid
graph TB
    subgraph "Client (TypeScript)"
        RC["react.client<br/>React + MUI + Parcel"]
    end

    subgraph "Server (Kotlin/JVM)"
        BACK["backend<br/>Javalin HTTP/WS"]
        SKEL["react.skeleton<br/>View Impls + Dispatcher"]
        PRES["presentation<br/>Presenters (KMP commonMain)"]
        PERS["persistence<br/>Repositories jOOQ"]
        PERSR["persistence.rest<br/>REST API (for Compose)"]
        DOM["domain<br/>Entities + Interfaces"]
        CUBE["framework-cube<br/>MVP Engine"]
    end

    RC <-->|WebSocket| BACK
    BACK --> SKEL
    BACK --> PERSR
    SKEL --> PRES
    PRES --> DOM
    PRES --> PERS
    PERS --> DOM
    SKEL --> CUBE
    PRES --> CUBE
```

| Module | Role |
|--------|-------|
| **react.client** | Pure rendering in React/TypeScript with Material UI. Receives JSON states and sends user events. Bundled with Parcel. |
| **react.skeleton** | Server-side bridge: each `*ReactViewImpl` translates client events into presenter calls and serializes the ViewState to JSON. |
| **backend** | Javalin server that hosts the WebSocket (`/dispatcher/{id}`), serves the React static files, and exposes the REST API for Compose clients. |
| **presentation** | Shared presenters (KMP commonMain) — the same code used by both React and Compose. |
| **persistence** | jOOQ implementation of the repositories (H2 or PostgreSQL) — used directly by the presenters in the React architecture. |
| **persistence.rest** | REST endpoints — used by Compose clients via `persistence.client`. |

---

## Communication Cycle

```mermaid
sequenceDiagram
    participant Browser as React Client
    participant WS as WebSocket
    participant Dispatch as DispatchPhaseBhv
    participant ViewImpl as LoginReactViewImpl
    participant Presenter as LoginPresenter
    participant Repo as UserRepository (JDBC)
    participant Response as ResponsePhaseBhv

    Browser->>WS: JSON with event and formData
    WS->>Dispatch: parses request
    Dispatch->>ViewImpl: syncClientToServer(formData)
    ViewImpl->>Presenter: state.userName = formData.userName
    Dispatch->>ViewImpl: submit(eventCode=1, ...)
    ViewImpl->>Presenter: onEnter()
    Presenter->>Repo: findByCredentials(user, pass)
    Note over Repo: Direct JDBC access<br/>no network round trip
    Repo-->>Presenter: Subject
    Presenter->>ViewImpl: update() via markDirty
    Response->>Presenter: commitComputedState()
    Response->>ViewImpl: writeState(json)
    ViewImpl-->>WS: JSON with updated states
    WS-->>Browser: states of the dirty views
    Browser->>Browser: applies states and re-renders
```

The whole cycle happens in a single WebSocket exchange — the client sends the event, the server processes it and responds with the new state. There are no intermediate REST calls.

---

## Session Establishment

The session is established in two steps with a cryptographic key exchange:

```mermaid
sequenceDiagram
    participant Browser as Browser
    participant Server as Server

    Browser->>Server: GET /index.html
    Server->>Server: Generates appId = random.HMAC(random)
    Server->>Server: Generates RSA key pair (pub/priv)
    Server-->>Browser: Cookies: app_id, app_skey (RSA pub)

    Browser->>Browser: Generates random AES-256-GCM password
    Browser->>Browser: Encrypts AES password with RSA pub
    Browser->>Browser: Sets app_signature cookie

    Browser->>Server: WebSocket /dispatcher/appId
    Browser->>Server: ping with secret=app_signature
    Server->>Server: Decrypts AES password with RSA priv
    Server->>Server: Creates ReactRootPresenter
    Server-->>Browser: Initial state (views)
```

The `appId` is self-signed: `{random}.{HMAC(random)}` — this prevents forging session IDs.

---

## WebSocket Protocol

### Client → Server (User Event)

```json
{
  "requestId": 42,
  "event": ["c677cda52d14:1:1"],
  "c677cda52d14:1": {
    "userName": "admin",
    "password": "<AES-GCM encrypted, Base64>"
  }
}
```

- **`event`**: list of `vsid:eventCode` (which view and which action)
- **`vsid`**: view instance identifier (`hexVID:instanceNumber`)
- **Form data**: indexed by `vsid`, sensitive fields encrypted

### Server → Client (State Response)

```json
{
  "requestId": 42,
  "uri": "/home",
  "states": [
    {
      "id": "473dbdd7a36a:3",
      "nickName": "Admin",
      "cartItemCount": 0,
      "productsPanelViewId": "a1b2c3d4e5f6:4",
      "purchasesPanelViewId": "b3c4d5e6f7a8:5"
    },
    {
      "id": "a1b2c3d4e5f6:4",
      "products": [...]
    }
  ]
}
```

- **`states`**: only the views that changed (dirty tracking)
- **`uri`**: new navigation fragment (for the browser history)
- **References between views**: `*ViewId` fields point to other views

---

## Skeleton — The Bridge Between Presenter and Client

Each presenter has a matching `*ReactViewImpl` in the skeleton module. This class implements `GenericViewImpl` and does three things:

### 1. Synchronizes form data (client → server)

```kotlin
override fun syncClientToServer(formData: Map<String, Any?>) {
    val state = presenter.state
    if (formData.containsKey("userName")) {
        state.userName = CoerceUtils.asString(formData["userName"])
    }
    if (formData.containsKey("password")) {
        // Sensitive data is decrypted with AES
        state.password = app.getDataSecurity()
            .b64Decipher(CoerceUtils.asString(formData["password"]) ?: "")
    }
}
```

### 2. Dispatches events to the presenter

```kotlin
override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
    if (eventCode == 1) {
        presenter.onEnter()  // Maps the integer code to a presenter method
    }
}
```

### 3. Serializes the state to JSON

```kotlin
override fun writeState(json: ExtensibleObjectOutput) {
    presenter.state.write(instanceId(), json)
}
```

The mapping between hexadecimal View IDs and views is fixed and shared between client and server:

| View | Hex VID | Client (TSX) | Server (Kotlin) |
|------|---------|---------------|-------------------|
| Browser | `7b32e816a191` | BrowserView.tsx | BrowserReactViewImpl.kt |
| Root | `f2d345c4a610` | RootView.tsx | RootReactViewImpl.kt |
| Login | `c677cda52d14` | LoginView.tsx | LoginReactViewImpl.kt |
| Home | `473dbdd7a36a` | HomeView.tsx | HomeReactViewImpl.kt |
| Cart | `7eb485e5f843` | CartView.tsx | CartReactViewImpl.kt |
| Product | `48b693f67410` | ProductView.tsx | ProductReactViewImpl.kt |
| Receipt | `e8d0bd8ae3bc` | ReceiptView.tsx | ReceiptReactViewImpl.kt |
| ProductsPanel | `a1b2c3d4e5f6` | ProductsPanel.tsx | ProductsPanelReactViewImpl.kt |
| PurchasesPanel | `b3c4d5e6f7a8` | PurchasesPanel.tsx | PurchasesPanelReactViewImpl.kt |

---

## State Synchronization

### Dirty Tracking

When a presenter calls `view.update()`, the view is marked "dirty" in `ReactRootPresenter`. In the response, only dirty views are serialized:

```mermaid
graph TB
    P["Presenter calls update()"]
    P --> MD["app.markDirty(view)"]
    MD --> DM["dirtyViewMap stores view"]

    subgraph "In the response"
        DM --> IT["Iterates dirtyViewMap"]
        IT --> WS["writeState(json) for each view"]
        WS --> CL["Removes from dirtyViewMap"]
    end
```

### Server Push

When a view is marked dirty outside the request/response cycle (for example, by an asynchronous operation), the server schedules an automatic push over WebSocket after 200ms:

```kotlin
fun markDirty(view: GenericViewImpl) {
    dirtyViewMap[view.instanceId()] = view
    if (!requestInProgress && wsSession != null) {
        schedulePush()  // Push with a 200ms delay
    }
}
```

### View Garbage Collection

The client keeps a reference system for views. When a `*ViewId` field changes in a parent view's state, the previous child view is released automatically after 4 seconds of inactivity.

---

## Security

### Transport Layer

```mermaid
graph LR
    subgraph "Handshake"
        A["Server generates RSA key pair"] --> B["Sends pub key via cookie"]
        B --> C["Client generates AES-256-GCM password"]
        C --> D["Encrypts password with RSA pub"]
        D --> E["Sends via app_signature cookie"]
        E --> F["Server decrypts with RSA priv"]
    end

    subgraph "Session"
        F --> G["Sensitive data encrypted<br/>with shared AES-GCM"]
    end
```

- **Session ID**: self-signed with HMAC — prevents forging
- **Sensitive fields**: encrypted end to end with AES-256-GCM (e.g., passwords)
- **Navigation**: URLs signed with a hash to prevent parameter tampering

### Security Inherent to the Architecture

In the remote view, repository data **never travels directly to the client**. The presenter on the server decides exactly which fields to expose in the ViewState. This eliminates entire classes of vulnerabilities:

- There is no REST API exposed to the React client — no endpoint to attack
- There are no JWT tokens on the client — authentication is managed in the server session
- Sensitive data never leaves the JVM — the presenter filters it before serializing

---

## Session Management and Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: GET /index.html
    Created --> Connected: WebSocket opened
    Connected --> Active: Initial ping
    Active --> Active: User events
    Active --> Disconnected: WebSocket closed
    Disconnected --> Connected: Reconnection
    Disconnected --> Expired: 3-minute timeout
    Active --> Expired: 3-minute timeout without WS
    Expired --> [*]: release()
```

- **Creation**: when `/index.html` is accessed, session cookies are set
- **Lifetime**: 3 minutes (`DEFAULT_TIME_SPAN`) — extended on every interaction
- **Reconnection**: if the WebSocket drops, the client reconnects and finds the same session (if it has not expired)
- **Cleanup**: expired sessions without an active WebSocket are removed periodically
- **Logout/Disconnection**: an unauthenticated session is released immediately when the WebSocket closes

---

## Advantages of the Remote View

### 1. Zero Data Latency

The presenters access JDBC repositories directly in the same JVM. There is no HTTP serialization, JSON parsing, or network round trip for each database query. An operation that in the Compose model requires:

```
Client → HTTP Request → REST Endpoint → Repository → DB → Response → HTTP Response → Client
```

In the React model is reduced to:

```
Presenter → Repository → DB → Presenter
```

### 2. Minimal Attack Surface

- **No REST API exposed** to the client (the REST endpoints exist only for Compose clients)
- **No sensitive data on the client** — passwords, tokens, and payment data never reach the browser
- **No business logic on the client** — reverse engineering the logic is impossible
- **Server-side session** — no exposed JWT or authentication cookies

### 3. Ultra-Light Client

The React client contains only:
- Rendering components (TSX)
- WebSocket communication logic
- Encryption of sensitive fields

It does not contain: business validations, data access, complex state management, routes, or navigation logic.

### 4. Transactional Consistency

Because presenters and repositories share the same JVM, regular JDBC transactions spanning multiple data operations can be used — something hard to coordinate when data travels over REST.

### 5. Instant Updates

Changes to the presentation logic take effect immediately on server deployment, with no need to update clients. The React client is cache-friendly and rarely changes.

### 6. Native Server Push

The bidirectional WebSocket lets the server send state updates to the client without the client having to ask. This enables real-time notifications, dashboard updates, and synchronization across tabs/users.

---

## Ideal Use Cases

The remote view architecture is especially suited for:

| Use Case | Why |
|-------------|---------|
| **Corporate/internal applications** | Controlled network, critical security, sensitive data |
| **Administrative systems (back office)** | Many CRUD screens, complex logic, few concurrent users |
| **Financial applications** | Data never leaves the server, simplified auditing |
| **Systems with regulated data (LGPD/GDPR)** | Personal data stays on the server, client is stateless |
| **Real-time dashboards** | Server push over WebSocket is native |
| **Applications that require strong consistency** | JDBC transactions coordinated on the server |
| **Low-bandwidth environments** | Only state JSON is transferred, not raw data |

### When NOT to use it

| Scenario | Reason |
|---------|--------|
| **Offline operation** | Requires a permanent WebSocket connection |
| **Massive scale (millions of users)** | Each session consumes server memory |
| **Native mobile apps** | Compose Multiplatform is a better fit |
| **Games or complex animations** | WebSocket latency may be noticeable |

---

## Complete Example: Login Flow

```mermaid
sequenceDiagram
    participant User as User
    participant React as React Client
    participant WS as WebSocket
    participant Disp as DispatchPhaseBhv
    participant LView as LoginReactViewImpl
    participant LPres as LoginPresenter
    participant URepo as UserRepository (JDBC)
    participant Resp as ResponsePhaseBhv

    User->>React: Fills in user name and password and clicks Sign in

    Note over React: Encrypts password with AES-GCM

    React->>WS: event=c677cda52d14:1:1<br/>formData userName=admin<br/>password=AES(password)

    WS->>Disp: parses request JSON

    Disp->>LView: syncClientToServer(formData)
    LView->>LPres: state.userName = admin
    LView->>LView: state.password = decipher(AES)
    LView->>LPres: state.password = real_password

    Disp->>LView: submit(eventCode=1, ...)
    LView->>LPres: onEnter()

    LPres->>URepo: findByCredentials(admin, password)
    Note over URepo: Direct SQL on H2<br/>no intermediate REST
    URepo-->>LPres: Subject(id=1, nick=Admin)

    LPres->>LPres: app.subject = subject
    LPres->>LPres: Navigates to /home

    Note over LPres: Creates HomePresenter<br/>ProductsPanelPresenter<br/>PurchasesPanelPresenter

    Resp->>LPres: commitComputedState()
    Resp->>Resp: Collects dirty views

    Resp-->>WS: JSON with states of Home,<br/>ProductsPanel, PurchasesPanel

    WS-->>React: uri=/home + states=[...]
    React->>React: Updates browser history
    React->>React: Renders Home screen

    User->>User: Sees the main screen with products
```

Note that throughout the flow:
1. The **password never travels in clear text** — it is encrypted with AES-GCM on the client and decrypted on the server
2. **Database access is direct** — `UserRepository` runs SQL in the same JVM as the presenter
3. The **client does not know how to navigate** — the server decides that the next screen is Home and sends the corresponding states
4. Only **views that changed** are included in the response (dirty tracking)
