# Framework

Bibliotecas base reutilizáveis, independentes da aplicação Shopping.

## Módulos

- [br.com.wdc.kt.framework.commons/](br.com.wdc.kt.framework.commons/) — Utilitários e tipos base (KMP)
- [br.com.wdc.kt.framework.cube/](br.com.wdc.kt.framework.cube/) — Engine Cube MVP (KMP)
- [br.com.wdc.kt.framework.domain/](br.com.wdc.kt.framework.domain/) — Contratos de acesso a dados: repositório, critérios, projeção, transação (KMP)
- [br.com.wdc.kt.framework.persistence/](br.com.wdc.kt.framework.persistence/) — Transações sobre JDBC e coordenador de transações remotas (JVM)
- [br.com.wdc.kt.framework.jooq/](br.com.wdc.kt.framework.jooq/) — Consultas declarativas sobre jOOQ, para H2 e PostgreSQL (JVM)

## Uso como dependência

`framework-commons` e `framework-cube` são publicados no Maven Central sob a licença [MIT](LICENSE). Alvos: JVM (Java 21), Android (minSdk 26), JS, WasmJS, iosArm64 e iosSimulatorArm64.

```kotlin
// build.gradle.kts de um projeto Kotlin Multiplatform
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mrcdom.wdc.kt:framework-cube:0.1.0") // já traz o framework-commons
        }
    }
}
```

## Publicação

A configuração fica em [`fontes/build.gradle.kts`](../build.gradle.kts) (bloco `publishedModules`), com o plugin `com.vanniktech.maven.publish`.

```bash
cd fontes

# Conferir os artefatos localmente (~/.m2), sem assinatura
./gradlew :framework-commons:publishToMavenLocal :framework-cube:publishToMavenLocal

# Enviar ao Maven Central (exige credenciais e chave GPG em ~/.gradle/gradle.properties)
./gradlew :framework-commons:publishToMavenCentral :framework-cube:publishToMavenCentral
```

O envio cria um *deployment* no [Central Portal](https://central.sonatype.com/publishing/deployments) que precisa ser liberado manualmente. Uma versão liberada não pode ser alterada nem removida.
