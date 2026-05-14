package br.com.wdc.shopping.presentation.presenter.open.login.structs

import br.com.wdc.framework.commons.serialization.Externalizable
import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionValues

class Subject : Externalizable {

    var id: Long? = null
    var nickName: String? = null

    override fun writeExternal(out: ExtensibleObjectOutput) {
        out.beginObject()
            .name("id").value(id!!)
            .name("nickName").value(nickName)
            .endObject()
    }

    override fun readExternal(input: ExtensibleObjectInput) {
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> id = InputCoerceUtils.asLong(input)
                "nickName" -> nickName = InputCoerceUtils.asString(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
    }

    companion object {
        fun projection(): User {
            val pv = ProjectionValues
            val prj = User()
            prj.id = pv.i64
            prj.name = pv.str
            return prj
        }

        fun create(src: User?): Subject? {
            if (src == null) return null
            val tgt = Subject()
            tgt.id = src.id
            tgt.nickName = src.name
            return tgt
        }
    }
}
