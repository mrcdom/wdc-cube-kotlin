package br.com.wdc.framework.commons.log

import org.slf4j.LoggerFactory

/**
 * Log factory that delegates to SLF4J.
 *
 * Best for server-side and desktop JVM applications where Logback
 * (or another SLF4J provider) is available on the classpath.
 *
 * Usage:
 * ```
 * Log.setFactory(Slf4jLogFactory())
 * ```
 */
class Slf4jLogFactory : Log.Factory {

    override fun create(name: String): Log {
        val logger = LoggerFactory.getLogger(name)
        return Log(name).apply {
            errorOut = { msg, t -> if (t != null) logger.error(msg, t) else logger.error(msg) }
            warnOut = { msg, t -> if (t != null) logger.warn(msg, t) else logger.warn(msg) }
            infoOut = { msg, t -> if (t != null) logger.info(msg, t) else logger.info(msg) }
            debugOut = { msg, t -> if (t != null) logger.debug(msg, t) else logger.debug(msg) }
            traceOut = { msg, t -> if (t != null) logger.trace(msg, t) else logger.trace(msg) }
        }
    }
}
