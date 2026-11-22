package br.com.wdc.framework.domain.doc

/**
 * The documentation of a type or a field that crosses the API: the request and the response of an operation.
 *
 * It lives in the domain, not in the REST layer, so that whoever declares the request and the response (the
 * application layer) documents them without depending on HTTP. The OpenAPI generator of `framework-rest` reads it:
 * on the type, it becomes the schema `description` (and the operation summary, with `describedBy`); on the field,
 * the property `description` and `example` (and those of a query parameter, with `queryOf`).
 *
 * On a `data class` field, the annotation without an explicit target lands on the Java field, which is what the
 * generator reads.
 *
 * @param value the text, as the documentation reader sees it
 * @param example an example value, as text (empty = no example)
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ApiDoc(val value: String, val example: String = "")
