package br.com.wdc.framework.rest.security

/**
 * Casa verbo e caminho da requisição, no estilo dos `requestMatchers` do Spring:
 *
 * - `"/auth/sign_in"` — qualquer verbo; `"POST /auth/sign_in"` — só POST;
 * - `*` casa parte de um segmento (`foto*.png`), `{x}` um segmento inteiro, `**` zero ou mais segmentos;
 * - a barra final é ignorada (`/a/` casa `/a`), como no Spring MVC.
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
            require(pattern.startsWith("/")) { "O padrão deve começar com '/': $spec" }
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
