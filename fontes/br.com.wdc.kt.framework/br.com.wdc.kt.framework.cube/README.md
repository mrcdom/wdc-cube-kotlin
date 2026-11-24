# framework-cube

Engine of the **Cube MVP** pattern — state management, hierarchical navigation with Places, and presenter lifecycle.

**Platforms:** JVM, Android, iOS, JS, wasmJs

**Main dependencies:** `framework-commons`, `kotlinx-coroutines-core`

## Components

| Component | Responsibility |
|------------|-----------------|
| `CubePlace` | Navigation destination with ID, name and presenter factory |
| `CubeTreePlace` | A place in a tree (`child`), hanging from `ROOT` — the application itself, with no presenter and not a step of any path: the path of a place is the navigation that reaches it; ids grow from the top to the leaves |
| `CubeKeys` | Base of an application's keys classes, one per place: the application, the intent (a new one from the current state when none is given) and `flip()`, which navigates to the place |
| `CubePresenter` | Lifecycle: `applyParameters` → `publishParameters` → `release`; `commitComputedState` (from `PresenterBase`) once per paint cycle |
| `AbstractPresenter` | Base of every presenter (`AbstractCubePresenter` extends it): binds the scope (`bind`), declares segments (`segment`, `detach`) and binds the guarded actions (`action`, `actionOf`, `actionOf2`, optionally with an event code and `target`; `onEvent` for a code handled by hand). An unhandled failure goes to `CubeApplication.alertUnexpectedError`; `updateHistory()` runs after every action |
| `CubeIntent` | Container of parameters (serializable) and attributes (ephemeral, such as the `ScopeSlot`s: `setScopeSlot`, `getScopeSlot`) |
| `ScopeSlot` | Parent-child composition: a presenter hands its scope to the parent (`setScope`), which keeps it in a field of its own scope |
| `CubeView` | A view kept as an object (native iOS/Android, a remote view, a test double): `instanceId`, `update`, `release` |
| `ScopeViews` | The registry of a view layer that keeps view objects: which view draws each kind of scope (`register<XxxScope> { … }`, `fallback`) and the view of each scope on screen — `viewOf(scope)` creates it once, repaints it on each change and releases it when the scope is released. Owned by the layer, not the application. Layers whose views are functions of the scope (Compose, React) do not need it |
| `Scope` | What a presenter shows: the values its view paints, as observed fields (`var x by field(initial)`; a change tells the subscribers — `subscribe`, `version` — so the views on screen repaint), and its actions. Released with its presenter (`release`, `onRelease`): a layer that keeps a view of it drops the view. Writes itself as JSON field by field (`write`, `toJson`): a nested scope as `"<name>ViewId"`, a list declared with `scopes()` as `"<name>ViewIds"`, `field(initial, sent = false)` left out. `dispatch(code, formData)` runs the action bound to an event code; `onSync`/`sync` copy what a remote view typed |
| `ScopeValue` | A struct a scope field holds that writes itself as one JSON value |
| `Action`, `ActionOf`, `ActionOf2` | An entry point of a presenter kept in a field of its scope, with zero, one or two values (`suspend operator fun invoke`); the view calls `scope.onX(...)` |
| `FormParam` | How an action bound with an event code reads one value from a remote view's form data (`FormParam.long/int/string(name)`) |
| `CubeApplication` | Global state — map of active presenters (it keeps no views), `commitComputedState()` (every live presenter's, called by the view layer once per paint cycle), `alertUnexpectedError`, the `navigate()` entry point and `flip(place, intent)`, which steps through the path of a `CubeTreePlace`; the root of the tree: its `applyParameters(intent, initialization, deepest)` runs in every navigation before the first step (and first on rollback); built with the places an address can name, it resolves an address to its place (`placeOf`, `flip(address)`) |
| `CubeNavigation` | Transactional engine — orchestrates execute → commit/rollback with support for interruptions |

## Documentation

- [Cube Architecture — Navigation Mechanism](../../../docs/architecture-cube.md) — detailed explanation of the navigation cycle, interruptions, presenter migration, commit, rollback and framework guarantees
- [Cube + Compose Architecture](../../../docs/architecture-cube-compose.md) — integration with Compose Multiplatform: views as functions of the scope, `useScope`, the paint cycle, safeCall, slots, the view catalog
- [Overall Project Architecture](../../../docs/architecture.md) — overview of the architecture, layers, modules and patterns
