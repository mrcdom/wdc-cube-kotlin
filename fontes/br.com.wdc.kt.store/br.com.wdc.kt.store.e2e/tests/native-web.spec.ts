import { expect, test } from '@playwright/test'
import { signIn, watchErrors } from './support'

test('native web (presenters in the browser, REST): sign in and fill the cart', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/native/index.html')
  await signIn(page)

  await page.getByText('Italian Design Coffee Maker').first().click()
  await page.getByRole('button', { name: 'Add to Cart' }).click()
  await expect(page.getByText('Shopping Cart')).toBeVisible()
  await expect(page.getByText('$199.99').first()).toBeVisible()

  expect(errors).toEqual([])
})
