import { test } from '@playwright/test'
import { remoteJourney } from './remote-flow'

test('remote view, TypeScript client: sign in, cart, release of views, history', async ({ page }) => {
  await remoteJourney(page, '/index.html')
})
