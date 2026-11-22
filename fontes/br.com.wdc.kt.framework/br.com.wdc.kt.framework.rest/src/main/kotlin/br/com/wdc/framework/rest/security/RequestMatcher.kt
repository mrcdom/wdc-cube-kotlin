package br.com.wdc.framework.rest.security

/**
 * Matches the request's verb and path, in the style of Spring's `requestMatchers`:
 *
 * - `"/auth/login"` — any verb; `"POST /auth/login"` — POST only;
 * - `*` matches part of a segment (`photo*.png`), `{x}` a whole segment, `**` zero or more segments;
 * - the trailing slash is ignored (`/a/` matches `/a`), as in Spring MVC.
 */
class RequestMatcher private constructor(val method: String?, val pattern: String, private val regex: Regex) {

    fun matches(method: String, path: String): Boolean =
        (this.method == null || this.method.equals(method, ignoreCase = true)) && regex.matches(normalize(path))

    override fun toString(): String = if (method == null) pattern else "$method $pattern"

    companion object {

        fun of(spec: String): RequestMatcher {
            val trimmed = spec.trim()
            val space = trimmed.indexOf(' ')
            val method = if (space > 0) trimmed.substring(0, space).uppercase() else null
            val pattern = if (space > 0) trimmed.substring(space + 1).trim() else trimmed
            require(pattern.startsWith("/")) { "The pattern must start with '/': $spec" }
            return RequestMatcher(method, pattern, compile(normalize(pattern)))
        }

        private fun normalize(path: String): String = if (path.length > 1 && path.endsWith("/")) path.trimEnd('/') else path

        private fun compile(pattern: String): Regex {
            val segments = pattern.split('/').drop(1)
            val sb = StringBuilder("^")
            for (segment in segments) {
                when {
                    segment == "**" -> sb.append("(?:/[^/]*)*")
                    segment.startsWith("{") && segment.endsWith("}") -> sb.append("/[^/]+")
                    segment.startsWith("<") && segment.endsWith(">") -> sb.append("(?:/[^/]*)+")
                    else -> {
                        sb.append('/')
                        segment.split('*').forEachIndexed { i, part ->
                            if (i > 0) sb.append("[^/]*")
                            if (part.isNotEmpty()) sb.append(Regex.escape(part))
                        }
                    }
                }
            }
            if (segments.isEmpty() || pattern == "/") sb.append("/?")
            sb.append("$")
            return Regex(sb.toString())
        }
    }
}
