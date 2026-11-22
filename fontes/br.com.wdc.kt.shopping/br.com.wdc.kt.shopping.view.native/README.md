# view-native

Views built with each **platform's native toolkit**, without abstraction frameworks such as Compose Multiplatform.

## Motivation

The main motivation for building native interfaces is to show that, using only each platform's native toolkit, you can build interfaces of the same quality as the versions built with declarative frameworks.

The cost is building with what the target platform offers — which may have no native reactive solution, or may lack some helpers that make assembling views easier. In return, the native versions **will always drain less battery** and **will stay aligned with the platform's evolution**, without depending on third-party updates.

## Subprojects

| Subproject | Platform | UI technology |
|---|---|---|
| [native.web/](native.web/) | Web (Kotlin/JS) | DOM API + React/MUI |
| [native.android/](native.android/) | Android | Android Views (programmatic) |
| [native.ios/](native.ios/) | iOS | UIKit through Kotlin/Native |

## Common Architecture

The three subprojects share the same **presenters**, **domain** and **persistence** layers — only the view layer is platform-specific. The architecture follows the **Cube MVP** pattern:

```mermaid
graph TD
    subgraph common["Common Layer (KMP)"]
        domain["Domain"]
        persistence["Persistence"]
        presenters["Presenters"]
    end

    presenters -->|CubeView interface| web["native.web<br/>React + MUI"]
    presenters -->|CubeView interface| android["native.android<br/>Android Views"]
    presenters -->|CubeView interface| ios["native.ios<br/>UIKit"]
```

### Cross-cutting Patterns

- **View Factory**: each presenter registers a factory for its native view when the application boots
- **ViewSlot**: container that manages swapping child views on navigation
- **ListSlot**: efficient list synchronization with view recycling (grow/shrink/update-in-place)
- **FlowLayout**: responsive grid that redistributes children according to the available width
- **Dirty-flag coalescing**: several `update()` calls are merged into a single `doUpdate()` per frame
- **Responsive layout**: breakpoints detect the available width and switch between compact (mobile) and wide (tablet/desktop) layouts

## Screens (Native Web version as reference)

| Login | Home (Products + Purchases) |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Home](screenshots/02-home.png) |

| Product Details | Cart |
|:---:|:---:|
| ![Product Details](screenshots/03-detalhes-produto.png) | ![Cart](screenshots/04-carrinho.png) |

| Receipt |
|:---:|
| ![Receipt](screenshots/05-recibo.png) |

> The three platforms (Web, Android, iOS) produce visually equivalent screens, with small nuances due to the native toolkits available.

## References

- [Cube Architecture](../../../docs/architecture-cube.md) — navigation mechanism and lifecycle
- [Architecture overview](../../../docs/architecture.md) — project context
