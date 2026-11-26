# Browser tests

The WDC Store's web clients in a real browser (Playwright, Chromium) against a real backend: the landing page, the
remote view's TypeScript and Kotlin clients, the native web client and Compose web. The backend runs with security on
(a random JWT secret) and fresh demo data, in a work directory of its own (`build/e2e-work`); the clients are the ones
deployed in `fontes/work/deployment` and the TypeScript bundle in the remote host's resources.

The CI runs them on every push (`.github/workflows/build.yml`, job "Browser tests").

```bash
cd fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.e2e
./prepare.sh                          # builds the TypeScript bundle, deploys native, compose and remote-kotlin, builds the backend
npm ci
npx playwright install chromium
npx playwright test                   # starts the backend on port 8091 (E2E_PORT to change it) and stops it at the end
```

| Test | What it proves |
|---|---|
| `landing.spec.ts` | The root is the landing page with the four clients deployed; an unknown path comes back to it. |
| `remote-typescript.spec.ts`, `remote-kotlin.spec.ts` | The remote view's journey (`remote-flow.ts`): sign in with the password ciphered on the wire after the ECDH handshake, open a product, add it to the cart, leave the cart (the host releases its views), come back through the browser's history, remove the row. |
| `native-web.spec.ts` | The native web client (presenters in the browser, REST): sign in and fill the cart. |
| `compose-web.spec.ts` | Compose web starts and draws (it paints on a canvas, so there is nothing to drive). |
| `portuguese.spec.ts` | The native web client and both remote clients in pt-BR: the texts come from the Portuguese catalog, also those the host sends. |
| `remote-alert.spec.ts` | A request the host cannot read shows the translated alert (code -3) in both remote clients, in en-US and pt-BR. |

Every login also checks that the fields say what they are (`autocomplete="username"`, a `password` field with
`autocomplete="current-password"`), and every test fails on a script error in the page.
