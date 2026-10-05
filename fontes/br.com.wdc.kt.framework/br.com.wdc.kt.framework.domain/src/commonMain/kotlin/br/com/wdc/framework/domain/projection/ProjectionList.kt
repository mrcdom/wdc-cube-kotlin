package br.com.wdc.framework.domain.projection

/**
 * Coleção de projeção de uma relação 1:N — carrega o bean projetado (exatamente um elemento, a forma do
 * item), o critério da entidade filha e, opcionalmente, o recorte a aplicar.
 *
 * O critério traz também o `OrderBy`, e é dele que sai a ordem. Sem ordem, [withLimit] e [withOffset]
 * cortam linhas em ordem indefinida — ver [HasSlice].
 */
class ProjectionList<E> private constructor(
    private val delegate: MutableList<E>,
    override val criteria: Any?,
) : MutableList<E> by delegate, HasCriteria, HasSlice {

    constructor(bean: E, criteria: Any?) : this(ArrayList<E>(1), criteria) {
        add(bean)
    }

    override var limit: Int? = null
        private set

    override var offset: Int? = null
        private set

    /** Máximo de linhas filhas a trazer. Ordene também, ou o corte é arbitrário. */
    fun withLimit(limit: Int?): ProjectionList<E> = apply { this.limit = limit }

    /** Linhas filhas a pular antes de começar. Ordene também, ou o salto é arbitrário. */
    fun withOffset(offset: Int?): ProjectionList<E> = apply { this.offset = offset }

    // Igualdade de lista: critério e recorte não participam.
    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
