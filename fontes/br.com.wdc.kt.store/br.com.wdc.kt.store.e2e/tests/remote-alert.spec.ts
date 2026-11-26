import { expect, test, type Page, type WebSocketRoute } from '@playwright/test'
import { signIn, USER } from './support'

/**
 * An unexpected error on the host reaches the remote views' alert as a message of the catalog (code -3), and the
 * browser shows it in its own language. To cause one, the test sits between the page and the host on the WebSocket and
 * sends the host a message that is not JSON: the host cannot read it, and alerts the user.
 */
async function withHostLine(page: Page): Promise<() => WebSocketRoute> {
  let host: WebSocketRoute | null = null
  await page.routeWebSocket(/\/dispatcher\//, (ws) => {
    const server = ws.connectToServer()
    ws.onMessage((message) => server.send(message))
    server.onMessage((message) => ws.send(message))
    host = server
  })
  return () => {
    if (!host) throw new Error('the page opened no socket to the host')
    return host
  }
}

const CLIENTS = [
  { name: 'TypeScript client', path: '/index.html' },
  { name: 'Kotlin client', path: '/remote-kotlin/index.html' },
]

for (const client of CLIENTS) {
  test.describe(client.name, () => {
    test('in en-US, the host alert reads in English', async ({ page }) => {
      const host = await withHostLine(page)
      await page.goto(client.path)
      await signIn(page)

      host().send('this is not JSON')
      await expect(page.getByText('Warning!')).toBeVisible({ timeout: 10_000 })
      await expect(page.getByText(/Unexpected error: /)).toBeVisible()
      await page.getByRole('button', { name: 'OK' }).click()
      await expect(page.getByText('Warning!')).toBeHidden()
    })

    test.describe('in pt-BR', () => {
      test.use({ locale: 'pt-BR' })

      test('the host alert reads in Portuguese', async ({ page }) => {
        const host = await withHostLine(page)
        await page.goto(client.path)
        await signIn(page, { signIn: 'Entrar', greeting: USER.greeting.replace('Hello', 'Olá') })

        host().send('this is not JSON')
        await expect(page.getByText('Atenção!')).toBeVisible({ timeout: 10_000 })
        await expect(page.getByText(/Erro inesperado: /)).toBeVisible()
        await page.getByRole('button', { name: 'OK' }).click()
        await expect(page.getByText('Atenção!')).toBeHidden()
      })
    })
  })
}
