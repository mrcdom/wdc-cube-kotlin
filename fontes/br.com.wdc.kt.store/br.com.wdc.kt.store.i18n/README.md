# store-i18n

The WDC Store's UI texts, in every language, for every view technology: Compose, the native views (web, iOS, Android) and the two remote clients (Kotlin and TypeScript).

## The source

One catalog per language, a flat JSON object of key → text:

| File | Language |
|---|---|
| [`messages.en-US.json`](messages.en-US.json) | US English, the default |
| [`messages.pt-BR.json`](messages.pt-BR.json) | Brazilian Portuguese |

Keys are grouped by screen or area: `common.*`, `login.*`, `home.*`, `products.*`, `purchases.*`, `product.*`, `cart.*`, `receipt.*`, `browser.*` (the remote clients' shell), `error.*` (what the presenters report). Values are filled into `{placeholders}`: `"home.greeting": "Hello, {name}"`. `format.date` is the language's date order (`{month}/{day}/{year}` in en-US, `{day}/{month}/{year}` in pt-BR). Prices stay in dollars in every language.

## What is generated

`./gradlew :store-scripts:generateMessages` (from `fontes/`) writes, from the catalogs:

| File | For |
|---|---|
| `src/commonMain/kotlin/br/com/wdc/store/i18n/StoreMessages.kt` | every Kotlin view: `Language`, `MessageKeys`, the catalogs, and one accessor per key on `Messages` (`messages.homeGreeting(name)`), with a parameter per placeholder |
| `remote.react.typescript/src/scripts/messages.ts` | the TypeScript remote client: `languageOf`, `Messages`, `MessageKey` |

The generated files are committed. `MessagesCatalogTest` (in `:store-scripts`) fails while a language misses a key, a key has other placeholders in another language, or the generated files do not match the catalogs.

## Adding a text or a language

- **A text:** add the key to every catalog, run the task, commit the catalogs and the generated files.
- **A language:** add `messages.<tag>.json` with every key (the test says which are missing), run the task. `Language.of` picks it for the platforms' tags of that language (`pt-BR`, `pt_BR`, `pt`).

## Who translates

The presentation does not know the user's language. A presenter reports a `Message` (a key and its arguments: `br.com.wdc.store.presentation.i18n.Message`), and each view translates it with its own `Messages`. The language is the platform's: the browser's (`navigator.language`) on the web, including the remote clients, the device's on iOS and Android, and Compose's `Locale.current`. On the remote views the message travels as `{"key": …, "args": {…}}` and is translated in the browser.

Not in the catalog:
- **Texts from the framework.** The remote clients' shell alerts for an unexpected error or a request that could not be read, and the bridges' outdated-protocol alert, are written in the published framework, so they stay in English.
- **Causes of unexpected errors.** An exception's message comes from the code or the server, so it is shown as it is, inside `error.unexpectedWithCause`.

The decision is in [ADR 0021](../../../docs/adr/0021-ui-texts-in-one-catalog.md).
