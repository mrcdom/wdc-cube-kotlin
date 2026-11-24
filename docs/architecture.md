# Project Architecture — Overview

## Contents

- [Introduction](#introduction)
- [Architectural Principles](#architectural-principles)
- [Architecture Layers](#architecture-layers)
- [Module Diagram](#module-diagram)
- [Multiplatform Strategy](#multiplatform-strategy)
- [Two View Modalities](#two-view-modalities)
  - [Local View — Compose Multiplatform](#local-view--compose-multiplatform)
  - [Remote View — React + WebSocket](#remote-view--react--websocket)
- [Presentation Layer](#presentation-layer)
- [Domain Layer](#domain-layer)
- [Persistence Layer](#persistence-layer)
- [Backend](#backend)
- [Compose Multiplatform Integration](#compose-multiplatform-integration)
- [Error Handling](#error-handling)
- [Per-Platform Initialization](#per-platform-initialization)
- [Complete Example: Login Flow](#complete-example-login-flow)
- [Detailed Documentation](#detailed-documentation)
- [Glossary](#glossary)

---

## Introduction

This project is a demo application (**Storefront Demo**) built on the **Cube MVP** framework in **Kotlin Multiplatform**. Its goal is to show how a single presentation codebase can be shared across multiple platforms and distinct view technologies.

The architecture is centered on three fundamental decisions:

1. **State management with mutable objects** — the Cube framework keeps what each presenter shows in a mutable `Scope`, eliminating the ceremony of immutable copies and reducers
2. **Full decoupling between presentation and UI technology** — presenters are plain Kotlin classes with no dependency on a UI framework; the rendering technology is a project choice
3. **Transactional navigation** — `CubeNavigation` treats each screen transition as an atomic transaction with commit/rollback

---

## Architectural Principles

| Principle | Description |
|-----------|-----------|
| **Separation of concerns** | View renders, Presenter controls, Domain models, Persistence accesses data |
| **UI-agnostic presentation** | Presenters know nothing about Compose, React or any other UI technology |
| **Simple mutable state** | A presenter's scope is a plain object with mutable properties — no reducers, no events, no unidirectional flow |
| **Navigation as a transaction** | Each navigation creates/reuses/discards presenters atomically |
| **True multiplatform** | The same presentation code runs on JVM, Android, iOS and WebAssembly |
| **State serialization** | Each screen persists and restores its state via URL/parameters (deep-linking) |

---

## Architecture Layers

```mermaid
graph TB
    subgraph "View Layer"
        CV["Compose Multiplatform<br/>(local view)"]
        RV["React + MUI<br/>(remote view)"]
    end

    subgraph "Presentation Layer (Plain Kotlin)"
        LP[LoginPresenter]
        HP[HomePresenter]
        PP[ProductPresenter]
        LS[LoginScope]
        HS[HomeScope]
        PS[ProductScope]
    end

    subgraph "Domain Layer"
        SVC[Services]
        REPO[Repositories]
        MODEL[Domain Models]
    end

    subgraph "Infrastructure"
        HTTP[HTTP Transport]
        JSON[JSON Serialization]
        CRYPTO[Crypto Provider]
        DB[H2 Database]
    end

    CV --> LP
    CV --> HP
    CV --> PP
    RV -.->|WebSocket| LP
    RV -.->|WebSocket| HP
    RV -.->|WebSocket| PP
    LP --> LS
    HP --> HS
    PP --> PS
    LP --> SVC
    HP --> SVC
    PP --> SVC
    SVC --> REPO
    REPO --> HTTP
    HTTP --> JSON
    HTTP --> CRYPTO
    REPO --> DB
```

---

## Module Diagram

```mermaid
graph TB
    subgraph "Per-Platform Executables"
        DESK[":view-compose-desktop<br/>(JVM)"]
        ANDR[":view-compose-android<br/>(Android)"]
        WEB[":view-compose-web<br/>(wasmJs)"]
        IOS[":view-compose-ios<br/>(iOS)"]
    end

    subgraph "Shared View"
        VC[":view-compose<br/>(Compose Multiplatform)"]
    end

    subgraph "Presentation"
        PRES[":shopping-presentation<br/>(Plain Kotlin)"]
    end

    subgraph "Domain"
        DOM[":shopping-domain<br/>(Models & Interfaces)"]
        PCLI[":shopping-persistence-client<br/>(REST Client)"]
    end

    subgraph "Framework"
        CUBE[":framework-cube<br/>(MVP Core)"]
        COMM[":framework-commons<br/>(Utilities)"]
    end

    subgraph "Backend"
        BACK[":backend<br/>(Javalin Server)"]
    end

    DESK --> VC
    ANDR --> VC
    WEB --> VC
    IOS --> VC
    VC --> PRES
    PRES --> DOM
    PRES --> CUBE
    PCLI --> DOM
    PCLI --> COMM
    CUBE --> COMM
    DOM --> COMM
    BACK -.->|HTTP/REST| PCLI

    style DESK fill:#4a90d9,color:#fff
    style ANDR fill:#4a90d9,color:#fff
    style WEB fill:#4a90d9,color:#fff
    style IOS fill:#4a90d9,color:#fff
    style VC fill:#7cb342,color:#fff
    style PRES fill:#f9a825,color:#000
    style DOM fill:#ef6c00,color:#fff
    style PCLI fill:#ef6c00,color:#fff
    style CUBE fill:#ab47bc,color:#fff
    style COMM fill:#ab47bc,color:#fff
    style BACK fill:#78909c,color:#fff
```

| Module | Responsibility |
|--------|-----------------|
| `:framework-cube` | MVP engine — transactional navigation, presenter lifecycle, Places, Intent |
| `:framework-commons` | Shared utilities — logging, JSON, HTTP, crypto, schedulers |
| `:shopping-presentation` | Presenters and their scopes — pure presentation logic (no UI) |
| `:shopping-domain` | Domain models, repository interfaces, services |
| `:shopping-persistence-client` | REST implementation of the repositories and of the transaction service (HTTP client) |
| `:shopping-persistence` | jOOQ implementation of the repositories (direct database access) |
| `:view-compose` | Shared Compose Multiplatform views |
| `:view-compose-desktop` | JVM Desktop entry point |
| `:view-compose-android` | Android entry point |
| `:view-compose-web` | wasmJs (WebAssembly) entry point |
| `:view-compose-ios` | iOS entry point (via Xcode) |
| `:shopping-backend` | Javalin server — REST API, WebSocket, H2 |
| `:shopping-scripts` | Database migrations and scripts |

---

## Multiplatform Strategy

### Supported Platforms

```mermaid
graph TB
    subgraph "Shared Code (commonMain)"
        FW[Framework Cube + Commons]
        PRES[Presenters + Scopes]
        DOM[Domain Models + Services]
        VIEW[Compose Views]
    end

    subgraph "Platforms"
        JVM["Desktop (JVM)<br/>Gson, OkHttp, JCE"]
        AND["Android<br/>Gson, OkHttp, JCE"]
        WASM["Web (wasmJs)<br/>Kotlinx-serialization<br/>Fetch API, Web Crypto"]
        IOSN["iOS (Native)<br/>NSJSONSerialization<br/>URLSession, CommonCrypto"]
    end

    FW --> JVM
    FW --> AND
    FW --> WASM
    FW --> IOSN
    PRES --> JVM
    PRES --> AND
    PRES --> WASM
    PRES --> IOSN
    DOM --> JVM
    DOM --> AND
    DOM --> WASM
    DOM --> IOSN
    VIEW --> JVM
    VIEW --> AND
    VIEW --> WASM
    VIEW --> IOSN

    style FW fill:#ab47bc,color:#fff
    style PRES fill:#f9a825,color:#000
    style DOM fill:#ef6c00,color:#fff
    style VIEW fill:#7cb342,color:#fff
    style JVM fill:#4a90d9,color:#fff
    style AND fill:#4a90d9,color:#fff
    style WASM fill:#4a90d9,color:#fff
    style IOSN fill:#4a90d9,color:#fff
```

### Per-Platform Implementations

| Component          | JVM / Android         | wasmJs                  | iOS                     |
|--------------------|-----------------------|-------------------------|-------------------------|
| JSON               | Gson                  | Kotlinx-serialization   | NSJSONSerialization     |
| HTTP               | OkHttp                | Fetch API               | URLSession              |
| Crypto             | JCE                   | Web Crypto API          | CommonCrypto            |
| Scheduler          | ScheduledThreadPool   | setTimeout/setInterval  | dispatch_queue          |
| ThreadLocal        | java.lang.ThreadLocal | Global variable         | pthread_key             |

---

## Two View Modalities

The project demonstrates two distinct ways of implementing the view layer, both sharing the **same presentation layer**.

### Local View — Compose Multiplatform

Presenters run on the **client**. The Compose view renders the local presenter's `scope` directly. Communication with the backend goes through REST.

```mermaid
graph LR
    subgraph "Client (all platforms)"
        VIEW["Compose View"] --> PRES["Presenter"]
        PRES --> SCOPE["Scope"]
        PRES --> SVC["Service"]
    end
    SVC -->|HTTP/REST| BACK["Backend"]
```

### Remote View — React + WebSocket

Presenters run on the **server**. The React client is responsible only for rendering. Every user interaction is sent to the server over WebSocket, and the server responds with the new scopes serialized as JSON.

```mermaid
graph LR
    subgraph "Browser"
        REACT["React + MUI<br/>(pure rendering)"]
    end
    subgraph "Server"
        SKEL["Skeleton"] --> PRES["Presenter"]
        PRES --> SCOPE["Scope"]
    end
    REACT <-->|WebSocket| SKEL
```

See [architecture-react.md](architecture-react.md) for full details of this modality.

---

## Presentation Layer

**Presenters** encapsulate all of a screen's business logic. They are plain Kotlin classes with no dependency on a UI framework:

```kotlin
class LoginPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    val scope = bind(LoginScope()) // a field change repaints the view: no update() calls
    private val loginService = LoginService()

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        // read on every call: a reused presenter goes where its parent puts it now
        ownerSlot = LoginKeys(app, intent).parentSlot
        if (initialization) {
            view = createView?.invoke(this)
        }
        ownerSlot?.setView(view!!)
        return false
    }

    fun onEnter() {
        scope.loading = true

        val subject = loginService.fetchSubject(scope.userName ?: "", scope.password ?: "")
        scope.loading = false

        if (subject?.id != null) {
            app.subject = subject
            HomeKeys(app).flip()
        } else {
            scope.errorMessage = "Username or password not recognized!"
        }
    }
}
```

The **scope** is what a presenter shows: a simple mutable object holding the data its view paints. Its fields are observed: assigning one a different value asks for a repaint; it also writes itself as JSON for the remote view ([details](architecture-cube.md#scope)):

```kotlin
class LoginScope : Scope() {
    var userName: String? by field(null)
    var password: String? by field(null)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
    var loading: Boolean by field(false)

    override fun write(instanceId: String, json: ExtensibleObjectOutput) { /* the fields the remote view needs */ }
}
```

Hierarchical navigation is done through **Places** and **CubeNavigation**. Each route is a sequence of steps. `ROOT` (`CubeTreePlace.ROOT`) is the application itself: it is not a step, but `RootPresenter.applyParameters` runs before the first step of every navigation (a navigation to the root alone goes on to the home):

| Route                     | Steps                            |
|---------------------------|----------------------------------|
| Login                     | `ROOT → LOGIN`                   |
| Home (product list)       | `ROOT → HOME`                    |
| Product detail            | `ROOT → HOME → PRODUCT`          |
| Cart                      | `ROOT → HOME → CART`             |
| Purchase receipt          | `ROOT → HOME → RECEIPT`          |

```mermaid
graph TD
    ROOT --> LOGIN
    ROOT --> HOME
    HOME --> CART
    HOME --> PRODUCT
    HOME --> RECEIPT

    style ROOT fill:#4a90d9,color:#fff
    style LOGIN fill:#7cb342,color:#fff
    style HOME fill:#7cb342,color:#fff
    style CART fill:#f9a825,color:#000
    style PRODUCT fill:#f9a825,color:#000
    style RECEIPT fill:#f9a825,color:#000
```

The mechanism that connects parent and child views uses **ViewSlots** — functional callbacks that fully decouple the parent from the child:

```mermaid
sequenceDiagram
    participant Root as RootPresenter
    participant Intent as CubeIntent
    participant Home as HomePresenter
    participant RootView as RootView

    Note over Root: applyParameters(deepest=false)
    Root->>Intent: parentSlot = contentSlot (RootKeys)

    Note over Home: applyParameters(deepest=true)
    Home->>Intent: parentSlot (HomeKeys)
    Intent-->>Home: contentSlot
    Home->>Root: contentSlot.setView(homeView)
    Note over RootView: Recomposes and renders HomeView
```

For full details of the transactional navigation mechanism (commit, rollback, interruption, presenter migration), see [architecture-cube.md](architecture-cube.md).

---

## Domain Layer

The domain layer contains:

- **Models** — business entities (`Product`, `Purchase`, `Subject`, etc.)
- **Repository interfaces** — contracts with search criteria (Query Object Pattern)
- **Services** — business operations that coordinate repositories

Models and interfaces are shared across all platforms (`commonMain`). Repository implementations vary: REST client for local views, jOOQ for the backend.

See [architecture-persistence.md](architecture-persistence.md) for details of the criteria-based repository pattern.

---

## Persistence Layer

| Implementation | Module | Use |
|---------------|--------|-----|
| **REST Client** | `:shopping-persistence-client` | Local views (Compose) — calls the backend over HTTP |
| **jOOQ** | `:shopping-persistence` | Backend — direct database access (H2 or PostgreSQL) |

Both implement the same contract. The REST implementation sends the criteria and the projection as JSON, using the same codec the server uses to read them; the jOOQ implementation translates them into a single SQL query with bind parameters.

---

## Backend

HTTP server based on **Javalin** (Java 21) with:

- **REST API** — endpoints for products, cart, purchases, authentication
- **WebSocket** — support for the remote view modality (React)
- **H2 Database** — embedded database with versioned migrations
- **JWT** — authentication with access token + refresh token, sessions persisted in the database

---

## Compose Multiplatform Integration

Cube is integrated with Compose through the `ComposeCubeView` class, which solves three fundamental problems:

- **Recomposition** — a `revision` counter (`MutableState<Int>`) is incremented when the presenter calls `update()`, triggering Compose's automatic recomposition
- **Threading** — `safeCall` dispatches presenter actions to a coroutine with `limitedParallelism(1)`, keeping the UI thread free and guaranteeing serial execution
- **Hierarchical composition** — `RenderSlot(view)` renders a child `CubeView` inside a parent view, with no type coupling

The **view factories** pattern lets each platform register its Compose views in the entry point, keeping presenters free of UI dependencies.

See [architecture-cube-compose.md](architecture-cube-compose.md) for full details: the ComposeCubeView bridge, revision counter, safeCall, RenderSlot, factory registration, per-platform initialization, deep-linking and the loading pattern.

---

## Error Handling

### Exception Hierarchy

```mermaid
graph TB
    EX[Exception]
    EX --> OE[OfflineException<br/>Server unavailable]
    EX --> ICE[InvalidCartItemException<br/>Invalid cart item]
    EX --> BE[BusinessException<br/>Business rule violated]
    EX --> PNF[ProductNotFoundException<br/>Product not found]
    EX --> PUNF[PurchaseNotFoundException<br/>Purchase not found]
    EX --> WPE[WrongParametersException<br/>Invalid parameters]

    style OE fill:#ef5350,color:#fff
    style ICE fill:#ef5350,color:#fff
    style BE fill:#ffa726,color:#000
    style PNF fill:#ffa726,color:#000
    style PUNF fill:#ffa726,color:#000
    style WP fill:#78909c,color:#fff
    style WPE fill:#78909c,color:#fff
```

### Handling Strategy

Errors are handled at **two levels**:

1. **In the Presenter** — expected errors (failed login, database offline) are handled with friendly messages in the presenter's scope
2. **In safeCall** — unexpected errors are caught and delegated to `RootPresenter.alertUnexpectedError()`, which sets the root view's `scope.errorMessage`

```kotlin
// Level 1: Specific handling in the Presenter
fun onBuy() {
    try {
        val purchaseId = cart.commit(app.subject!!)
        ReceiptKeys(app).apply { this.purchaseId = purchaseId }.flip()
    } catch (caught: InvalidCartItemException) {
        scope.errorMessage = "Some products have a quantity of less than one item. Unable to purchase."
    } catch (caught: OfflineException) {
        scope.errorMessage = "The database is currently offline. Please wait a moment and try again."
    }
}

// Level 2: safeCall catches any unhandled exception
protected fun safeCall(action: () -> Unit) {
    presenterScope.launch {
        try {
            action()
        } catch (e: Exception) {
            app.alertUnexpectedError(LOG, "Unexpected error", e)
        }
    }
}
```

---

## Per-Platform Initialization

Each platform has an entry point that:

1. **Configures** platform-specific **dependencies** (JSON, HTTP, Crypto, Scheduler)
2. **Registers view factories** (binding Presenters to Compose Views; the root view's is `RootPresenter.createView`)
3. **Creates the application** and triggers the initial navigation
4. **Renders** using Compose

```mermaid
sequenceDiagram
    participant Main as Entry Point
    participant Platform as Platform Config
    participant App as RootPresenter
    participant Keys as RootKeys
    participant Home as HomePresenter
    participant Compose as Compose Runtime

    Main->>Platform: initializePlatform(baseUrl)
    Note over Platform: JSON, HTTP, Crypto,<br/>Scheduler, View Factories

    Main->>App: create RootPresenter()
    Main->>Keys: RootKeys(app).flip()
    Keys->>App: app.flip(CubeTreePlace.ROOT, intent) — no steps
    App->>App: applyParameters(init=true, deepest=true):<br/>creates the root view, restores the session
    App->>Home: HomeKeys(app, intent).flip() (redirect)
    Home->>Home: Checks subject
    Home->>Keys: LoginKeys(app, intent).flip() when there is none

    Main->>Compose: Renders (app.view as? RootView)
    Compose->>App: rootView.Render()
```

---

## Complete Example: Login Flow

The diagram below shows the complete flow from the click on the "Sign in" button to the display of Home with the products:

```mermaid
sequenceDiagram
    actor User as User
    participant LV as LoginView
    participant SC as safeCall
    participant LP as LoginPresenter
    participant LS as LoginService
    participant API as Backend REST
    participant Keys as HomeKeys
    participant Nav as CubeNavigation
    participant HP as HomePresenter
    participant HV as HomeView

    User->>LV: Clicks Sign in
    LV->>SC: safeCall - presenter.onEnter()
    Note over LV: UI thread released

    SC->>LP: onEnter() on background thread
    LP->>LP: scope.loading = true, update()
    Note over LV: Recomposes: spinner + disabled fields

    LP->>LS: fetchSubject(admin, ***)
    LS->>API: POST /auth/login
    API-->>LS: Subject id=1, nick=Admin
    LS-->>LP: Subject(id=1, nick=Admin)

    LP->>LP: scope.loading = false
    LP->>LP: app.subject = subject

    LP->>Keys: HomeKeys(app).flip()
    Keys->>Nav: navigate().step(HOME).execute()

    Nav->>Nav: app.applyParameters() — offers the content slot
    Nav->>HP: applyParameters(init=true, deepest=true)
    Note over HP: Creates CartManager,<br/>ProductsPanel, PurchasesPanel
    HP->>HP: Injects view into the Root slot

    Nav->>Nav: release LoginPresenter
    Nav->>Nav: commitComputedState()
    Nav->>Nav: updateHistory()

    Note over HV: Compose recomposes:<br/>renders Home with products
    HV->>User: Shows main screen
```

---

## Detailed Documentation

| Document | Content |
|-----------|----------|
| [architecture-cube.md](architecture-cube.md) | Cube framework — transactional navigation mechanism, presenter lifecycle, interruption and migration, commit/rollback, guarantees |
| [architecture-cube-compose.md](architecture-cube-compose.md) | Cube + Compose integration — ComposeCubeView, revision counter, safeCall, RenderSlot, view factories, per-platform initialization |
| [architecture-react.md](architecture-react.md) | Remote view modality — React + WebSocket, Skeletons, scope synchronization, security |
| [architecture-persistence.md](architecture-persistence.md) | Persistence layer — repositories, criteria, projection, transactions (local and remote), REST API |

---

## Glossary

| Term                 | Definition |
|----------------------|-----------|
| **Place**            | Navigation destination with id, name and presenter factory; declared by the keys class of the place (`CubeTreePlace`, child of the place of the keys it extends) |
| **CubeIntent**       | Container of parameters (serializable) and attributes (ephemeral) for a navigation transaction |
| **CubeViewSlot**     | Functional callback for injecting a child view into a parent view |
| **Presenter**        | Controller that encapsulates a screen's presentation logic (plain Kotlin, no UI dependency) |
| **Scope**            | What a presenter shows: a mutable object with the data its view paints, observed (a change asks for a repaint) and serializable to JSON |
| **ComposeCubeView**  | Bridge between CubeView and Jetpack Compose, with a revision counter mechanism |
| **safeCall**         | Serialized asynchronous dispatch (limitedParallelism=1) for presenter actions |
| **CubeNavigation**   | Atomic navigation transaction with commit, rollback and support for interruptions |
| **CubeApplication**  | Global container of active presenters and entry point for navigation |
| **Skeleton**         | Server-client bridge in the React modality — serializes each view's scope for WebSocket |
| **revision**         | MutableState counter that triggers Compose recomposition |
