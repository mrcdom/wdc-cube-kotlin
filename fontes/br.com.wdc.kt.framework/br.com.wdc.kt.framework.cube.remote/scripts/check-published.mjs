// After publishing: asks the registry until both packages are visible at the version just published. A green publish
// step is not a release — the registry can take minutes to show a new version.
//
//   node scripts/check-published.mjs
import { readFileSync } from 'node:fs'

const packages = ['remote.bridge.typescript', 'remote.bridge.typescript.react'].map((dir) =>
  JSON.parse(readFileSync(new URL(`../${dir}/package.json`, import.meta.url), 'utf8')),
)
const deadline = Date.now() + 20 * 60 * 1000

for (const { name, version } of packages) {
  for (;;) {
    const response = await fetch(`https://registry.npmjs.org/${name}/${version}`)
    if (response.ok) {
      console.log(`${name}@${version} is on the registry`)
      break
    }
    if (Date.now() > deadline) {
      console.error(`${name}@${version} is not on the registry after 20 minutes`)
      process.exit(1)
    }
    await new Promise((resolve) => setTimeout(resolve, 30_000))
  }
}
