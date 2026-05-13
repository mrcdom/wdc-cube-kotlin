package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.persistence.repository.BaseApplyCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.sql.SqlList
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ApplyUserCriteria(cmd: BaseCommand) : BaseApplyCriteria(cmd) {

    lateinit var root: EnUser
    lateinit var criteria: UserCriteria

    override fun apply(sql: SqlList) {
        criteria.userId?.let {
            sql.ln(AND, root.id, EQUAL, param("userId", it))
        }

        criteria.userName?.let {
            sql.ln(AND, root.userName, EQUAL, param("userName", it))
        }

        criteria.password?.let {
            val hashedPassword = BigInteger(md5().digest(it.toByteArray(StandardCharsets.UTF_8))).toString(36)
            sql.ln(AND, root.password, EQUAL, param("password", hashedPassword))
        }
    }

    private fun md5(): MessageDigest = MessageDigest.getInstance("MD5")
}
