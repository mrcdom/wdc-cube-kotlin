import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.E2E_PORT ?? 8091)

/**
 * The WDC Store's web clients in a real browser against a real backend (started by start-backend.sh, with security
 * on and fresh demo data). Build the clients and the backend first: ./prepare.sh.
 */
export default defineConfig({
  testDir: './tests',
  // one backend, one set of demo users: the tests share them, so they run one at a time
  workers: 1,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Compose web draws with WebGL: a software implementation in headless runs
        launchOptions: { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
      },
    },
  ],
  webServer: {
    command: './start-backend.sh',
    url: `http://localhost:${port}/health`,
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
