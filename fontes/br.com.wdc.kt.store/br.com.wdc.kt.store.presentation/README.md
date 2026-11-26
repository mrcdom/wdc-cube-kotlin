# store-presentation

Presenters (Cubes) of the WDC Store application — platform-independent presentation logic.

**Platforms:** JVM, Android, iOS, JS, wasmJs

The same presenters are reused in the three architectures:
- [Compose Multiplatform (local view)](../../../docs/architecture.md) — presenters on the client
- [Native (local view)](../../../docs/architecture.md) — presenters on the client with a native UI per platform
- [React (remote view)](../../../docs/architecture-react.md) — presenters on the server

Contains: `RootPresenter` (the application), `OpenPresenter` and `RestrictedPresenter` (guards), `LoginPresenter`, `HomePresenter`, `ProductPresenter`, `CartPresenter`, `ReceiptPresenter`, each with its scope (`XxxScope`) and keys class (`XxxKeys`). Two guard places sit between the root and the screens, as in the Java version: `OpenPresenter` (`OpenKeys`, prefix `op`, place `open`), above the login, and `RestrictedPresenter` (`RestrictedKeys`, prefix `rs`, place `restricted`), above the home and its children. They have no scope and no view: the restricted guard sends a user without a subject to the open area, the open guard sends a signed-in user to the restricted area, and each, as the deepest step, goes on to its screen (the home, the login). The tree: `RootKeys` → `OpenKeys` → `LoginKeys`, and `RootKeys` → `RestrictedKeys` → `HomeKeys` → `ProductKeys`/`CartKeys`/`ReceiptKeys`; the addresses of the screens did not change (`public/login`, `home`, …). A presenter shows its scope and binds the scope's actions in its constructor (`scope.onBuy = action(1) { onBuy() }`); it never creates a view. A method called by a scope action carries the action's name, with `on` ([ADR 0015](../../../docs/adr/0015-action-handlers-carry-the-action-name.md)). Texts a view shows come from the presentation as an `i18n.Message`: a key and its arguments, which presenters put in `errorMessage` and the views translate from the catalog (`:store-i18n`); the presentation does not know the user's language. The Home's products and purchases panels and the cart rows (`CartItemScope`) are segments: scopes of their own, without presenters. Each scope declares its `VID`, the id of the client component that paints it in the remote React view (`LoginScope.VID`).

**The session** is the `RootPresenter`'s: `startSession(subject)` at sign-in keeps the user and the session's tokens in the platform's `SessionStorage`, and the first navigation restores them. It listens to the session (`SessionListener`): on each renewal it saves the new tokens (the refresh token is rotated), and when the server will not renew it, it forgets the user and goes to the login, which says the session expired (`LoginKeys.sessionExpired`); the saved cart stays. `endSession()`, the Home's exit, revokes the session on the server and clears the storage. What a remote view sends ciphered (the login's password) is deciphered by `b64Decipher`, which only the remote host implements.

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
