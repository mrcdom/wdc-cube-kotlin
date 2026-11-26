# 0022. The remote views' alerts in the user's language too

- **Date:** 2026-11-26
- **Status:** Accepted; amends [0021](0021-ui-texts-in-one-catalog.md), which left these texts in English

## Context

After [0021](0021-ui-texts-in-one-catalog.md), the remote views still showed two kinds of text in English, whatever the user's language:

- **The host's alert for an unexpected error** ("Could not process the request: …"). The framework passes a diagnostic to the application's `alertUnexpectedError`, and the Store's `RemoteRootPresenter` sent it to the browser as text (alert code `-1`).
- **The bridges' alert for a page older than the host** ("This page is out of date. Reload it to continue."), written in the framework's two bridges.

## Decision

**Host alerts go as a message, through a code of the Store's own.** `RemoteRootPresenter` sends the shell's alert with code `-3` (`ALERT_MESSAGE`), and `args` = the message's key, then each argument's name and value (`["error.unexpectedWithCause", "cause", "connection refused"]`). Both Store clients translate it with their catalog. The diagnostic goes to the server's log only.

- The framework and the remote protocol do not change: the alert's code and arguments were already the application's to define.
- Codes `-1` (a text) and `-2` (an address not reachable) keep working.

**The bridges take the outdated-page text from the application:** `RemoteBridge.outdatedMessage` (Kotlin), and `ScopeCoordinator.outdatedMessage` / `bridge.setOutdatedMessage` (TypeScript). The English text stays the default. The Store's clients set it at startup from `browser.outdated`.

## Consequences

- A remote view in Portuguese shows its alerts in Portuguese, except the cause of an unexpected error, which is an exception's message.
- The bridges gain one setting in 0.5.0, before it is published, with a default that keeps the old behavior.
- Another application on the same framework can do the same with its own codes and catalog. Nothing in the framework assumes the Store's catalog.

## Alternatives considered

- **A "message" alert in the framework's protocol**, specified with conformance fixtures: reusable as it stands, but it changes protocol 1's specification and every implementation (Java and C# too), for something the application can already express with its own alert code.
- **Leaving the outdated-page alert in English:** it is rare (an old page cached in the browser), but it would be the one text a Portuguese user could not read.
