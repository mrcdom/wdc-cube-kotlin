# 0016. The remote protocol is versioned, and specified by shared fixtures

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

The remote view ([0010](0010-server-driven-remote-views.md)) has two clients in this repository (TypeScript and Kotlin/JS), one host, a stress client, and versions of the same architecture in Java and C#. Nothing told a host which version of the messages a client spoke: when 0.5.0 changed the handshake ([0011](0011-remote-channel-security.md)), an old bundle still cached in a browser would simply fail, in no clear way. And the only description of the messages was each implementation's code: the tests of the Kotlin host checked the Kotlin host, and the clients had none.

## Decision

**A version, in the handshake.** The client sends `"protocol": 1` with its `clientKey`; the host answers with the version it speaks, with its `serverKey`. A handshake with a version the host does not speak — or with none — and any message before an accepted handshake are answered with `{"requestId": …, "error": "unsupported-protocol", "protocols": [1]}`, and nothing in them is read. The client then sends nothing more, does not reconnect, and tells the user, in the browser's alert, that the page is out of date; the alert's OK reloads the page. The version goes up only when the wire changes in a way the other side cannot read.

**A specification with shared fixtures.** The protocol is written down message by message, with language-neutral fixtures, in `conformance/` of the remote framework:

- **host fixtures:** messages to send and the answers to expect, against a small *conformance application* (a counter with rows) that each host implements;
- **client fixtures:** messages a client receives, what the user does, and what it must send, keep and drop;
- **matching rules** that let one fixture serve every implementation: partial objects, placeholders, captures for the ids a host makes, scopes by id, the keep-alives left out.

The Kotlin host runs the host fixtures; the Kotlin client (Kotlin/JS, in a browser) and the TypeScript client (Vitest and jsdom) run the client fixtures, each with a fake WebSocket. The Java and C# versions can run the same files.

## Consequences

- An outdated page says so instead of failing.
- A change to the wire starts with a fixture, and every implementation shows whether it follows. The first run found a defect both clients shared: requests queued before the handshake went out right after the hello, not after its answer.
- A client without the version is refused, which is a breaking change of the wire in 0.5.0, alongside the handshake's own.
- The fixtures are part of the protocol: changing one means changing what the protocol says, in every implementation.

## Alternatives considered

- **The version in the WebSocket URL** (`?protocol=1`): the host could refuse before any message, but the client would see a closed socket instead of a message it can explain to the user.
- **Assume version 1 when missing:** more tolerant, but a client from before 0.5.0 would then fail later, in the handshake, with no explanation.
- **Specification only, without fixtures:** nothing would check that the implementations follow it.
