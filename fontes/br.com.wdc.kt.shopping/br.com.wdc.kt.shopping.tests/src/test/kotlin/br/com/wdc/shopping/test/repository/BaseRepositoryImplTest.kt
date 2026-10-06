package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.persistence.repository.BaseRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A regra que decide, campo a campo, o que um `update` grava. */
class BaseRepositoryImplTest {

    private data class Thing(var id: Long? = null, var name: String? = null)

    /** Expõe o utilitário, que é `protected`: só as implementações de repositório o usam. */
    private object Probe : BaseRepositoryImpl() {
        fun nameChanged(newBean: Thing, oldBean: Thing?, projection: Thing) = changed(newBean, oldBean, projection) { it.name }
    }

    private val marked = Thing(name = "~")

    @Test
    fun withoutTheOldState_everyMarkedFieldIsWritten() {
        assertTrue(Probe.nameChanged(Thing(1L, "a"), null, marked))
        // inclusive null, que limpa o valor
        assertTrue(Probe.nameChanged(Thing(1L, null), null, marked))
    }

    @Test
    fun withTheOldState_onlyWhatDiffersIsWritten() {
        assertTrue(Probe.nameChanged(Thing(1L, "a"), Thing(1L, "b"), marked))
        assertTrue(Probe.nameChanged(Thing(1L, null), Thing(1L, "b"), marked))
        assertFalse(Probe.nameChanged(Thing(1L, "a"), Thing(1L, "a"), marked))
    }

    @Test
    fun aFieldTheProjectionDoesNotMark_isNeverWritten() {
        assertFalse(Probe.nameChanged(Thing(1L, "a"), Thing(1L, "b"), Thing()))
        assertFalse(Probe.nameChanged(Thing(1L, "a"), null, Thing()))
    }
}
