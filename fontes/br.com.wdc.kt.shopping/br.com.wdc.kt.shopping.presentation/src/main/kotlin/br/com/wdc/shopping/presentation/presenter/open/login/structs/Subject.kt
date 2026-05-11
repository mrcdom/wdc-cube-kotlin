package br.com.wdc.shopping.presentation.presenter.open.login.structs

import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionValues

class Subject {

    var id: Long? = null
    var nickName: String? = null

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
