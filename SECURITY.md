# Security

## Reporting a vulnerability

Report it privately, through **GitHub's private vulnerability reporting**: the repository's *Security* tab → *Report a vulnerability*. Please do not open a public issue or pull request for it.

Say what is affected (a framework module and version, or the WDC Store demo), how to reproduce it, and what an attacker gains. You will get an answer as soon as possible; a fix for the framework goes out in its next version, with a row in the release notes.

## What is in scope

- **The framework** (`io.github.mrcdom.wdc.kt:*`): the latest published version, and the code on `main` that becomes the next one.
- **The WDC Store demo and the starter**, as examples: a flaw in them is likely to be copied into applications built from them, so it counts even though they are not products.

## Known limitations

The demo's security design, its trust boundaries and the limitations it accepts are described in [docs/security-architecture.md](docs/security-architecture.md) (for example: session storage in the browser is readable by a script injected into the page; counters of sign-in failures and requests are kept in memory, per instance). A report of one of those is welcome when it shows a way to make it worse than described.

A backend started without `security.jwt.secret` runs with its API open, by design, for development; see the work directory's example configuration.
