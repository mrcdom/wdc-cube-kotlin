package br.com.wdc.framework.domain.projection

/**
 * Coleção de projeção de uma relação 1:N — carrega o bean projetado (exatamente um elemento, a forma do
 * item), o critério da entidade filha e, opcionalmente, o recorte a aplicar.
 *
 * O critério traz também o `OrderBy`, e é dele que sai a ordem. Sem ordem, [limit] e [offset]
 * cortam linhas em ordem indefinida — ver [HasSlice].
 *
 * @param bean a forma do item.
 * @param criteria o critério da entidade filha (com a ordem).
 * @param limit máximo de linhas filhas a trazer, ou `null` para todas. Ordene também, ou o corte é arbitrário.
 * @param offset linhas filhas a pular antes de começar, ou `null` para nenhuma. Ordene também, ou o salto é
 *   arbitrário.
 */
class ProjectionList<E> private constructor(
    private val delegate: MutableList<E>,
    override val criteria: Any?,
    override val limit: Int?,
    override val offset: Int?,
) : MutableList<E> by delegate, HasCriteria, HasSlice {

    constructor(bean: E, criteria: Any?, limit: Int? = null, offset: Int? = null) :
        this(ArrayList<E>(1), criteria, limit, offset) {
        add(bean)
    }

    /** Cópia com outro máximo de linhas filhas. */
    @Deprecated(
        "Passe o recorte ao construtor; sai na próxima versão",
        ReplaceWith("ProjectionList(first(), criteria, limit = limit, offset = offset)"),
    )
    fun withLimit(limit: Int?): ProjectionList<E> = ProjectionList(ArrayList(delegate), criteria, limit, offset)

    /** Cópia com outro salto de linhas filhas. */
    @Deprecated(
        "Passe o recorte ao construtor; sai na próxima versão",
        ReplaceWith("ProjectionList(first(), criteria, limit = limit, offset = offset)"),
    )
    fun withOffset(offset: Int?): ProjectionList<E> = ProjectionList(ArrayList(delegate), criteria, limit, offset)

    // Igualdade de lista: critério e recorte não participam.
    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
