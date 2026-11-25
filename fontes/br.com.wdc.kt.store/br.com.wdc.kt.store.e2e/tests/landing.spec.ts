import { expect, test } from '@playwright/test'

test('the root is the landing page, with every web client deployed', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'WDC Store' })).toBeVisible()
  await expect(page.locator('article.card')).toHaveCount(4)
  await expect(page.locator('article.card.available')).toHaveCount(4)
  await expect(page.getByText('Security is off')).toHaveCount(0)
})

test('an unknown path comes back to the landing page', async ({ page }) => {
  await page.goto('/no-such-page')
  await expect(page).toHaveURL(/\/$/)
})
