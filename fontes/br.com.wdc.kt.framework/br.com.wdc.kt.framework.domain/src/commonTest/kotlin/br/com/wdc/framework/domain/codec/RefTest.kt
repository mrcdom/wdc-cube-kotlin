package br.com.wdc.framework.domain.codec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefTest {

    class Customer : KeyedEntity {
        var id: Long? = null
        var name: String? = null
        override fun key(): Any? = id
    }

    @Test
    fun ref_buildsTheEntityWithOnlyTheKey() {
        val customer = ref(::Customer, 7L) { id = it }!!
        assertEquals(7L, customer.id)
        assertNull(customer.name)
    }

    @Test
    fun ref_ofNull_isNull_andDoesNotCreate() {
        var created = 0
        assertNull(ref({ created++; Customer() }, null as Long?) { id = it })
        assertEquals(0, created)
    }
}
