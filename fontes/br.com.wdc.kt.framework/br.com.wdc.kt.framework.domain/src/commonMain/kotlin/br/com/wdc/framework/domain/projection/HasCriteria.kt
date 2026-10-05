package br.com.wdc.framework.domain.projection

/**
 * Coleção de projeção que carrega um critério de filtragem adicional, propagado à consulta da relação.
 */
interface HasCriteria {
    val criteria: Any?
}

/**
 * Coleção de projeção que recorta quantas linhas filhas trazer.
 *
 * **Recortar sem ordenar devolve linhas em ordem indefinida** — o banco não promete ordem nenhuma sem
 * `ORDER BY`. A ordem vem do critério que a coleção carrega, via [HasCriteria]: é lá que mora o `OrderBy`
 * da entidade filha.
 */
interface HasSlice {
    /** Máximo de linhas filhas, ou `null` para trazer todas. */
    val limit: Int?

    /** Quantas linhas pular antes de começar, ou `null` para começar da primeira. */
    val offset: Int?
}
