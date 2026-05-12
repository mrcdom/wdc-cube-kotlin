package br.com.wdc.framework.commons.log

/**
 * Lightweight logging facade compatible with all runtimes (desktop, Android, iOS, Wasm).
 *
 * No reflection, no service-loading, no platform-specific blocking structures.
 *
 * Usage:
 * ```
 * private val LOG = Log.getLogger("MyClass")
 * LOG.warn("something happened: {}", detail)
 * ```
 */
class Log(val name: String) {

    enum class Level {
        ERROR, WARN, INFO, DEBUG, TRACE
    }

    fun interface Factory {
        fun create(name: String): Log
    }

    // :: Public API

    fun error(msg: String) = log(Level.ERROR, msg, null, null)
    fun error(msg: String, vararg args: Any?) {
        val t = extractThrowable(args)
        log(Level.ERROR, msg, args, t)
    }
    fun error(msg: String, t: Throwable?) = log(Level.ERROR, msg, null, t)

    fun warn(msg: String) = log(Level.WARN, msg, null, null)
    fun warn(msg: String, vararg args: Any?) {
        val t = extractThrowable(args)
        log(Level.WARN, msg, args, t)
    }
    fun warn(msg: String, t: Throwable?) = log(Level.WARN, msg, null, t)

    fun info(msg: String) = log(Level.INFO, msg, null, null)
    fun info(msg: String, vararg args: Any?) {
        val t = extractThrowable(args)
        log(Level.INFO, msg, args, t)
    }
    fun info(msg: String, t: Throwable?) = log(Level.INFO, msg, null, t)

    fun debug(msg: String) = log(Level.DEBUG, msg, null, null)
    fun debug(msg: String, vararg args: Any?) {
        val t = extractThrowable(args)
        log(Level.DEBUG, msg, args, t)
    }
    fun debug(msg: String, t: Throwable?) = log(Level.DEBUG, msg, null, t)

    fun trace(msg: String) = log(Level.TRACE, msg, null, null)
    fun trace(msg: String, vararg args: Any?) {
        val t = extractThrowable(args)
        log(Level.TRACE, msg, args, t)
    }
    fun trace(msg: String, t: Throwable?) = log(Level.TRACE, msg, null, t)

    val isDebugEnabled: Boolean get() = globalLevel.ordinal >= Level.DEBUG.ordinal
    val isTraceEnabled: Boolean get() = globalLevel.ordinal >= Level.TRACE.ordinal

    // :: Customization (used by Factory implementations)

    var errorOut: ((String, Throwable?) -> Unit)? = null
    var warnOut: ((String, Throwable?) -> Unit)? = null
    var infoOut: ((String, Throwable?) -> Unit)? = null
    var debugOut: ((String, Throwable?) -> Unit)? = null
    var traceOut: ((String, Throwable?) -> Unit)? = null

    // :: Internal

    private fun log(level: Level, msg: String, args: Array<out Any?>?, t: Throwable?) {
        if (level.ordinal > globalLevel.ordinal) return

        val formatted = format(msg, args)

        val out = when (level) {
            Level.ERROR -> errorOut
            Level.WARN -> warnOut
            Level.INFO -> infoOut
            Level.DEBUG -> debugOut
            Level.TRACE -> traceOut
        }

        if (out != null) {
            out(formatted, t)
        } else {
            defaultOutput(level, formatted, t)
        }
    }

    private fun defaultOutput(level: Level, formatted: String, t: Throwable?) {
        val line = "${level.name} [${shortName()}] $formatted"
        println(line)
        t?.printStackTrace()
    }

    private fun shortName(): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot + 1) else name
    }

    companion object {
        private var factoryRef: Factory = Factory { name -> Log(name) }

        var globalLevel: Level = Level.DEBUG

        fun setFactory(factory: Factory?) {
            factoryRef = factory ?: Factory { name -> Log(name) }
        }

        fun getLogger(name: String): Log = factoryRef.create(name)

        private fun format(msg: String?, args: Array<out Any?>?): String {
            if (msg == null) return ""
            if (args == null || args.isEmpty()) return msg

            val sb = StringBuilder(msg.length + 32)
            var argIdx = 0
            var i = 0
            while (i < msg.length) {
                if (i < msg.length - 1 && msg[i] == '{' && msg[i + 1] == '}') {
                    if (argIdx < args.size) {
                        sb.append(args[argIdx++])
                    } else {
                        sb.append("{}")
                    }
                    i += 2
                } else {
                    sb.append(msg[i])
                    i++
                }
            }
            return sb.toString()
        }

        private fun extractThrowable(args: Array<out Any?>?): Throwable? {
            if (args != null && args.isNotEmpty()) {
                val last = args[args.size - 1]
                if (last is Throwable) return last
            }
            return null
        }
    }
}
