// Lint: ESLint's and typescript-eslint's recommended rules, and the rules of React hooks, for code that runs in the
// browser.
import js from '@eslint/js'
import { defineConfig } from 'eslint/config'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig(
  { ignores: ['lib/', 'dist/', 'node_modules/', '.parcel-cache/'] },
  js.configs.recommended,
  tseslint.configs.recommended,
  reactHooks.configs.flat.recommended,
  { languageOptions: { globals: globals.browser } },
)
