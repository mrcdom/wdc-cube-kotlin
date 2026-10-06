package br.com.wdc.framework.domain.criteria

/**
 * Operador de comparação de um [Criterion], com a mesma semântica do SQL.
 *
 * É deliberadamente **neutro**: não conhece jOOQ nem SQL, apenas nomeia a comparação. Quem traduz para
 * condição é o `framework-jooq`; o cliente REST serializa o mesmo enum.
 *
 * @property arity quantidade de valores que o operador exige; negativo quando é variável ([IN])
 */
enum class Operator(val arity: Int) {
    /** `= ?` */
    EQ(1),
    /** `<> ?` */
    NE(1),
    /** `> ?` */
    GT(1),
    /** `>= ?` */
    GE(1),
    /** `< ?` */
    LT(1),
    /** `<= ?` */
    LE(1),
    /** `LIKE ?` — sensível a maiúsculas; os curingas fazem parte do valor. */
    LIKE(1),
    /** `ILIKE ?` — insensível a maiúsculas. */
    ILIKE(1),
    /** `BETWEEN ? AND ?` */
    BETWEEN(2),
    /** `IN (?, ?, …)` — aridade variável. */
    IN(-1),
    /** `IS NULL` */
    IS_NULL(0),
    /** `IS NOT NULL` */
    IS_NOT_NULL(0);

    /** `true` quando a quantidade informada satisfaz o operador. */
    fun accepts(count: Int): Boolean = if (arity < 0) count > 0 else count == arity
}
