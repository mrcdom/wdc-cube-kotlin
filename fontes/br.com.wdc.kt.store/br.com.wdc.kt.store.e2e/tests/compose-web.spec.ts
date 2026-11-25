import { expect, test } from '@playwright/test'
import { watchErrors } from './support'

// Compose draws on a canvas: there are no elements to drive, so this checks that the app starts and draws
test('Compose web (WebAssembly) starts and draws', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/compose/index.html')
  await expect(page.locator('canvas')).toBeVisible({ timeout: 30_000 })
  await page.waitForTimeout(3_000)
  expect(errors).toEqual([])
})
