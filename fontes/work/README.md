# work

The backend's **work directory** in development.

The backend does not start without knowing its work directory: it is given with the `--workdir=<folder>` argument or the `SHOPPING_WORKDIR` environment variable. `./gradlew :backend:run` passes this folder (another one: `-Pworkdir=/path`). Everything the service reads from and writes to disk comes from it:

| Folder | Purpose | In git |
|---|---|---|
| `config/` | The configuration and its supporting files: `application.toml` (local), `application.example.toml` (the template, with every key) and `logback.xml` (the log) | the defaults, yes; `application.toml`, no |
| `data/` | Local data reused across restarts — the default H2 database | no |
| `log/` | The log file and its rotations, as `config/logback.xml` says | no |
| `tmp/` | Temporary files created while running | no |
| `deployment/` | Each subfolder is a static resource context the server publishes (`compose/` → `/compose/`). The web frontends' `deploy.sh` scripts copy here | only the skeleton |

To get started: copy `config/application.example.toml` to `config/application.toml` and adjust it. Without that file the backend starts with the defaults — H2 database in `data/` and **no security**.

The directory must exist; missing subfolders are created on startup.
