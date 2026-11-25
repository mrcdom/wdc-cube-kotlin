# Cube starter

The smallest application of the Cube MVP architecture: a list of tasks with a detail, in the browser, and a backend that serves the page and keeps the tasks. Copy this folder to start your own.

It shows the pieces every Cube application has, and nothing else:

- **Places, declared by keys classes**, in a tree: `RootKeys` → `TaskListKeys` (`tasks`) → `TaskKeys` (`task?tk-id=3`). Presenters navigate only through keys (`TaskKeys(app).apply { taskId = 3 }.flip()`), and the address of the page follows: reload it, share it, or go back and forth.
- **Presenters that only make scopes.** A scope is the state of a piece of the screen plus its actions; the presenter binds the actions in its constructor (each to a method of the same name: `scope.onAdd` → `onAdd()`) and fills the scope in `applyParameters`, loading on the first navigation, or when what it shows changed, not on every one. It never creates or holds a view.
- **Calls: the contract between the two sides.** Each presenter reaches the data through calls (`list/calls/TaskCreate`, `detail/calls/TaskQuery`…), in the `shared` module. A call is written as a request over HTTP: its `run()` posts its `Request` to its `PATH` (`POST /api/calls/task-create`) through `CallHttpClient`, which the frontend implements with `fetch`. The backend extends each call with an `…Action` of the same name (`TaskCreateAction`) that overrides `run()` with the repository, and its route only reads the request and runs the action. The presenter writes `TaskCreate(title = text).run()`.
- **Information in transit as a keys attribute.** The detail goes back to the list with `taskChanged`, and only then does the list load again.
- **Composition through slots.** The root offers a slot to the list; the list offers the task's detail a slot of its own, next to it.
- **Segments.** Each row of the list is a scope of its own (`TaskRowScope`), with its own actions, reused from one load to the next.
- **Computed state once per paint** (`remaining`, in `TaskListPresenter.commitComputedState()`).
- **Tests of the presenters without any view or server** (`TasksFlowTest`, in a headless Chrome): their calls go to a `FakeBackend` registered as the `CallHttpClient`.
- **A React view per scope**, in Kotlin/JS: the views read their scope and call its actions; nothing else.

The decisions behind these rules are in the [architecture decision records](../docs/adr/README.md); the [WDC Store](../README.md) is the complete application (login, data policies, remote transactions, six rendering technologies).

## Run it

JDK 21 is needed.

```bash
./gradlew :backend:run                         # the application on http://localhost:8090/ (PORT changes it)
./gradlew build                                # everything: the tests, both sides, the bundle inside the backend
```

After changing an npm dependency or the Kotlin version, `./gradlew kotlinUpgradeYarnLock` updates `src/frontend/yarn.lock`; the build fails while it is out of date.

To work on the views with reloading on change, run the backend, then `./gradlew :frontend:jsBrowserDevelopmentRun` (port 8091; it sends `/api` to the backend).

## The modules

```
src/
├── shared/      the contract between frontend and backend (JVM and JS): the data model (Task) and the calls,
│                written as requests over HTTP through CallHttpClient (data/, list/calls/, detail/calls/)
├── frontend/    the browser: the presentation — TasksApp (the application, which is also the root presenter),
│   │            RootKeys, list/ and detail/ (keys, scopes, presenters) —, its React views, the binding
│   │            between Cube and React, FetchCallHttpClient, the entry point; yarn.lock (the npm
│   │            packages' versions, checked by the build)
│   └── jsTest   TasksFlowTest: the presenters, driven through their scopes, against a FakeBackend
└── backend/     the server: the repository (in memory here), one …Action per call (list/calls/,
                 detail/calls/), one route per call (CallRoutes), and the frontend's bundle at the root
```

The presentation lives in the frontend, as it usually does. The architecture also lets it run on the server, with the browser only drawing (the remote view), or be shared by several clients (Compose, native iOS and Android): the WDC Store shows each. `src/frontend/…/CubeReact.kt` is generic: it is the same in any application drawn with React.

## Grow it

- **A new call:** in `shared`, an `open` class with its `PATH`, its `@Serializable` `Request` and its `run()` posting it; in the backend, its `…Action` overriding `run()` and one route in `CallRoutes`. The route only reads the request and runs the action. What the action throws reaches the browser as the same exception: a `NotFoundException` is a 404 there and back.
- **A new screen:** a keys class (declaring its place as a child of its parent's), a scope, a presenter with its calls, a React view, and one line in the view catalog (`Views.kt`) and in `TasksApp.PLACES`. A missing view fails at once, naming the scope. Follow the presenters here: sections (`Public Instance Fields`, `Cube API`, `User Actions`, `Messages`), `errorCode` and `errorMessage` set by `alert…`/`error…` methods, the parent's slot read on every navigation, and calls with two or more arguments by name.
- **A database:** register another `TaskRepository` in the backend's `main` (better, one built on the framework's contracts, `framework-domain` and `framework-jooq`) and a `TransactionService` in `TaskTransactions.BEAN`: each write action then runs in a transaction. Nothing else changes.
- **Local form state** (the text being typed) belongs to the view, but a navigation can reuse a presenter, its scope and the view drawing it for another item: key the form by the item, so it starts again from the new one (see `TaskView`).

## The framework's version

Until 0.5.0 is published on Maven Central (December 2026), `settings.gradle.kts` builds the framework from this repository's sources (`includeBuild("../fontes")`), so this folder has to stay next to `fontes/`. Once it is published, delete the `includeBuild` block: the modules already name the published artifacts (`io.github.mrcdom.wdc.kt:framework-cube:0.5.0`), and the folder can live anywhere.
