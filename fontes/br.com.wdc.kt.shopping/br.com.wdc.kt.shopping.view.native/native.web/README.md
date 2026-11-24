# native.web

View implementation using **React + Material UI** through Kotlin/JS — no Compose Web, no Wasm.

## Architecture

### Base Class: `ReactCubeView<S>`

Every view extends `ReactCubeView<S>`, which ties the Cube lifecycle to React's rendering mechanism. A view receives
the application and the scope it paints — never the presenter — and calls the scope's actions (`scope.onX(...)`)
inside `safeCall`:

```kotlin
class ProductsPanelView(app: RootPresenter, scope: ProductsPanelScope)
    : ReactCubeView<ProductsPanelScope>("products-panel-view", app, scope) {

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

1. A scope change calls the view's `update()` → marks the view as dirty
2. On the next microtask (`Promise.resolve().then {}`), the scheduler flushes
3. The flush first runs `app.commitComputedState()` once (every live presenter's), then increments the `revision` (React state) of each dirty view
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

Parent views use the `RenderSlot` component. The parent scope holds the child's scope (`content`, and
`productsPanel`/`purchasesPanel` in Home); the parent asks the application for that scope's view, created once per
scope and reused:

```kotlin
// In HomeView
val contentView = scope.content?.let(app::viewOf)
RenderSlot { view = contentView }

// When the presenter navigates, the scope in the field changes
// and RenderSlot renders the new view automatically
```

The cart draws each row (`CartScope.rows`) with the view of the row's scope (`CartItemView`), so changing a quantity
repaints that row and the total, not the list. The catalog of views (`ScopeViews`, scope class → view) is set on the
application in `Main.kt` before the first navigation.

`RenderSlot` is memoized: a parent repaint does not repaint the view in its slot. Cube views are independent of each other — a view repaints itself when its own scope changes (its component subscribes to `onUpdate`), and the slot repaints only when the parent puts a different view in it.

### Emitters (event handlers)

Event handlers are properties of the view class, created once per view — the same function on every render, like the `readonly emitXxx` fields of the remote React client:

```kotlin
class ReceiptView(app: RootPresenter, scope: ReceiptScope) : ReactCubeView<ReceiptScope>("receipt-view", app, scope) {
    private val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    override val component = FC<Props> {
        Button { onClick = onOpenProducts }
    }
}
```

- An emitter does not capture values of the render: it reads what it needs from the scope, from a field of the view, or from an element ref, when it runs.
- UI state that belongs to the view (a quantity being chosen, the selected tab) lives in a field; the emitter changes it and calls `forceUpdate()`, which repaints this view only.
- List items get their own emitters, kept per key by `EmitterCache` (the products panel: `openProduct[product.id]`); `retainOnly(keys)` drops the keys no longer shown. A list whose rows have actions of their own (the cart) is better drawn as one view per row scope (`CartItemView`), whose emitters are fields like any view's.
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
├── Main.kt                # Entry point — bootstrap and the view catalog (ScopeViews)
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
    ├── CartView.kt              # Storefront cart and its row view (CartItemView)
    └── ReceiptView.kt           # Purchase receipt
```
