# Framework

Bibliotecas base reutilizáveis, independentes da aplicação Shopping.

## Módulos

- [br.com.wdc.kt.framework.commons/](br.com.wdc.kt.framework.commons/) — Utilitários e tipos base (KMP)
- [br.com.wdc.kt.framework.cube/](br.com.wdc.kt.framework.cube/) — Engine Cube MVP (KMP)
- [br.com.wdc.kt.framework.domain/](br.com.wdc.kt.framework.domain/) — Contratos de acesso a dados: repositório, critérios, projeção, transação (KMP)
- [br.com.wdc.kt.framework.persistence/](br.com.wdc.kt.framework.persistence/) — Transações sobre JDBC e coordenador de transações remotas (JVM)
- [br.com.wdc.kt.framework.jooq/](br.com.wdc.kt.framework.jooq/) — Consultas declarativas sobre jOOQ, para H2 e PostgreSQL (JVM)

## Uso como dependência

Os cinco módulos são publicados no Maven Central, sob a licença [MIT](LICENSE), no grupo `io.github.mrcdom.wdc.kt`. Todos saem com a mesma versão.

| Artefato | Plataformas | Traz junto |
|---|---|---|
| `framework-commons` | JVM, Android, JS, WasmJS, iOS | — |
| `framework-cube` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-domain` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-persistence` | JVM | `framework-domain` |
| `framework-jooq` | JVM | `framework-persistence`, jOOQ |

Alvos: JVM (Java 21), Android (minSdk 26), JS, WasmJS, iosArm64 e iosSimulatorArm64. Compilados com Kotlin 2.3: quem consome nos alvos JS, WasmJS e iOS precisa de Kotlin 2.3 ou mais novo.

```kotlin
// build.gradle.kts de um projeto Kotlin Multiplatform
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mrcdom.wdc.kt:framework-cube:0.2.0")   // navegação e presenters
            implementation("io.github.mrcdom.wdc.kt:framework-domain:0.2.0") // contratos de acesso a dados
        }
    }
}

// build.gradle.kts de um módulo JVM de servidor
dependencies {
    implementation("io.github.mrcdom.wdc.kt:framework-jooq:0.2.0") // já traz persistence e domain
}
```

| Versão | O que trouxe |
|---|---|
| 0.2.0 | `framework-domain`, `framework-persistence` e `framework-jooq`; compilação com Kotlin 2.3 |
| 0.1.0 | `framework-commons` e `framework-cube` |

## Publicação

A configuração fica em [`fontes/build.gradle.kts`](../build.gradle.kts) (bloco `publishedModules`), com o plugin `com.vanniktech.maven.publish`. A versão é uma só, definida ali.

```bash
cd fontes

# Conferir os artefatos localmente (~/.m2), sem assinatura
./gradlew publishToMavenLocal

# Enviar ao Maven Central (exige credenciais e chave GPG em ~/.gradle/gradle.properties)
./gradlew publishToMavenCentral
```

As duas tarefas existem só nos módulos publicados. O envio cria um *deployment* no [Central Portal](https://central.sonatype.com/publishing/deployments) que precisa ser liberado manualmente. Uma versão liberada não pode ser alterada nem removida.
