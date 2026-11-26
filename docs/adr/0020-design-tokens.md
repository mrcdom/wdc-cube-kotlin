# 0020. One set of design tokens, generated for every view technology

- **Date:** 2026-11-26
- **Status:** Accepted

## Context

The WDC Store draws the same screens with six view technologies: Compose, the native web, iOS and Android views, and the remote clients in Kotlin and in TypeScript. The backend's landing page is a seventh look. Each kept its own palette:

- the same values, copied by hand, but not the same roles. Compose had tertiary colors no view used; Android had a toast background; the remote Kotlin client lacked some colors.
- Android had a spacing and radius scale (`Dimens`); the other technologies wrote their sizes in each view.

A change to the brand meant seven edits that nothing checked.

## Decision

**`tokens.json`, in the W3C Design Tokens format, is the single source**, in a new multiplatform module `:store-design`. It holds:

- the palette: the native web theme's, which won where the themes differed; plus the roles only others used (`onSecondaryContainer`, `toastBackground`);
- spacing: Android's scale;
- radii;
- font sizes, weights and the family.

**The code is generated and committed.** `:store-scripts:generateDesignTokens`, like the jOOQ schema, writes:

- `StoreTokens.kt` (multiplatform), for every Kotlin view;
- `tokens.ts`, for the TypeScript client;
- `tokens.css`, CSS variables for the landing page.

`DesignTokensTest` fails while they don't match the JSON.

**Each technology keeps its theme file and its names** (`StoreColors`, `Dimens`, `StoreTheme`), now filled from the tokens. The views use the tokens for the spacing, radii and type sizes on the scale. These stay as they are:

- values off the scale;
- the sizes of things (images, icons, control heights, borders);
- breakpoints, which differ per platform on purpose.

The move to the tokens changed no value, so no screen changed.

## Consequences

- A color, spacing step or type size is changed once, in `tokens.json`. Running the task and rebuilding the clients changes it everywhere.
- Designers' tools that speak the W3C format can read and write the file.
- The landing page shares only the app's roles. Its extra colors and its dark mode are its own: the apps have no dark mode yet.
- Off-scale values remain in the views: each is a decision for later, to fold into the scale or to keep.

## Alternatives considered

- **The tokens as a Kotlin object, with TypeScript generated from it:** the source would have types, but a non-Kotlin consumer would read a translation, and design tools could not.
- **A Kotlin object, with the TypeScript theme kept by hand and a test comparing them:** the least machinery, but two files to edit for one change.
- **A third-party generator (Style Dictionary):** the usual tool, but a Node toolchain inside the Gradle build, for four kinds of output that fit in one file.
