package br.com.wdc.framework.domain.criteria

/**
 * Critério sobre um campo: os pedidos de comparação feitos e como eles se combinam.
 *
 * Esta classe já cobre o campo cuja única comparação sensata é de identidade — enum, booleano, chave
 * estrangeira. Campos ordenáveis e textuais usam as subclasses, que acrescentam o que o tipo permite.
 *
 * **Pedidos sucessivos acumulam, e por padrão valem juntos (`AND`).** É o que faz o intervalo montado em
 * duas linhas — `ge(inicio)` e depois `le(fim)`, como sai de dois campos de tela — significar o que se
 * espera, e o que faz `ne(1)` seguido de `ne(2)` excluir os dois.
 *
 * **Alternativa se pede com [or]**, que marca o campo como disjuntivo:
 * ```
 * criteria.name.or().startingWith("CAFE")
 * criteria.name.startingWith("CHA")
 * // (name ILIKE 'CAFE%' OR name ILIKE 'CHA%')
 * ```
 * A disjunção vale **dentro** do campo; entre campos diferentes a junção é sempre `AND`.
 *
 * **Valor nulo não acrescenta pedido**, em vez de apagar os anteriores: preserva o costume de montar
 * filtro a partir de campos de tela, onde vazio significa "não filtrar por isto". Para apagar o que já foi
 * pedido há [clear]; para comparar com nulo, [isNull].
 *
 * Nenhum método aqui conhece jOOQ ou SQL: a mesma classe vale no servidor e no cliente.
 *
 * @param C tipo do `XxxCriteria` que contém este campo
 * @param T tipo do valor comparado
 * @property name nome lógico do campo — é por ele que a tradução encontra a coluna correspondente
 */
open class Criterion<C, T : Any>(owner: C?, val name: String) {

    /** Um pedido de comparação: o operador e os valores que o acompanham. */
    class Predicate<T : Any> internal constructor(val operator: Operator, values: List<T?>) {

        val values: List<T?> = values.toList()

        /** Primeiro valor, ou `null` — atalho para os operadores de um valor só. */
        val value: T? get() = values.firstOrNull()

        override fun toString(): String = if (values.isEmpty()) operator.toString() else "$operator $values"
    }

    // Navegação, não estado: não trafega (fecharia um ciclo com o critério que o contém).
    private var owner: C? = owner

    private val requests = ArrayList<Predicate<T>>()

    /** Pedidos feitos sobre este campo, na ordem em que foram feitos. */
    val predicates: List<Predicate<T>> get() = requests.toList()

    /** `true` quando os pedidos deste campo se combinam com `OR`. */
    var disjunctive: Boolean = false
        private set

    /**
     * Devolve o dono a um critério que chegou sem ele — o caso de um critério reconstruído a partir do
     * transporte. Sem dono, os métodos fluentes não têm o que devolver.
     */
    fun rebind(owner: C) {
        this.owner = owner
    }

    /** `true` quando o critério ainda não sabe a que `XxxCriteria` pertence. */
    fun detached(): Boolean = owner == null

    /** `true` quando há ao menos um pedido, ou seja, quando este campo deve virar condição. */
    fun isSet(): Boolean = requests.isNotEmpty()

    /**
     * Marca o campo como disjuntivo: os pedidos passam a valer com `OR` entre si. Vale para o campo
     * inteiro, e não só para o pedido seguinte — chamar uma vez basta, em qualquer ponto.
     */
    open fun or(): Criterion<C, T> {
        disjunctive = true
        return this
    }

    /** Volta a exigir todos os pedidos (`AND`), desfazendo [or]. */
    open fun and(): Criterion<C, T> {
        disjunctive = false
        return this
    }

    /** Apaga os pedidos deste campo, voltando a não filtrar. Não desfaz [or]. */
    fun clear(): C {
        requests.clear()
        return owner()
    }

    /** Igualdade. `null` não acrescenta pedido. */
    fun eq(value: T?): C = if (value == null) owner() else add(Operator.EQ, value)

    /** Diferente de. `null` não acrescenta pedido. */
    fun ne(value: T?): C = if (value == null) owner() else add(Operator.NE, value)

    /**
     * Pertence ao conjunto. Conjunto nulo ou vazio não acrescenta pedido — filtrar por "nenhum" seria inútil.
     * (O operador no fio é `IN`; o nome é `isIn` porque `in` é palavra reservada em Kotlin.)
     */
    fun isIn(values: Collection<T>?): C {
        if (values.isNullOrEmpty()) {
            return owner()
        }
        requests.add(Predicate(Operator.IN, values.toList()))
        return owner()
    }

    /** Pertence ao conjunto. */
    fun isIn(vararg values: T): C = isIn(values.asList())

    fun isNull(): C = add(Operator.IS_NULL)

    fun isNotNull(): C = add(Operator.IS_NOT_NULL)

    /**
     * Repõe um pedido tal como veio do transporte, sem passar pelas regras dos métodos fluentes.
     *
     * Existe para a desserialização, e só para ela: `eq(null)` não acrescenta pedido, o que é o certo para
     * quem monta o filtro a partir de uma tela, mas apagaria em silêncio um `EQ NULL` que o outro lado enviou.
     */
    fun restore(operator: Operator, values: List<T?>?) {
        requests.add(Predicate(operator, values ?: emptyList()))
    }

    /** Marca a disjunção ao remontar o critério do transporte. */
    fun restoreDisjunctive(value: Boolean) {
        disjunctive = value
    }

    /** Acrescenta um pedido; visível às subclasses, que oferecem os operadores conforme o tipo do campo. */
    protected fun add(operator: Operator, vararg values: T?): C {
        requests.add(Predicate(operator, values.asList()))
        return owner()
    }

    /** O critério dono, para as subclasses devolverem ao encadear. */
    protected fun owner(): C =
        owner ?: throw IllegalStateException("critério '$name' sem dono — chame rebind() antes de encadear")

    override fun toString(): String =
        if (!isSet()) "$name (não informado)"
        else "$name ${if (disjunctive) "qualquer de " else "todos de "}$requests"
}
