package br.com.wdc.shopping.view.react

import br.com.wdc.shopping.view.react.supports.WorkDirectory
import org.h2.tools.Server
import java.nio.file.Path

object H2ServerMain {

    private const val DEFAULT_DB_NAME = "wedocode-shopping"

    @JvmStatic
    fun main(args: Array<String>) {
        val dataDir = resolveDataDir(args)
        val dbFile = dataDir.resolve(DEFAULT_DB_NAME).toAbsolutePath()

        val baseDir = dataDir.toAbsolutePath().toString()

        val tcpServer = Server.createTcpServer(
            "-tcp",
            "-tcpAllowOthers",
            "-tcpPort", "9092",
            "-baseDir", baseDir
        ).start()

        val webServer = Server.createWebServer(
            "-web",
            "-webAllowOthers",
            "-webPort", "8082",
            "-baseDir", baseDir
        ).start()

        println("==========================================================")
        println(" H2 Database Server started")
        println("==========================================================")
        println(" TCP Server : ${tcpServer.url}")
        println(" Web Console: ${webServer.url}")
        println()
        println(" JDBC URL   : jdbc:h2:tcp://localhost:9092/file:$dbFile")
        println(" User       : sa")
        println(" Password   : (empty)")
        println("==========================================================")
        println(" Press Ctrl+C to stop.")
        println("==========================================================")

        Runtime.getRuntime().addShutdownHook(Thread {
            tcpServer.stop()
            webServer.stop()
            println("H2 servers stopped.")
        })

        Thread.currentThread().join()
    }

    /** A pasta `data/` do diretório de trabalho informado (`--workdir=…` ou `SHOPPING_WORKDIR`). */
    private fun resolveDataDir(args: Array<String>): Path = WorkDirectory.resolve(args).resolve("data")
}
