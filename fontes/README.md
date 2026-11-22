# fontes

Root Gradle project containing all modules of the Storefront application.

## Modules

### Framework

- [br.com.wdc.kt.framework/](br.com.wdc.kt.framework/) — Framework base libraries

### Storefront application

- [br.com.wdc.kt.shopping/](br.com.wdc.kt.shopping/) — Storefront application modules

## Build

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Build the React client bundle the backend serves (not versioned; again after changing the client)
(cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.remote/remote.react/remote.react.client && npm install && npm run build)

# Run backend
./gradlew :backend:run
```
