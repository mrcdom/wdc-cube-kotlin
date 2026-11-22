# Cube + Compose Architecture — Integration with Compose Multiplatform

## Contents

- [Overview](#overview)
- [Bridge Layer — ComposeCubeView](#bridge-layer--composecubeview)
  - [Revision Counter](#revision-counter)
  - [safeCall — Serialized Asynchronous Execution](#safecall--serialized-asynchronous-execution)
  - [RenderSlot — View Composition](#renderslot--view-composition)
- [Registering View Factories](#registering-view-factories)
- [Complete Flow: From Action to Pixel](#complete-flow-from-action-to-pixel)
- [View Implementation Pattern](#view-implementation-pattern)
- [Slot Mechanism in Compose](#slot-mechanism-in-compose)
- [Per-Platform Initialization](#per-platform-initialization)
  - [Desktop (JVM)](#desktop-jvm)
  - [Web (wasmJs)](#web-wasmjs)
  - [Android](#android)
  - [iOS](#ios)
- [Deep-Linking and History (Web)](#deep-linking-and-history-web)
- [Loading Pattern](#loading-pattern)

---

## Overview

Cube integrates with Compose Multiplatform through a minimal **bridge layer** — the `ComposeCubeView` class. This layer solves three problems:

1. **Recomposition** — how to notify Compose that state has changed, without relying on `MutableState` in presenters
2. **Threading** — how to run presenter logic off the UI thread, keeping the UI responsive
3. **Hierarchical composition** — how to render child views inside parent views, without coupling

The presenter stays **pure Kotlin** — it knows nothing about Compose and imports no `androidx` package. The whole Compose dependency is concentrated in the view.

```mermaid
graph LR
    subgraph "Pure Kotlin (commonMain)"
        P[Presenter] --> S[ViewState]
        P --> CV[CubeView interface]
    end

    subgraph "Compose (view module)"
        CCV[ComposeCubeView] -.->|implements| CV
        CCV --> REV["revision: MutableState&lt;Int&gt;"]
        CCV --> SC[safeCall]
        CCV --> RND["@Composable Render()"]
    end

    P -->|"update()"| CCV
    CCV -->|"revision++"| REV
    REV -->|"recomposition"| RND
    RND -->|"reads"| S
```

---

## Bridge Layer — ComposeCubeView

### Revision Counter

`ComposeCubeView` implements `CubeView` and exposes a `MutableState<Int>` named `revision`. The presenter calls `update()`, which increments `revision.value`. Because `@Composable Render()` reads that value, Compose detects the change and schedules a recomposition.

```kotlin
abstract class ComposeCubeView(
    private val id: String,
    protected val app: ShoppingApplication
) : CubeView {

    val revision: MutableState<Int> = mutableIntStateOf(0)

    override fun update() {
        revision.value++  // Thread-safe, triggers recomposition
    }

    @Composable
    abstract fun Render()
}
```

**Why it works:**

```mermaid
sequenceDiagram
    participant BG as Background Thread
    participant P as Presenter
    participant V as ComposeCubeView
    participant REV as MutableState revision
    participant C as Compose Runtime
    participant UI as UI Thread

    BG->>P: action() via safeCall
    P->>P: state.field = newValue
    P->>V: update()
    V->>REV: revision.value++
    REV->>C: Snapshot change detected
    C->>UI: Schedule recomposition
    UI->>V: Render()
    V->>REV: val rev = revision.value
    Note over UI: Reads updated state and renders
```

1. Compose's `MutableState` is **thread-safe** — changes on any thread are detected by the snapshot system
2. Reading `revision.value` inside the `@Composable` creates an automatic **subscription**
3. When the value changes, Compose schedules a **recomposition** on the UI thread
4. Multiple calls to `update()` in the same frame are **coalesced** into a single recomposition

### safeCall — Serialized Asynchronous Execution

`safeCall` dispatches the presenter action to a **coroutine with parallelism limited to 1**:

```kotlin
protected fun safeCall(action: () -> Unit) {
    presenterScope.launch {
        try {
            action()
        } catch (e: Exception) {
            app.alertUnexpectedError(LOG, "Unexpected error in $id", e)
        }
    }
}

companion object {
    private val presenterScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1)
    )
}
```

This guarantees:

| Property | How |
|----------|-----|
| **Free UI thread** | The action runs in the background — the interface stays responsive |
| **Serial execution** | `limitedParallelism(1)` — state mutations never run concurrently |
| **Synchronous code in the presenter** | The presenter writes linear code, with no callbacks or suspending functions |
| **Captured errors** | Unhandled exceptions are delegated to `RootPresenter.alertUnexpectedError()` |

```mermaid
sequenceDiagram
    participant UI as UI Thread
    participant Q as Queue (limitedParallelism=1)
    participant BG as Background Thread

    UI->>Q: safeCall { onAddToCart() }
    UI->>Q: safeCall { onBuy() }
    Note over UI: UI free, both queued

    Q->>BG: onAddToCart()
    Note over BG: Runs to completion
    BG-->>Q: done

    Q->>BG: onBuy()
    Note over BG: Runs sequentially
    BG-->>Q: done
```

### RenderSlot — View Composition

The `RenderSlot` function turns a generic `CubeView` into a `@Composable` call:

```kotlin
@Composable
fun RenderSlot(view: CubeView) {
    (view as? ComposeCubeView)?.Render()
}
```

Parent views use it to render child views without knowing their concrete type. The parent view only knows that the `ViewState` holds a `CubeView`:

```kotlin
// Inside RootView.Render():
val contentView = state.contentView
if (contentView != null) {
    RenderSlot(contentView)
}
```

---

## Registering View Factories

Each presenter declares a static `createView` variable that accepts a factory function:

```kotlin
// In the presenter (pure Kotlin — no Compose dependency):
class LoginPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        var createView: ((LoginPresenter) -> CubeView)? = null
    }

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            view = createView?.invoke(this)  // ← calls the factory
        }
        // ...
    }
}
```

In each platform's **entry point**, the factories are registered, binding presenters to concrete views:

```kotlin
// At startup (e.g. Desktop Main.kt):
RootPresenter.createView     = { p -> RootView(p) }
LoginPresenter.createView    = { p -> LoginView(p) }
HomePresenter.createView     = { p -> HomeView(p) }
CartPresenter.createView     = { p -> CartView(p) }
ProductPresenter.createView  = { p -> ProductView(p) }
ReceiptPresenter.createView  = { p -> ReceiptView(p) }
ProductsPanelPresenter.createView  = { p -> ProductsPanelView(p) }
PurchasesPanelPresenter.createView = { p -> PurchasesPanelView(p) }
```

```mermaid
graph TD
    subgraph "Presenter (commonMain)"
        LP["LoginPresenter<br/>companion: createView"]
    end

    subgraph "Entry Point (platform)"
        EP["Main.kt<br/>LoginPresenter.createView = { p → LoginView(p) }"]
    end

    subgraph "View (view-compose)"
        LV["LoginView(presenter)<br/>extends ComposeCubeView"]
    end

    EP -->|registers factory| LP
    LP -->|"createView?.invoke(this)"| LV
    LV -->|"reads state"| LP
```

This mechanism lets:

- The presenter **not know** the concrete view class
- Different platforms register **different views** for the same presenter
- Tests leave the factory `null` — the presenter works without a view
- The React modality skip registering factories — the Skeletons take the role of the view

---

## Complete Flow: From Action to Pixel

The diagram below shows the complete flow from a user action to rendering:

```mermaid
sequenceDiagram
    actor User as User
    participant View as LoginView (Compose)
    participant SC as safeCall (BG thread)
    participant P as LoginPresenter
    participant S as LoginViewState
    participant API as REST Backend
    participant Rev as revision MutableState
    participant CR as Compose Runtime

    User->>View: Clicks "Sign in"
    View->>SC: safeCall { presenter.onEnter() }
    Note over View: UI thread released

    SC->>P: onEnter()
    P->>S: state.loading = true
    P->>View: update()
    View->>Rev: revision.value++
    Rev->>CR: Snapshot change → recomposition
    Note over View: Renders: spinner + disabled fields

    P->>API: loginService.fetchSubject()
    API-->>P: Subject(id=1, nick=Admin)

    P->>S: state.loading = false
    P->>S: app.subject = subject
    P->>P: Routes.home(app) → navigation
    Note over P: CubeNavigation commit → HomePresenter created
    Note over View: Root slot updated → HomeView rendered
```

---

## View Implementation Pattern

Every Compose view follows the same pattern:

```kotlin
class LoginView(private val presenter: LoginPresenter)
    : ComposeCubeView("login-view", presenter.app) {

    @Composable
    override fun Render() {
        // (1) Subscribes to recomposition
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value

        // (2) Reads the presenter state
        val state = presenter.state
        val loading = state.loading

        // (3) Uses remember for local UI state (text fields)
        var userName by remember { mutableStateOf(state.userName ?: "") }

        // (4) Renders the UI by reading the ViewState
        OutlinedTextField(
            value = userName,
            onValueChange = {
                userName = it
                state.userName = it  // ← direct mutation of the ViewState
            },
            enabled = !loading,
        )

        // (5) Actions delegate to the presenter via safeCall
        Button(
            onClick = { safeCall { presenter.onEnter() } },
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("Sign in")
            }
        }
    }
}
```

**Key points:**

| Step | What it does | Why |
|------|--------------|-----|
| `revision.value` | Creates a Compose subscription | Without it, the view does not recompose when `update()` is called |
| `presenter.state` | Accesses the mutable ViewState | The state is read directly — no copies, no mapping |
| `remember` | Keeps local UI state | Text fields need local state for smooth typing |
| `state.userName = it` | Direct mutation | The ViewState is mutable by design — simplicity > ceremony |
| `safeCall { ... }` | Dispatches the action to the background | Free UI thread, guaranteed serial execution |

---

## Slot Mechanism in Compose

The slot connects parent and child views without direct coupling. The flow:

```mermaid
sequenceDiagram
    participant Root as RootPresenter
    participant Intent as CubeIntent
    participant Home as HomePresenter
    participant RootState as RootViewState
    participant RootView as RootView Compose

    Note over Root: applyParameters deepest=false
    Root->>Intent: setViewSlot(SLOT_OWNER, contentSlot)
    Note over Root: contentSlot callback:<br/>view -> state.contentView = view then update()

    Note over Home: applyParameters deepest=true, init=true
    Home->>Home: view = createView(this) HomeView
    Home->>Intent: getViewSlot(SLOT_OWNER)
    Intent-->>Home: contentSlot
    Home->>Root: contentSlot.setView(homeView)
    Root->>RootState: state.contentView = homeView
    Root->>RootView: update() revision++
    Note over RootView: Recomposes and renders:<br/>RenderSlot(state.contentView)
```

**In the RootPresenter code:**

```kotlin
class RootPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    val state = RootViewState()

    // Callback that receives the child view and stores it in the state
    private val contentSlot = CubeViewSlot { v -> setContentView(v) }

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            view = createView?.invoke(this)
        }
        // Publishes the slot in the intent so the next step can use it
        intent.setViewSlot(PlaceAttributes.SLOT_OWNER, contentSlot)
        return true
    }

    private fun setContentView(view: CubeView) {
        if (state.contentView !== view) {
            state.contentView = view
            update()  // ← triggers RootView recomposition
        }
    }
}
```

**In the RootView code (Compose):**

```kotlin
class RootView(private val presenter: RootPresenter)
    : ComposeCubeView("root-view", presenter.app) {

    @Composable
    override fun Render() {
        val rev = revision.value

        val contentView = presenter.state.contentView
        if (contentView != null) {
            RenderSlot(contentView)  // ← renders the child view
        }
    }
}
```

`RootViewState.contentView` has type `CubeView` — the parent view does not know whether it is `HomeView`, `LoginView` or another one. It just calls `RenderSlot`, and Compose renders the right content.

---

## Per-Platform Initialization

Each platform has an entry point that follows the same pattern:

```mermaid
sequenceDiagram
    participant Main as Entry Point
    participant Plat as Platform Config
    participant Factories as View Factories
    participant App as ShoppingApplication
    participant Compose as Compose Runtime

    Main->>Plat: Configures JSON, HTTP, Crypto, Scheduler
    Main->>Factories: Registers createView for each presenter
    Main->>App: Creates the application instance
    Main->>App: app.go("public") → initial navigation
    Main->>Compose: Renders RootView
```

### Desktop (JVM)

```kotlin
fun main() {
    initializePlatform(baseUrl)  // JSON, HTTP, Crypto, Scheduler, View Factories

    val app = DesktopShoppingApplication()
    app.go("public")

    application {
        Window(title = "Storefront", ...) {
            ShoppingTheme {
                val rootView = app.getRootPresenter()?.view() as? RootView
                rootView?.Render()
            }
        }
    }
}
```

- `ScheduledExecutor`: Swing `javax.swing.Timer`
- `SessionStorage`: `java.util.prefs.Preferences`
- `updateHistory()`: no-op (no browser)

### Web (wasmJs)

```kotlin
fun main() {
    initializePlatform(baseUrl)

    val app = ComposeShoppingApplication()
    restoreAuthState(app)

    val hash = getLocationHash()
    if (hash.isNotBlank()) app.safeGo(hash) else app.go("public")

    jsOnHashChange {
        val newHash = getLocationHash()
        if (newHash != app.fragment) app.safeGo(newHash)
    }

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        ShoppingTheme {
            val rootView = app.getRootPresenter()?.view() as? RootView
            rootView?.Render()
        }
    }
}
```

- `ScheduledExecutor`: `setTimeout` / `setInterval`
- `SessionStorage`: `window.sessionStorage`
- `updateHistory()`: updates `window.location.hash` with an HMAC-signed intent

### Android

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        initializePlatform(baseUrl)

        val app = AndroidShoppingApplication(this)
        app.go("public")

        setContent {
            ShoppingTheme {
                val rootView = app.getRootPresenter()?.view() as? RootView
                rootView?.Render()
            }
        }
    }
}
```

- `ScheduledExecutor`: Android `Handler` + `Looper`
- `SessionStorage`: `SharedPreferences`
- `updateHistory()`: no-op

### iOS

```kotlin
fun MainViewController() = ComposeUIViewController {
    initializePlatform(baseUrl)

    val app = IosShoppingApplication()
    app.go("public")

    ShoppingTheme {
        val rootView = app.getRootPresenter()?.view() as? RootView
        rootView?.Render()
    }
}
```

- `ScheduledExecutor`: `dispatch_queue`
- `SessionStorage`: `NSUserDefaults`
- `updateHistory()`: no-op

---

## Deep-Linking and History (Web)

On the Web platform, Cube navigation is synchronized with the browser's `location.hash`, enabling deep-linking and the back/forward buttons.

```mermaid
sequenceDiagram
    participant User as User
    participant Browser as Browser
    participant App as ShoppingApplication
    participant Nav as CubeNavigation

    Note over App: Internal navigation
    App->>Nav: navigate().step(ROOT).step(HOME).step(PRODUCT).execute(intent)
    Nav->>Nav: commit() → updateHistory()
    App->>App: publishParameters(intent)
    App->>App: signIntent(intentStr) → HMAC-SHA256
    App->>Browser: window.location.hash = "product?productId=42&sign=..."

    Note over User: Clicks "Back" in the browser
    Browser->>App: hashchange event
    App->>App: safeGo(hash)
    App->>App: verifyIntent(intentStr, signature) → OK
    App->>Nav: navigate from parsed intent
```

The HMAC signature ensures that manually tampered URLs are rejected. The signing secret is:
- **Before login:** a fixed anonymous key
- **After login:** a per-session secret received from the server

---

## Loading Pattern

With `safeCall` running off the UI thread, loading indicators come naturally:

```mermaid
stateDiagram-v2
    [*] --> Idle: Screen rendered
    Idle --> Loading: User clicks action
    Loading --> Idle: Response received
    Loading --> Error: Exception captured
    Error --> Idle: User corrects and retries

    state Idle {
        [*] --> FieldsEnabled
    }

    state Loading {
        [*] --> FieldsDisabled
        FieldsDisabled --> SpinnerOnButton
    }

    state Error {
        [*] --> ErrorMessage
    }
```

**In the presenter** (synchronous, linear code):

```kotlin
fun onEnter() {
    state.loading = true  // → UI shows spinner (the state is observed: no update() call)

    try {
        val subject = loginService.fetchSubject(state.userName ?: "", state.password ?: "")
        state.loading = false

        if (subject?.id != null) {
            app.subject = subject
            Routes.home(app)
        } else {
            state.errorMessage = "Username or password not recognized!"
        }
    } catch (caught: Exception) {
        state.loading = false
        state.errorMessage = "Failed to communicate with the server. Check your connection."
    }
}
```

**In the view** (Compose reacts automatically):

```kotlin
Button(
    onClick = { safeCall { presenter.onEnter() } },
    enabled = !loading,
) {
    if (loading) CircularProgressIndicator() else Text("Sign in")
}
```

The presenter changes `state.loading`; the observed state (`ObservableViewState`) calls `update()`, and Compose recomposes. No callbacks, no observers, no explicit bindings.
