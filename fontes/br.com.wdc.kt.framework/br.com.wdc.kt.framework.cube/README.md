# framework-cube

Engine of the **Cube MVP** pattern — state management, hierarchical navigation with Places, and presenter lifecycle.

**Platforms:** JVM, Android, iOS, wasmJs

## Components

| Component | Responsibility |
|------------|-----------------|
| `CubePlace` | Navigation destination with ID, name and presenter factory |
| `CubeTreePlace` | A place in a tree (`child`), hanging from `ROOT` — the application itself, with no presenter and not a step of any path: the path of a place is the navigation that reaches it; ids grow from the top to the leaves |
| `CubeKeys` | Base of an application's keys classes, one per place: the application, the intent (a new one from the current state when none is given) and `flip()`, which navigates to the place |
| `CubePresenter` | Lifecycle: `applyParameters` → `publishParameters` → `commitComputedState` → `release` |
| `CubeIntent` | Container of parameters (serializable) and attributes (ephemeral, such as `ViewSlot`) |
| `CubeViewSlot` | Parent-child composition mechanism between views |
| `CubeView` | Rendering interface (Compose, React, etc.) |
| `Scope` | What a presenter shows: the values its view paints, as observed fields (`var x by field(initial)`; a change asks for a repaint once the presenter binds it with `bind(...)`), written as JSON for views that receive it serialized (`write`, `toJson`) |
| `CubeApplication` | Global state — map of active presenters, the `navigate()` entry point and `flip(place, intent)`, which steps through the path of a `CubeTreePlace`; the root of the tree: its `applyParameters(intent, initialization, deepest)` runs in every navigation before the first step (and first on rollback); built with the places an address can name, it resolves an address to its place (`placeOf`, `flip(address)`) |
| `CubeNavigation` | Transactional engine — orchestrates execute → commit/rollback with support for interruptions |

## Documentation

- [Cube Architecture — Navigation Mechanism](../../../docs/architecture-cube.md) — detailed explanation of the navigation cycle, interruptions, presenter migration, commit, rollback and framework guarantees
- [Cube + Compose Architecture](../../../docs/architecture-cube-compose.md) — integration with Compose Multiplatform: ComposeCubeView, revision counter, safeCall, slots, view factories
- [Overall Project Architecture](../../../docs/architecture.md) — overview of the architecture, layers, modules and patterns
