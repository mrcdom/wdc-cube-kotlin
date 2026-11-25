// prepack of both TypeScript packages: the repository's LICENSE goes into the package being packed.
import { copyFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

copyFileSync(fileURLToPath(new URL('../../../../LICENSE', import.meta.url)), 'LICENSE')
