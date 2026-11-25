// Lint: ESLint's and typescript-eslint's recommended rules, for code that runs in the browser.
import js from '@eslint/js'
import { defineConfig } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig(
  { ignores: ['lib/', 'node_modules/', 'coverage/'] },
  js.configs.recommended,
  tseslint.configs.recommended,
  { languageOptions: { globals: globals.browser } },
  // the fixtures are untyped JSON
  { files: ['test/**'], rules: { '@typescript-eslint/no-explicit-any': 'off' } },
)
