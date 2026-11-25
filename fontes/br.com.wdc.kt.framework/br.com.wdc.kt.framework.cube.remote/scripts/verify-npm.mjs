// Before publishing: the two packages as npm would ship them, installed into a project that is not this one.
// Everything else here resolves the bridges from their sources; this is the first place their packed form is used.
//
//   node scripts/verify-npm.mjs
//
// Packs both, installs the tarballs (with their peer dependencies) into a new folder outside the repository,
// type-checks a consumer against the published types (strict, NodeNext, no skipLibCheck) and imports both in Node.
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = fileURLToPath(new URL('..', import.meta.url))
const run = (cmd, args, cwd) => execFileSync(cmd, args, { cwd, stdio: 'inherit' })
const work = mkdtempSync(join(tmpdir(), 'wdc-bridge-verify-'))

try {
  const packs = join(work, 'packs')
  mkdirSync(packs)
  for (const dir of ['remote.bridge.typescript', 'remote.bridge.typescript.react']) {
    run('npm', ['pack', '--pack-destination', packs], join(here, dir))
  }
  const tarballs = readdirSync(packs).map((name) => join(packs, name))

  const consumer = join(work, 'consumer')
  mkdirSync(consumer)
  writeFileSync(join(consumer, 'package.json'), JSON.stringify({ name: 'consumer', private: true, type: 'module' }))
  run(
    'npm',
    [
      'install',
      '--no-audit',
      '--no-fund',
      ...tarballs,
      'react@19',
      'react-dom@19',
      'history@5',
      'universal-cookie@7',
    ].concat(['typescript@5.7.3', '@types/react@19']),
    consumer,
  )
  writeFileSync(
    join(consumer, 'tsconfig.json'),
    JSON.stringify({
      compilerOptions: {
        target: 'ES2024',
        lib: ['ES2024', 'DOM'],
        module: 'NodeNext',
        moduleResolution: 'NodeNext',
        jsx: 'react-jsx',
        strict: true,
        noEmit: true,
      },
      include: ['app.tsx'],
    }),
  )
  writeFileSync(
    join(consumer, 'app.tsx'),
    `import { ScopeCoordinator, BROWSER_VSID, PROTOCOL_VERSION, type BrowserScope } from 'wdc-cube-remote-bridge'
import bridge, { BaseViewClass, type ViewProps } from 'wdc-cube-remote-bridge-react'

export function Hello({ vsid }: ViewProps) {
  return <div>{vsid}</div>
}
export const used: unknown[] = [PROTOCOL_VERSION, BROWSER_VSID, ScopeCoordinator, bridge, BaseViewClass]
export type Alert = BrowserScope['alertMessage']
`,
  )
  run('npx', ['tsc', '-p', 'tsconfig.json'], consumer)
  run(
    'node',
    [
      '--input-type=module',
      '-e',
      "await import('wdc-cube-remote-bridge'); await import('wdc-cube-remote-bridge-react'); console.log('both import')",
    ],
    consumer,
  )
  console.log('verify-npm: the packed packages install, type-check and import')
} finally {
  rmSync(work, { recursive: true, force: true })
}
