# 0001. Record architecture decisions

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

This project is a reference architecture: people read it to build their own applications. The architecture documents in [docs/](../README.md) describe how the code works today, but not why it is that way, nor what was weighed and turned down. Several decisions were deliberate departures from the Java version of the same architecture, or from common practice, and were at risk of being "fixed" back by someone who did not know the reason.

## Decision

Significant decisions are recorded here, one per file, numbered, in the shape of Michael Nygard's architecture decision records: context, decision, consequences, and the alternatives that were considered. The records are in US English, like the rest of the repository. They link to the architecture documents for details instead of repeating them.

A decision is significant when it shapes the code that adopters write, when it costs something that has to be accepted knowingly, or when it departs from the Java version or from what a reader would expect.

## Consequences

- A change that goes against a record has to say so and supersede it with a new one, which keeps the reasoning visible.
- The records 0002 to 0014 were written after the fact, from the decisions taken while the project was built (October and November 2026); their dates are when each decision was made.
- The architecture documents stay the description of the present; the records are the history of why.
