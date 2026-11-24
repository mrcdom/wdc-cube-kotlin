package br.com.wdc.store.test.repository

import br.com.wdc.store.persistence.repository.BaseRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rule that decides, field by field, what an `update` writes. */
class BaseRepositoryImplTest {

    private data class Thing(var id: Long? = null, var name: String? = null)

    /** Exposes the utility, which is `protected`: only repository implementations use it. */
    private object Probe : BaseRepositoryImpl() {
        fun nameChanged(newBean: Thing, oldBean: Thing?, projection: Thing) = changed(newBean, oldBean, projection) { it.name }
    }

    private val marked = Thing(name = "~")

    @Test
    fun withoutTheOldState_everyMarkedFieldIsWritten() {
        assertTrue(Probe.nameChanged(Thing(1L, "a"), null, marked))
        // including null, which clears the value
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
