import { test } from '@playwright/test'
import { remoteJourney } from './remote-flow'

test('remote view, Kotlin client: sign in, cart, release of views, history', async ({ page }) => {
  await remoteJourney(page, '/remote-kotlin/index.html')
})
