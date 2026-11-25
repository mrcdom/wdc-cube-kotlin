# 0014. The backend runs from an explicit work directory

- **Date:** 2026-10-06
- **Status:** Accepted

## Context

The backend read its configuration from a path relative to wherever the process started, with fallbacks (`app.basedir`, a system property for the configuration file). Run from the IDE, from Gradle or from an installed distribution, it found different files, or none, and wrote data and logs in unexpected places.

## Decision

The backend does not start without a **work directory**, given by `--workdir=<folder>` or the `STORE_WORKDIR` variable; the folder must exist. There is no default relative to the process. Everything it uses comes from fixed subfolders (`StoreConfig`):

| Folder | Holds |
|---|---|
| `config/` | `application.toml` and the logging configuration |
| `data/` | the local database and other data kept between runs |
| `log/` | the log file and its rotations |
| `tmp/` | temporary files |
| `deployment/` | one subfolder per context of static files served (the web clients) |

In development the work directory is `fontes/work`. Its defaults are versioned (`config/application.example.toml`, `config/logback.xml`, the `deployment` skeleton); `config/application.toml` holds secrets and is not versioned, nor are the data, logs and deployed bundles. Code refers to the folders through `StoreConfig`, never to a fixed `work/…` path.

## Consequences

- The same binary behaves the same from anywhere; a second backend with another configuration is just another folder.
- A fresh clone starts with no security (no JWT secret); the example file says how to turn it on.
- Starting without the argument fails at once, with a message, instead of guessing.
