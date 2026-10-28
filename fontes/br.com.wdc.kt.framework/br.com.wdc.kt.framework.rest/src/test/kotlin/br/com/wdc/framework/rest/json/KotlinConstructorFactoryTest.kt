package br.com.wdc.framework.rest.json

import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Leitura de classes Kotlin pelo construtor primário. A tarefa `testFinalFields` roda esta classe no JDK 26 com a
 * gravação de campo final por reflexão bloqueada: se a leitura voltar a gravar `val` por reflexão, ela falha.
 */
class KotlinConstructorFactoryTest {

    data class Order(val id: Long, val customer: String, val note: String?)

    data class WithDefaults(val id: Long = 0, val status: String = "open", val tags: List<String> = emptyList(), val note: String? = "none")

    data class Named(@SerializedName("customer_name", alternate = ["customerName", "name"]) val customer: String)

    data class Page<T>(val count: Int, val elements: List<T>)

    data class Validated(val quantity: Int) {
        init {
            require(quantity > 0) { "quantity deve ser positiva" }
        }
    }

    class WithBody(val id: Long) {
        var label: String? = null
        val fixed: String = "fixo"
    }

    data class Nested(val order: Order, val due: LocalDate?)

    private val mapper = GsonJsonMapper(JsonOptions.springDefaults())

    @Test
    fun readsValClasses_throughTheConstructor() {
        assertEquals(Order(7, "Ana", null), mapper.fromJson<Order>("""{"id":7,"customer":"Ana"}"""))
        assertEquals(Order(7, "Ana", "x"), mapper.fromJson<Order>("""{"id":7,"customer":"Ana","note":"x","unknown":1}"""))
    }

    @Test
    fun defaults_applyToAbsentFields_andToExplicitNullInNonNullableOnes() {
        assertEquals(WithDefaults(), mapper.fromJson<WithDefaults>("{}"))
        assertEquals(WithDefaults(id = 3, status = "open"), mapper.fromJson<WithDefaults>("""{"id":3,"status":null}"""))
        // num anulável, o null explícito vence o padrão
        assertNull(mapper.fromJson<WithDefaults>("""{"note":null}""").note)
        assertEquals(listOf("a"), mapper.fromJson<WithDefaults>("""{"tags":["a"]}""").tags)
    }

    @Test
    fun requiredFields_missingOrNull_areRefused() {
        val missing = assertFailsWith<JsonParseException> { mapper.fromJson<Order>("""{"id":7}""") }
        assertTrue("customer" in missing.message!!)
        val nulled = assertFailsWith<JsonParseException> { mapper.fromJson<Order>("""{"id":7,"customer":null}""") }
        assertTrue("customer" in nulled.message!!)
    }

    @Test
    fun serializedName_andItsAlternates() {
        assertEquals("Ana", mapper.fromJson<Named>("""{"customer_name":"Ana"}""").customer)
        assertEquals("Bia", mapper.fromJson<Named>("""{"customerName":"Bia"}""").customer)
        assertEquals("Caio", mapper.fromJson<Named>("""{"name":"Caio"}""").customer)
        assertEquals("""{"customer_name":"Ana"}""", mapper.toJson(Named("Ana")))
    }

    @Test
    fun genericClasses_resolveTheirTypeArguments() {
        val page = mapper.fromJson<Page<Order>>("""{"count":1,"elements":[{"id":1,"customer":"Ana"}]}""")
        assertEquals(Order(1, "Ana", null), page.elements.single())
        assertEquals(listOf(Order(1, "Ana", null)), mapper.fromJson<List<Order>>("""[{"id":1,"customer":"Ana"}]"""))
    }

    @Test
    fun validationInInit_surfacesAsItsOwnException() {
        val e = assertFailsWith<IllegalArgumentException> { mapper.fromJson<Validated>("""{"quantity":0}""") }
        assertEquals("quantity deve ser positiva", e.message)
    }

    @Test
    fun bodyVar_isWrittenAfterConstruction_bodyVal_isLeftAlone() {
        val value = mapper.fromJson<WithBody>("""{"id":1,"label":"x","fixed":"outro"}""")
        assertEquals("x", value.label)
        assertEquals("fixo", value.fixed)
    }

    @Test
    fun nestedObjects_andDates_andNullRoot() {
        val value = mapper.fromJson<Nested>("""{"order":{"id":2,"customer":"Ana"},"due":"2026-11-03"}""")
        assertEquals(Order(2, "Ana", null), value.order)
        assertEquals(LocalDate.of(2026, 11, 3), value.due)
        assertNull(mapper.fromJson<Nested?>("null"))
        assertFailsWith<JsonParseException> { mapper.fromJson<Nested>("""{"order":[1]}""") }
    }

    @Test
    fun writing_isUnchanged() {
        assertEquals("""{"id":7,"customer":"Ana","note":null}""", mapper.toJson(Order(7, "Ana", null)))
        assertEquals("""{"id":7,"customer":"Ana"}""", GsonJsonMapper().toJson(Order(7, "Ana", null)))
    }
}
