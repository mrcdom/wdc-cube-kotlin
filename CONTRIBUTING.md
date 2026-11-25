# Contributing

Thanks for taking the time. This repository is a reference architecture: the framework (`fontes/br.com.wdc.kt.framework`, published on Maven Central as `io.github.mrcdom.wdc.kt:*`), the WDC Store demo that exercises it, and a starter project. Changes are judged by whether they keep it a good example to copy.

## Before you start

- **Read the documentation of the area you will touch** ([docs/](docs/README.md)): architecture, Cube framework, persistence, security. Where a document and the code disagree, the code is right — fix the document in the same change.
- **Read the [decision records](docs/adr/README.md).** Several choices depart on purpose from common practice or from the Java version of the architecture (no secured repositories, access control only as data policies at the REST boundary, actions run one at a time…). A change that goes against a record supersedes it with a new one, saying why.
- **For anything bigger than a fix, open an issue first** to agree on the approach.

## Build and test

JDK 21; Gradle comes with the wrapper. Everything runs from `fontes/`.

```bash
cd fontes
./gradlew test                 # the JVM tests (needs network; not --offline)
./gradlew build                # what CI checks: every target's tests (JS and Wasm in a headless Chrome, iOS on the
                               # simulator on macOS), the suite again on an embedded PostgreSQL, the Android lint, the public API
```

- **Browser tests** of the web clients against a real backend: see [the e2e README](fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.e2e/README.md).
- **The TypeScript client:** `npm ci`, then `npx -y -p typescript@5.7.3 tsc --noEmit -p tsconfig.json` `npm run format:check` and `npm run lint` in its folder; the two bridges also have `npm run lint`.
- **The starter:** `./gradlew build` in [starter/](starter/README.md).

`./gradlew test` does not compile the targets outside the JVM; before calling a change done, run `./gradlew build`.

## What a change should carry

- **Tests** that would fail without it.
- **The documentation** it changes, in the same pull request: the architecture documents, the module's README, and — for the framework — a row in the release notes of the next version ([fontes/br.com.wdc.kt.framework/README.md](fontes/br.com.wdc.kt.framework/README.md)), saying whether it breaks anything and what to do.
- **The public API dump**, when the framework's API changes: `./gradlew updateKotlinAbi`, and commit the `api/` files. `checkKotlinAbi` (in `check`) fails on an unrecorded change.
- **US English** in everything versioned: code, comments, KDoc, messages, tests, documentation, screen texts. Currency `$`, decimal point, dates MM/DD/YYYY in the views.

## Conventions worth knowing

- Presenters only make scopes and bind their actions in the constructor; the method an action runs carries the action's name (`scope.onBuy = action(1) { onBuy() }`).
- The presentation reaches data through resource calls (`calls/` next to each presenter), which read the repository from its `BEAN`.
- Calls with two or more arguments pass them by name, one per line.
- Scopes that make up the same screen live in the same file.

The decision records and [architecture-cube.md](docs/architecture-cube.md) give the reasons.

## Pull requests

One topic per pull request, against `main`, with the CI green. Describe what changed and how it was verified. Security problems are not reported in pull requests or issues: see [SECURITY.md](SECURITY.md).
