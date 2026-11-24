# store-presentation

Presenters (Cubes) of the WDC Store application — platform-independent presentation logic.

**Platforms:** JVM, Android, iOS, wasmJs

The same presenters are reused in the three architectures:
- [Compose Multiplatform (view local)](../../../docs/architecture.md) — presenters on the client
- [Native (view local)](../../../docs/architecture.md) — presenters on the client with a native UI per platform
- [React (view remota)](../../../docs/architecture-react.md) — presenters on the server

Contains: `RootPresenter` (the application), `LoginPresenter`, `HomePresenter`, `ProductPresenter`, `CartPresenter`, `ReceiptPresenter`, each with its scope (`XxxScope`) and keys class (`XxxKeys`). A presenter shows its scope and binds the scope's actions in its constructor (`scope.onBuy = action(1) { buy() }`); it never creates a view. The Home's products and purchases panels and the cart rows (`CartItemScope`) are segments: scopes of their own, without presenters.

The presenters reach the data through **resource calls** (`framework-domain`, package `resource`), in a `calls/` package next to the presenter that uses them; each reaches its repositories through a private property per repository, read when used: `private val productRepo: ProductRepository get() = ProductRepository.BEAN.get()`:

| Call | Verb | What it does |
|---|---|---|
| `CredentialsCheck` (`open/login/calls`) | own (`ResourceCall`) | The login: checks user name and password, answers the `Subject` or `null` |
| `ProductQuery` (`restricted/products/calls`) | `ResourceQuery` | One product, with its description |
| `ProductListQuery` (`restricted/products/calls`) | `ResourceQuery` | The Home's products, by name, without descriptions |
| `PurchasesPageQuery` (`restricted/home/calls`) | `ResourceQuery` | One page of the user's purchases, most recent first |
| `ReceiptQuery` (`restricted/receipt/calls`) | `ResourceQuery` | The receipt of a purchase **of the logged-in buyer** (the id comes from the address) |
| `PurchaseCreate` (`restricted/cart/calls`) | `ResourceCreate` | The checkout: the purchase and its items in one transaction; the items carry no price, the item repository's INSERT reads it |

A new instance per use, the request in the constructor: `ProductQuery(productId = id).run()`. `CartManager` only keeps the cart; its `commit` runs `PurchaseCreate`. `StoreCallTest` (`:store-tests`) tests the calls in process; the REST path is covered at the repository level.
