import { expect, test } from '@playwright/test'
import { signIn, watchErrors } from './support'

// A browser in Brazilian Portuguese: the clients take its language and show the store in pt-BR
test.use({ locale: 'pt-BR' })

const PT = { signIn: 'Entrar', greeting: 'Olá, John Doe' }

test('native web in pt-BR: the texts come from the Portuguese catalog', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/native/index.html')
  await expect(page.getByText('Entre para continuar')).toBeVisible({ timeout: 20_000 })
  await signIn(page, PT)
  // the logout icon says what it does, in the page's language
  await expect(page.getByRole('button', { name: 'Sair' }).first()).toBeAttached()

  await page.getByText('Italian Design Coffee Maker').first().click()
  await page.getByRole('button', { name: 'Adicionar ao carrinho' }).click()
  await expect(page.getByText('Carrinho de compras')).toBeVisible()
  await expect(page.getByText('$199.99').first()).toBeVisible()

  expect(errors).toEqual([])
})

test('remote view, TypeScript client, in pt-BR: the browser translates what the host sends', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/index.html')
  await signIn(page, PT)
  await expect(page.getByText('Produtos').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sair' }).first()).toBeAttached()

  expect(errors).toEqual([])
})

test('remote view, Kotlin client, in pt-BR: the browser translates what the host sends', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/remote-kotlin/index.html')
  await signIn(page, PT)
  await expect(page.getByText('Produtos').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sair' }).first()).toBeAttached()

  expect(errors).toEqual([])
})
