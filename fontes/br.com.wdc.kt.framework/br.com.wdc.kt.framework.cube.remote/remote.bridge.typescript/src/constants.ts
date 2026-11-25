export const NOOP_VOID = () => void 0 as void

export const CAUTHED = (reason: unknown) => {
  console.error('Unexpected', reason)
}

export const BROWSER_VID = '7b32e816a191'
export const BROWSER_VSID = `${BROWSER_VID}:0`

/** The version of the remote protocol this client speaks: it goes in the handshake (`protocol`). */
export const PROTOCOL_VERSION = 1

/** What the user is told when the host does not speak this client's version. */
export const OUTDATED_MESSAGE = 'This page is out of date. Reload it to continue.'

// Keepalive interval to prevent "Idle Timeout" errors
// Reduced to 15 seconds to ensure WebSocket stays active while page is open
export const KEEP_ALIVE_INTERVAL = 15 * 1000
