# native.web

View implementation using **React + Material UI** through Kotlin/JS — no Compose Web, no Wasm.

## Architecture

### Base Class: `ReactCubeView`

Every view extends `ReactCubeView`, which ties the Cube lifecycle to React's rendering mechanism:

```kotlin
class ProductsPanelView(presenter: ProductsPanelPresenter) 
    : ReactCubeView<ProductsPanelPresenter>("products-panel", presenter) {

    override fun ChildrenScope.render() {
        // JSX through Kotlin/JS wrappers
        Typography { +"Products" }
        Box { /* ... */ }
    }
}
```

The lifecycle is:
1. `render()` — declarative function that produces the UI through React/MUI
2. `ViewUpdateScheduler` increments a `revision` → React detects the state change → re-render
3. Since React is already reactive, there is no need for manual guards (the virtual DOM optimizes automatically)

### Technology Stack

| Layer | Technology |
|--------|-----------|
| **Language** | Kotlin/JS (compiled to JavaScript) |
| **UI Library** | React 18 (through kotlin-wrappers) |
| **Component Library** | Material UI 5 (MUI) |
| **Bundler** | Webpack (through the Gradle plugin) |
| **Routing** | Hash-based (`#home`, `#product?id=1`) through Cube navigation |

### Cube ↔ React Integration

The bridge between the Cube framework (imperative) and React (declarative) is the `ViewUpdateScheduler`:

1. The presenter calls `update()` → marks the view as dirty
2. On the next microtask (`Promise.resolve().then {}`), the scheduler flushes
3. The flush increments the `revision` (React state) of each dirty view
4. React re-renders only the components whose state changed

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **RenderSlot** | React component that renders a child `CubeView` (navigation) |
| **Responsive breakpoints** | `window.innerWidth` to switch between mobile and desktop layouts |
| **Hash routing** | `window.location.hash` kept in sync with Cube navigation |
| **MUI theming** | Colors and typography through constants (`ShoppingColors`, `ShoppingTheme`) |
| **useEffect + revision** | Each view uses `useState(revision)` to trigger a re-render |

### Navigation and Slots

Parent views use the `RenderSlot` component, which renders the child view bound to a presenter slot:

```kotlin
// In HomeView
RenderSlot { view = contentView }

// When the presenter navigates, the slot gets another view
// and RenderSlot renders the new view automatically
```

`RenderSlot` is memoized: a parent repaint does not repaint the view in its slot. Cube views are independent of each other — a view repaints itself when its own view state changes (its component subscribes to `onUpdate`), and the slot repaints only when the parent puts a different view in it.

### Emitters (event handlers)

Event handlers are properties of the view class, created once per view — the same function on every render, like the `readonly emitXxx` fields of the remote React client:

```kotlin
class ReceiptView(private val presenter: ReceiptPresenter) : ReactCubeView("receipt-view", presenter) {
    private val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { presenter.onOpenProducts() } }

    override val component = FC<Props> {
        Button { onClick = onOpenProducts }
    }
}
```

- An emitter does not capture values of the render: it reads what it needs from the presenter state, from a field of the view, or from an element ref, when it runs.
- UI state that belongs to the view (a quantity being chosen, the selected tab) lives in a field; the emitter changes it and calls `forceUpdate()`, which repaints this view only.
- List items get their own emitters, kept per key by `EmitterCache` (`itemEmitters[item.id].remove`); `retainOnly(keys)` drops the keys no longer shown.
- Text fields are uncontrolled where the value is only needed on submit (login): `inputRef` + `defaultValue`, read when the emitter runs, so typing repaints nothing.

### Web Worker (optional)

The project supports running the presenters in a separate **Web Worker**, isolating the business logic from the rendering thread. In this mode:
- Presenters run on the worker thread
- The view communicates through serialized `postMessage`
- `bridge/` holds the adapters for that communication

## How to build and run

```bash
cd fontes

# Development with hot reload
./gradlew :view-native-web:jsBrowserDevelopmentRun --continuous

# Production build
./gradlew :view-native-web:jsBrowserProductionWebpack

# Web Worker build (if needed)
./gradlew :view-native-web-worker:jsBrowserDevelopmentWebpack
```

The application will be available at `http://localhost:8080/native/index.html`.

## File Structure

```
src/jsMain/kotlin/br/com/wdc/shopping/nativeui/web/
├── Main.kt                # Entry point — bootstrap and factory registration
├── bridge/                # Web Worker adapters (serialized communication)
├── theme/
│   ├── Colors.kt          # Material Design palette
│   └── Theme.kt           # MUI theme configuration
├── util/
│   ├── ViewUpdateScheduler.kt  # Update coalescing through a microtask
│   └── ViewUtils.kt            # Formatting, image URLs
└── views/
    ├── RootView.kt              # App shell + hash router
    ├── LoginView.kt             # Login screen
    ├── HomeView.kt              # Responsive layout with panels/tabs
    ├── ProductsPanelView.kt     # Product grid (CSS Grid/Flow)
    ├── PurchasesPanelView.kt    # Paginated purchase list
    ├── ProductView.kt           # Product detail
    ├── CartView.kt              # Storefront cart
    └── ReceiptView.kt           # Purchase receipt
```
