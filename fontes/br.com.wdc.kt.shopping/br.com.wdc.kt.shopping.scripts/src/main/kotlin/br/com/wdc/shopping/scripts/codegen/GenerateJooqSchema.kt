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
 * Generates the jOOQ classes of `:shopping-persistence` from the project's own schema.
 *
 * Strategy: creates an in-memory H2, applies the whole DDL through [DBCreate] (with the migrations) and runs the
 * jOOQ generator against that database. The result is **versioned**, so the build does not depend on running the
 * generator.
 *
 * From `fontes/`:
 * ```
 * ./gradlew :shopping-scripts:generateJooqSchema
 * ```
 */
object GenerateJooqSchema {

    const val PACKAGE_NAME = "br.com.wdc.shopping.persistence.scheme"

    private const val JDBC_USER = "sa"
    private const val JDBC_PASSWORD = "codegen" // ephemeral in-memory H2, for the generation only
    private val counter = AtomicInteger()

    /** @param args `[0]` = target source directory (the `src/main/kotlin` of `:shopping-persistence`) */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "provide the target source directory" }
        val outputDir = Paths.get(args[0]).toAbsolutePath().normalize()
        generate(outputDir)
        println("jOOQ: classes generated in $outputDir ($PACKAGE_NAME)")
    }

    /** Generates the [PACKAGE_NAME] package under [outputDir], replacing whatever is in it. */
    fun generate(outputDir: Path) {
        val url = "jdbc:h2:mem:jooq_codegen_${counter.incrementAndGet()};DB_CLOSE_DELAY=-1"

        // keeps the in-memory database alive until the generation ends
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
                                // the migration log is the scripts' business; TO_BASE64 is the JSON dialect's function
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
                                // Only the essentials: tables, columns, sequences and the primary key — which the
                                // child collection slicing uses (JsonQueryBuilder).
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
