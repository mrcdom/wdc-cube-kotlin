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

    /** `ILIKE '%valor%'` — o caso mais comum de busca por trecho, com os curingas postos por conveniência. */
    fun containing(fragment: String?): C = if (fragment.isNullOrEmpty()) owner() else ilike("%$fragment%")

    /** `ILIKE 'valor%'` — ancorado no início, que é o que permite ao banco usar índice. */
    fun startingWith(prefix: String?): C = if (prefix.isNullOrEmpty()) owner() else ilike("$prefix%")
}
