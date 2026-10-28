package br.com.wdc.framework.domain.criteria

/**
 * Critério de campo textual.
 *
 * Herda de [ComparableCriterion] porque texto também se ordena — `name.ge("M")` é consulta legítima — e
 * acrescenta as buscas por padrão.
 *
 * **Os curingas fazem parte do valor.** `like("%CAFE%")` é diferente de `like("CAFE")`, exatamente como em
 * SQL. Acrescentá-los automaticamente tiraria de quem escreve a consulta a capacidade de ancorar no início —
 * que é justamente o que decide se um índice será usado.
 *
 * **Em [like] e [ilike] o valor é padrão; em [containing] e [startingWith], é texto.** Os dois últimos escapam
 * `%`, `_` e a própria barra invertida do trecho antes de pôr os curingas: o que a pessoa digitou é procurado
 * literalmente, e `50%` não vira "começa com 50". Para montar à mão um padrão com texto de fora, [escape].
 *
 * @param C tipo do `XxxCriteria` que contém este campo
 */
class TextCriterion<C>(owner: C?, name: String) : ComparableCriterion<C, String>(owner, name) {

    // Retorno covariante: sem isto, or() devolveria a superclasse e o encadeamento perderia like e ilike.
    override fun or(): TextCriterion<C> {
        super.or()
        return this
    }

    override fun and(): TextCriterion<C> {
        super.and()
        return this
    }

    /** `LIKE`, sensível a maiúsculas. Texto nulo ou vazio não acrescenta pedido. */
    fun like(pattern: String?): C = if (pattern.isNullOrEmpty()) owner() else add(Operator.LIKE, pattern)

    /** `ILIKE`, insensível a maiúsculas. Texto nulo ou vazio não acrescenta pedido. */
    fun ilike(pattern: String?): C = if (pattern.isNullOrEmpty()) owner() else add(Operator.ILIKE, pattern)

    /** `ILIKE '%valor%'`, com o valor literal — o caso mais comum de busca por trecho. */
    fun containing(fragment: String?): C = if (fragment.isNullOrEmpty()) owner() else ilike("%${escape(fragment)}%")

    /** `ILIKE 'valor%'`, com o valor literal — ancorado no início, que é o que permite ao banco usar índice. */
    fun startingWith(prefix: String?): C = if (prefix.isNullOrEmpty()) owner() else ilike("${escape(prefix)}%")

    companion object {
        /** O caractere de escape dos padrões; o tradutor para SQL o declara (`ESCAPE '\'`). */
        const val ESCAPE: Char = '\\'

        /** O texto como literal num padrão: `%`, `_` e o próprio [ESCAPE] ganham o escape na frente. */
        fun escape(text: String): String = buildString(text.length) {
            for (ch in text) {
                if (ch == '%' || ch == '_' || ch == ESCAPE) append(ESCAPE)
                append(ch)
            }
        }
    }
}
