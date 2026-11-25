import { expect, type Page } from '@playwright/test'
import { signIn, watchErrors, watchSocket } from './support'

/** The vids the host gives the cart and its rows (each scope declares its own). */
const CART_VID = '7eb485e5f843'
const CART_ROW_VID = '5d9e3c7a1f42'

/**
 * The remote view's journey, the same for every client of the protocol: sign in with the password ciphered on the
 * wire, open a product, add it to the cart, leave the cart (the host releases its views), come back through the
 * browser history, and remove the row.
 */
export async function remoteJourney(page: Page, path: string) {
  const errors = watchErrors(page)
  const socket = watchSocket(page)

  await page.goto(path)
  await signIn(page)

  // the password went ciphered, inside the login's form data
  const loginForm = socket.sent.flatMap((m) => Object.values(m ?? {})).find((v: any) => v && v.password)
  expect(loginForm, 'the login sent its form').toBeTruthy()
  expect(loginForm.password).not.toBe('fulano')
  // the session's key was agreed by ECDH: the client sent its key and the host answered with its own
  expect(socket.sent.some((m) => m?.clientKey)).toBe(true)
  expect(socket.received.some((m) => m?.serverKey)).toBe(true)

  await page.getByText('Italian Design Coffee Maker').first().click()
  await page.getByRole('button', { name: 'Add to Cart' }).click()
  await expect(page.getByText('Shopping Cart')).toBeVisible()
  await expect(page.getByText('$199.99').first()).toBeVisible()

  const beforeLeaving = socket.received.length
  await page.getByRole('button', { name: 'Continue Shopping' }).click()
  await expect(page.getByText('Shopping Cart')).toBeHidden()
  await expect
    .poll(() => socket.releasedSince(beforeLeaving).map((id) => id.split(':')[0]))
    .toEqual(expect.arrayContaining([CART_VID, CART_ROW_VID]))

  // back in the browser's history: the host follows the signed address
  await page.goBack()
  await expect(page.getByText('Shopping Cart')).toBeVisible()

  await page.getByRole('button', { name: 'Remove' }).click()
  await expect(page.getByText('Shopping Cart')).toBeHidden()

  expect(errors).toEqual([])
}
