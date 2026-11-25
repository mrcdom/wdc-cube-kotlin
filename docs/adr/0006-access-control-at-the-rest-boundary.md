# 0006. Access control lives at the REST boundary, as data policies

- **Date:** 2026-10-05 (no secured repositories); 2026-11-24 (data policies)
- **Status:** Accepted

## Context

A repository is called from two places. **From a client** (Compose, native web, native apps), through the REST API: the user controls the client and can send any criterion, any projection, any entity. **From inside the server**, by the remote view's presenters: there the user cannot call a repository at all, only trigger what the presentation asks for.

The Java version wraps every repository in a `SecuredXxxRepository` that checks permissions and ownership on every call, wherever it comes from. Inside the server that check guards against nothing the user can reach, and it made every repository call carry a security context and a second layer of code. The REST controllers also had permission strings per route (`data:all`, `Requires x:y`), which said who may call an endpoint but not which rows or fields of the answer they may see.

## Decision

**Access control lives only at the HTTP boundary**, and there it is a **data policy** per entity (`framework-rest`, package `policy`; the WDC Store's in `persistence-rest/security`). For each role, a policy states which rows can be read (a criterion joined with **and** to the caller's, never replacing it), which fields can be read as they are, which are masked, and which writes are allowed. `RepositoryRpc` applies it to every request of the entity: reads, counts, inserts, updates, deletes.

There are no `SecuredXxxRepository` classes, neither in persistence nor in presentation. Inside the server, repositories return whatever is asked; the presentation is responsible for asking only for what belongs to the user in session. A parameter of the address is user input: a service that loads data named by navigation restricts it to the user in session (`ReceiptQuery(purchaseId, userId)`).

Specific rules that follow from it:

- a field the caller may not read, named in a projection or a criterion, is refused with 403 **naming the field**, instead of being dropped silently;
- the password is write-only: it never goes out through the API, with or without security;
- alternative groups in criteria (`anyOf`) are not accepted from the wire (400): a disjunction could widen the reach the policy grants.

## Consequences

- One place to review for "who sees what" per entity; a new entity needs a policy before it is exposed.
- Code inside the server can read more than any user may see, so presenters of the remote view have to ask carefully. This is the price of removing the secured layer, accepted knowingly.
- The C# and Java versions differ here on purpose; this record is the reason not to bring the secured repositories back.

## Alternatives considered

- **Secured repositories, as in Java.** Rejected by the maintainer: they add cognitive load and guard nothing a user can reach inside the server.
- **Permission strings per route.** Replaced: they cannot express rows and fields.

See [security-architecture.md](../security-architecture.md).
