package br.com.wdc.framework.domain.doc

/**
 * A documentação de um tipo ou de um campo que atravessa a API: o pedido e a resposta de uma operação.
 *
 * Fica no domínio, e não na camada REST, para que quem declara o pedido e a resposta (a camada de aplicação) os
 * documente sem depender do HTTP. O gerador de OpenAPI do `framework-rest` a lê: no tipo, vira a `description` do
 * esquema (e o resumo da operação, com `describedBy`); no campo, a `description` e o `example` da propriedade (e de
 * um parâmetro de query, com `queryOf`).
 *
 * Num campo de `data class`, a anotação sem alvo explícito cai no campo Java, que é o que o gerador lê.
 *
 * @param value o texto, como o leitor da documentação o vê
 * @param example um valor de exemplo, em texto (vazio = sem exemplo)
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ApiDoc(val value: String, val example: String = "")
