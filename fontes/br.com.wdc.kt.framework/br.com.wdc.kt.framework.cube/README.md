# framework-cube

Engine of the **Cube MVP** pattern — state management, hierarchical navigation with Places, and presenter lifecycle.

**Platforms:** JVM, Android, iOS, wasmJs

## Components

| Component | Responsibility |
|------------|-----------------|
| `CubePlace` | Navigation destination with ID, name and presenter factory |
| `CubePresenter` | Lifecycle: `applyParameters` → `publishParameters` → `commitComputedState` → `release` |
| `CubeIntent` | Container of parameters (serializable) and attributes (ephemeral, such as `ViewSlot`) |
| `CubeViewSlot` | Parent-child composition mechanism between views |
| `CubeView` | Rendering interface (Compose, React, etc.) |
| `CubeApplication` | Global state — map of active presenters and the `navigate()` entry point |
| `CubeNavigation` | Transactional engine — orchestrates execute → commit/rollback with support for interruptions |

## Documentation

- [Cube Architecture — Navigation Mechanism](../../../docs/architecture-cube.md) — detailed explanation of the navigation cycle, interruptions, presenter migration, commit, rollback and framework guarantees
- [Cube + Compose Architecture](../../../docs/architecture-cube-compose.md) — integration with Compose Multiplatform: ComposeCubeView, revision counter, safeCall, slots, view factories
- [Overall Project Architecture](../../../docs/architecture.md) — overview of the architecture, layers, modules and patterns
