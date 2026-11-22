# framework-cube

Engine of the **Cube MVP** pattern — state management, hierarchical navigation with Places, and presenter lifecycle.

**Platforms:** JVM, Android, iOS, wasmJs

## Components

| Component | Responsibility |
|------------|-----------------|
| `CubePlace` | Navigation destination with ID, name and presenter factory |
| `CubeTreePlace` | A place in a tree (`root`, `child`): its path from the root is the navigation that reaches it; ids grow from the root to the leaves |
| `CubeKeys` | Base of an application's keys classes, one per place: the application, the intent (a new one from the current state when none is given) and `flip()`, which navigates to the place |
| `CubePresenter` | Lifecycle: `applyParameters` → `publishParameters` → `commitComputedState` → `release` |
| `CubeIntent` | Container of parameters (serializable) and attributes (ephemeral, such as `ViewSlot`) |
| `CubeViewSlot` | Parent-child composition mechanism between views |
| `CubeView` | Rendering interface (Compose, React, etc.) |
| `CubeApplication` | Global state — map of active presenters, the `navigate()` entry point and `flip(place, intent)`, which steps through the path of a `CubeTreePlace` |
| `CubeNavigation` | Transactional engine — orchestrates execute → commit/rollback with support for interruptions |

## Documentation

- [Cube Architecture — Navigation Mechanism](../../../docs/architecture-cube.md) — detailed explanation of the navigation cycle, interruptions, presenter migration, commit, rollback and framework guarantees
- [Cube + Compose Architecture](../../../docs/architecture-cube-compose.md) — integration with Compose Multiplatform: ComposeCubeView, revision counter, safeCall, slots, view factories
- [Overall Project Architecture](../../../docs/architecture.md) — overview of the architecture, layers, modules and patterns
