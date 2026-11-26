# store-design

The WDC Store's design tokens: one palette, one spacing scale, one set of radii and type sizes for every view technology — Compose, the native views (web, iOS, Android), the two remote clients (Kotlin and TypeScript) and the backend's landing page.

## The source

[`tokens.json`](tokens.json), in the [W3C Design Tokens](https://www.designtokens.org/) format. Groups:

| Group | Tokens | Type |
|---|---|---|
| `color` | the Material-style roles (`primary`, `onPrimary`, `surfaceVariant`, `error`…), the store's own (`price`, `success`, `successContainer`), and translucent ones (`surfaceVariant40`, `priceBackground`, `whiteOverlay10`…85, `toastBackground`) | `#RRGGBB` or `#RRGGBBAA` |
| `spacing` | `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32 | px |
| `radius` | `xs` 4, `sm` 8, `md` 12, `lg` 18, `xl` 20 | px |
| `fontSize` | `caption` 12, `body` 14, `bodyLarge` 16, `title` 20, `headline` 24, `display` 28 | px |
| `fontWeight` | `regular` 400, `medium` 500, `bold` 700 | CSS weight |
| `fontFamily` | `base`: Roboto, Helvetica, Arial, sans-serif | list |

Dimensions are numbers in each platform's own unit: dp on Android and in Compose, pt on iOS, px on the web.

## What is generated

`./gradlew :store-scripts:generateDesignTokens` (from `fontes/`) writes, from `tokens.json`:

| File | For |
|---|---|
| `src/commonMain/kotlin/br/com/wdc/store/design/StoreTokens.kt` | every Kotlin view: `StoreTokens.Color.Primary` (`0xAARRGGBB`), `StoreTokens.Spacing.Md`, … |
| `remote.react.typescript/src/scripts/tokens.ts` | the TypeScript remote client: `StoreTokens.color.primary` (CSS), `StoreTokens.spacing.md`, … |
| `backend/src/main/resources/landing/tokens.css` | the landing page: `--store-color-primary`, `--store-spacing-md`, … |

The generated files are committed. `DesignTokensTest` (in `:store-scripts`) fails while they don't match `tokens.json`: change the JSON, run the task, commit both.

`TokenColors` (hand-written, next to `StoreTokens`) reads a color token the way each platform needs it: `argbInt` for Android, the channels for `UIColor`, `css` for the web.

## Using them

Each technology keeps its own theme file (`StoreColors`, `Dimens`, `StoreTheme`…) with the names its views already use, and that file takes its values from the tokens. The views use the tokens directly for spacing, radii and type sizes **that are on the scale**; off-scale values and the sizes of things (images, icons, control heights, borders, breakpoints) are written as they are. Breakpoints stay per technology on purpose (iOS 700 pt, web 518 px, Android 480 dp).

Changing a value in `tokens.json` changes it everywhere, after the task runs and each client is rebuilt (the `deploy.sh` of each web view, `npm run build` for the TypeScript client).

The decision is in [ADR 0020](../../../docs/adr/0020-design-tokens.md).
