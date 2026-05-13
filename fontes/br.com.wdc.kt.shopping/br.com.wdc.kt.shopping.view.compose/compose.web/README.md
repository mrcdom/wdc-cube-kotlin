# compose.web

Ponto de entrada **Web** da aplicação Compose. Compila para **wasmJs** e roda no browser via webpack dev server (porta 8082).

```bash
cd fontes && ./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun
```

O webpack proxy redireciona `/api/*` para o backend na porta 8080.
