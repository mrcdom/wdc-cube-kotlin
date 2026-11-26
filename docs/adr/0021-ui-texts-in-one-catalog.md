# 0021. The UI texts in one catalog per language, translated by the views from keys

- **Date:** 2026-11-26
- **Status:** Accepted

## Context

The WDC Store's texts were written in each of the six view technologies, in English, and in the presenters: about 20 error messages that the presenters put, ready to show, in each scope's `errorMessage`. Wording drifted between technologies: "Add" or "Add to Cart", "Sign in" or "Login". There was no way to show the store in another language, and nothing to check that a text existed everywhere it was needed.

## Decision

**One catalog per language, with the code generated from it**, like the design tokens ([0020](0020-design-tokens.md)). `messages.<language>.json` lives in the new module `:store-i18n`, as key → text with `{placeholders}`. `./gradlew :store-scripts:generateMessages` writes:

- for Kotlin: the languages, the keys, and one accessor per key with a parameter per placeholder, so a missing argument does not compile;
- for the TypeScript client: the same.

`MessagesCatalogTest` fails while a language misses a key, a key's placeholders differ between languages, or the generated files are stale.

**The presentation gives keys; the views translate.** `errorMessage` is a `Message` (a key and its arguments), not a text. Each view translates it in the platform's language, and the remote views do so in the browser: the message travels as `{"key", "args"}`. An unexpected error shows its cause through `error.unexpectedWithCause`; the code's own description of what failed goes to the log only.

**Two languages:** en-US, the default and the repository's language, and pt-BR, which proves that the catalog is complete. Dates follow the language's order (`format.date`). Prices stay in dollars in every language: the currency is a fact of the data, not of the language.

Where the technologies worded the same thing differently, the native web's wording won, as with the tokens.

## Consequences

- One text is changed in one place, in every language, for every view.
- A pt-BR device, or a browser in Portuguese, shows the store in Portuguese, with dates as DD/MM/YYYY.
- The presentation stays free of the language. For the remote views, the server does not need to know the user's language.
- Framework texts (the remote shell's unexpected-error alerts, the outdated-protocol alert) stay in English: translating them means a catalog in the published framework.
- The causes of unexpected errors (exception messages) are shown as they come.

## Alternatives considered

- **Each platform's own resources** (`strings.xml`, `Localizable.strings`, Compose Resources, JSON for the web): idiomatic on each platform, but the same text repeated in six places, with nothing checking that they agree.
- **The presenter translates**, knowing the user's locale: simpler views, but the language would be decided on the server for the remote views, and the presentation would carry every language.
- **English only, with the catalog ready:** less work, but nothing would show the catalog is complete.
