package br.com.wdc.framework.commons.log

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Log factory that delegates to `java.util.logging` (JUL).
 *
 * Best for GraalVM native-image targets (Android, iOS) where JUL is
 * built into the JDK and requires no reflection or service-loading.
 *
 * Usage:
 * ```
 * Log.setFactory(JulLogFactory())
 * ```
 */
class JulLogFactory : Log.Factory {

    override fun create(name: String): Log {
        val logger = Logger.getLogger(name)
        return Log(name).apply {
            errorOut = { msg, t -> logger.log(Level.SEVERE, msg, t) }
            warnOut = { msg, t -> logger.log(Level.WARNING, msg, t) }
            infoOut = { msg, t -> logger.log(Level.INFO, msg, t) }
            debugOut = { msg, t -> logger.log(Level.FINE, msg, t) }
            traceOut = { msg, t -> logger.log(Level.FINEST, msg, t) }
        }
    }
}
