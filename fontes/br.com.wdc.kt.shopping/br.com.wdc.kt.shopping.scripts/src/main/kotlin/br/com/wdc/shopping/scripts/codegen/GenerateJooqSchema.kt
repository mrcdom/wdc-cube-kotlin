package br.com.wdc.shopping.scripts.codegen

import br.com.wdc.shopping.scripts.sgbd.DBCreate
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target

/**
 * Gera as classes jOOQ de `:shopping-persistence` a partir do esquema do próprio projeto.
 *
 * Estratégia: cria um H2 em memória, aplica todo o DDL por [DBCreate] (com as migrações) e roda o gerador
 * do jOOQ contra esse banco. O resultado é **versionado**, para o build não depender de rodar o gerador.
 *
 * A partir de `fontes/`:
 * ```
 * ./gradlew :shopping-scripts:generateJooqSchema
 * ```
 */
object GenerateJooqSchema {

    const val PACKAGE_NAME = "br.com.wdc.shopping.persistence.scheme"

    private const val JDBC_USER = "sa"
    private const val JDBC_PASSWORD = "codegen" // H2 efêmero em memória, só para a geração
    private val counter = AtomicInteger()

    /** @param args `[0]` = diretório de fontes de destino (o `src/main/kotlin` de `:shopping-persistence`) */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "informe o diretório de fontes de destino" }
        val outputDir = Paths.get(args[0]).toAbsolutePath().normalize()
        generate(outputDir)
        println("jOOQ: classes geradas em $outputDir ($PACKAGE_NAME)")
    }

    /** Gera o pacote [PACKAGE_NAME] sob [outputDir], substituindo o que houver nele. */
    fun generate(outputDir: Path) {
        val url = "jdbc:h2:mem:jooq_codegen_${counter.incrementAndGet()};DB_CLOSE_DELAY=-1"

        // mantém o banco em memória vivo até o fim da geração
        DriverManager.getConnection(url, JDBC_USER, JDBC_PASSWORD).use { connection ->
            DBCreate().withConnection(connection).withSkipReset().run()

            val configuration = Configuration()
                .withJdbc(Jdbc().withDriver("org.h2.Driver").withUrl(url).withUser(JDBC_USER).withPassword(JDBC_PASSWORD))
                .withGenerator(
                    Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(
                            Database()
                                .withName("org.jooq.meta.h2.H2Database")
                                .withInputSchema("PUBLIC")
                                .withIncludes(".*")
                                // o log de migração é assunto dos scripts; TO_BASE64 é a função do dialeto JSON
                                .withExcludes("EN_MIGRATION_LOG|SQ_MIGRATION_LOG|TO_BASE64")
                                .withIncludeForeignKeys(false)
                                .withIncludeIndexes(false)
                                .withIncludeRoutines(false)
                        )
                        .withGenerate(
                            Generate()
                                .withDeprecated(false)
                                .withRecords(false)
                                .withPojos(false)
                                .withDaos(false)
                                .withInterfaces(false)
                                // Só o essencial: tabelas, colunas, sequências e a chave primária — que o recorte
                                // de coleção filha usa (JsonQueryBuilder).
                                .withSequences(true)
                                .withKeys(true)
                                .withRelations(true)
                                .withIndexes(false)
                                .withRoutines(false)
                                .withComments(false)
                                .withJavadoc(false)
                                .withGeneratedAnnotation(false)
                        )
                        .withTarget(Target().withPackageName(PACKAGE_NAME).withDirectory(outputDir.toString()))
                )
            GenerationTool.generate(configuration)
        }
    }
}
