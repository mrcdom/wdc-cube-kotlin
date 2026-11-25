# 0018. Failed sign-ins are counted in the database; the per-address limit stays per instance

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

[0012](0012-scram-sign-in.md) slows down password guessing with two limits, both kept in each instance's memory:

- **failed sign-ins per user** (`LoginThrottle`: five free, then a wait from 30 seconds doubling up to 15 minutes);
- **`/api/auth` requests per client address and minute** (`AuthRateLimit`, default 60).

With several backend instances ([running-several-instances.md](../running-several-instances.md)), each one counts its own. Someone guessing one account's password across N instances gets N times the free attempts, and each instance's wait covers only the attempts it saw.

## Decision

**Failed sign-ins are counted in the database**, in `EN_LOGIN_FAILURE`: the user, the number of failures in a row, and the time of the last one (UTC).

- **The wait is worked out when an attempt is checked**, from the count and the last failure. It is not stored. So the only write instances can race on is the count, and adding one is a single `UPDATE … SET FAILURES = FAILURES + 1`. It starts over at 1 when the last failure is older than an hour. The first failure inserts the row; when another instance inserted it first, the `UPDATE` is tried again.
- A success deletes the row. The periodic cleanup deletes the ones older than an hour.
- It works on any database, with or without the instances' mesh ([0017](0017-mesh-between-instances.md)), like the sign-in challenges.

**The per-address limit stays in each instance's memory.** It guards against load and counts every request, so in the database it would cost a write per request. Limiting per address belongs at the proxy in front of the instances, which sees all their traffic, and the guide shows how.

## Consequences

- Attempts on one account count as one, however they are spread over the instances. The protection 0012 describes holds for any number of instances.
- Each sign-in attempt reads one row. Each failure costs one write, and each success one `DELETE`.
- With N instances, the per-address limit is up to N times the configured one. Behind a proxy it already counted every client as the proxy's address. Deployments limit per address at the proxy.

## Alternatives considered

- **Count over the instances' mesh:** each instance announces its failures and adds up the others'. That needs no table, but it works only with `cluster.peers`, and it is approximate: a failure announced while a connection is down is not counted.
- **The per-address limit in the database too:** exact, but at a write for every `/api/auth` request.
- **Storing the time the wait ends:** the instances would race on computing it. Deriving it from the count and the last failure leaves one atomic write.
