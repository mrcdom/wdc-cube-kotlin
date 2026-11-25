import { defineConfig } from 'vitest/config'

// The bridge runs in a browser: the tests run in jsdom, with Node's Web Crypto.
export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
    testTimeout: 30000,
  },
})
