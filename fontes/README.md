# fontes

Root Gradle project containing all modules of the WDC Store application.

## Modules

### Framework

- [br.com.wdc.kt.framework/](br.com.wdc.kt.framework/) — Framework base libraries

### WDC Store application

- [br.com.wdc.kt.store/](br.com.wdc.kt.store/) — WDC Store application modules

## Build

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Build the React client bundle the backend serves (not versioned; again after changing the client)
(cd br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript && npm install && npm run build)

# Run backend
./gradlew :backend:run
```
