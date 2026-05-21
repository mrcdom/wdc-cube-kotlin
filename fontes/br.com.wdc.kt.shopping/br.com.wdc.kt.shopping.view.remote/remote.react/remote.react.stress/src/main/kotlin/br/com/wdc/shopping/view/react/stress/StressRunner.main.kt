package br.com.wdc.shopping.view.react.stress

/**
 * Stress Test Runner for Remote React Skeleton
 *
 * Usage:
 *   ./gradlew :stress-remote-react:run
 *   ./gradlew :stress-remote-react:run --args="--clients=50 --duration=120 --url=http://localhost:8080"
 *
 * Prerequisites:
 *   - Backend server must be running (./gradlew :backend:run)
 *   - At least one user must exist (e.g., "admin"/"admin" from scripts)
 */
fun main(args: Array<String>) {
    val config = parseArgs(args)

    println()
    println("╔══════════════════════════════════════════════════╗")
    println("║   Remote React Skeleton — Stress Test Tool      ║")
    println("╚══════════════════════════════════════════════════╝")
    println()

    val runner = StressRunner(config)
    val report = runner.run()
    report.printReport()
}

private fun parseArgs(args: Array<String>): StressConfig {
    var config = StressConfig()

    for (arg in args) {
        when {
            arg.startsWith("--clients=") ->
                config = config.copy(clientCount = arg.substringAfter("=").toInt())
            arg.startsWith("--duration=") ->
                config = config.copy(durationMs = arg.substringAfter("=").toLong() * 1000)
            arg.startsWith("--url=") ->
                config = config.copy(baseUrl = arg.substringAfter("="))
            arg.startsWith("--rampup=") ->
                config = config.copy(rampUpMs = arg.substringAfter("=").toLong() * 1000)
            arg.startsWith("--warmup=") ->
                config = config.copy(warmupMs = arg.substringAfter("=").toLong() * 1000)
            arg.startsWith("--think=") ->
                config = config.copy(thinkTimeMs = arg.substringAfter("=").toLong())
            arg.startsWith("--user=") -> {
                val parts = arg.substringAfter("=").split(":")
                if (parts.size == 2) {
                    config = config.copy(users = config.users + UserCredentials(parts[0], parts[1]))
                }
            }
            arg == "--help" || arg == "-h" -> {
                printHelp()
                System.exit(0)
            }
        }
    }

    return config
}

private fun printHelp() {
    println("""
        Remote React Skeleton — Stress Test Tool
        
        Options:
          --clients=N      Number of virtual clients (default: 10)
          --duration=N     Test duration in seconds (default: 60)
          --rampup=N       Ramp-up time in seconds (default: 5)
          --warmup=N       Warmup time in seconds (default: 5)
          --think=N        Think time between actions in ms (default: 500)
          --url=URL        Server base URL (default: http://localhost:8080)
          --user=NAME:PASS Add a user credential (default: admin:admin)
          --help, -h       Show this help
        
        Example:
          ./gradlew :stress-remote-react:run --args="--clients=50 --duration=120"
    """.trimIndent())
}
