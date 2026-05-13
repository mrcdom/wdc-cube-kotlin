package br.com.wdc.shopping.view.react

import org.h2.tools.Server
import java.nio.file.Path
import java.nio.file.Paths

object H2ServerMain {

    private const val DEFAULT_DB_NAME = "wedocode-shopping"

    @JvmStatic
    fun main(args: Array<String>) {
        val dataDir = resolveDataDir()
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

    private fun resolveDataDir(): Path {
        val configuredDir = System.getProperty("wedocode.shopping.runtime.dir")
        val baseDir = if (!configuredDir.isNullOrBlank()) Paths.get(configuredDir) else Paths.get("work")
        return baseDir.toAbsolutePath().normalize().resolve("data")
    }
}
