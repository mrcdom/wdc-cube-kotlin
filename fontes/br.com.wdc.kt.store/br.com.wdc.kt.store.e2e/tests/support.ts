import { expect, type Page } from '@playwright/test'

/** The demo customer: the password is the user name. */
export const USER = { name: 'fulano', password: 'fulano', greeting: 'Hello, John Doe' }

/** Collects the page's script errors; a test ends by checking there were none. */
export function watchErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  return errors
}

/** The remote view's WebSocket messages, both ways, parsed. */
export function watchSocket(page: Page) {
  const sent: any[] = []
  const received: any[] = []
  page.on('websocket', (ws) => {
    ws.on('framesent', (f) => sent.push(parse(f.payload)))
    ws.on('framereceived', (f) => received.push(parse(f.payload)))
  })
  return {
    sent,
    received,
    /** The view ids the host released since [from] (an index into `received`). */
    releasedSince: (from: number) => received.slice(from).flatMap((m) => m?.releasedViews ?? []) as string[],
  }
}

function parse(payload: string | Buffer) {
  try {
    return JSON.parse(payload.toString())
  } catch {
    return null
  }
}

/**
 * Signs in from the login screen: the user name and the password fields, in that order, then "Sign in". The fields
 * must say what they are, so password managers fill them and screen readers don't read the password.
 */
export async function signIn(
  page: Page,
  texts: { signIn: string; greeting: string } = { signIn: 'Sign in', greeting: USER.greeting },
) {
  const fields = page.locator('input')
  await expect(fields.nth(1)).toBeVisible({ timeout: 20_000 })
  await expect(fields.nth(0)).toHaveAttribute('autocomplete', 'username')
  await expect(fields.nth(1)).toHaveAttribute('type', 'password')
  await expect(fields.nth(1)).toHaveAttribute('autocomplete', 'current-password')

  await fields.nth(0).fill(USER.name)
  await fields.nth(1).fill(USER.password)
  await page.getByRole('button', { name: texts.signIn }).click()
  await expect(page.getByText(texts.greeting)).toBeVisible({ timeout: 20_000 })
}
