package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import java.io.StringReader

/**
 * O que resta do acesso JDBI a usuário: a projeção do usuário embutida nas consultas de compra e de item de
 * compra, que ainda não foram portadas para jOOQ. As operações do repositório estão em [UserRepositoryImpl].
 */
class FetchUsersCmd : BaseCommand() {

    companion object {
        fun fields(prj: User?, en: EnUser): List<DbField> {
            val pv = ProjectionValues
            var p = prj
            if (p == null) {
                p = User()
                p.name = pv.str
                p.userName = pv.str
                p.name = pv.str
            }
            p.id = pv.i64

            val fields = mutableListOf<DbField>()
            if (p.id != null) fields.add(en.id)
            if (p.userName != null) fields.add(en.userName)
            if (p.password != null) fields.add(en.password)
            if (p.name != null) fields.add(en.name)
            if (p.roles != null) fields.add(en.roles)
            return fields
        }

        fun fromJson(json: String, userMap: MutableMap<Long, User>): User {
            JsonReader(StringReader(json)).use { reader ->
                val row = EnUser.Row.parseJson(reader)

                val user = userMap.getOrPut(row.id!!) {
                    User().also { it.id = row.id }
                }

                if (user.userName == null) user.userName = row.userName
                if (user.password == null) user.password = row.password
                if (user.name == null) user.name = row.name
                if (user.roles == null) user.roles = row.roles
                return user
            }
        }
    }

    fun cteUser(prj: User?, superAlias: String?, superId: DbField?): SqlList {
        val u = EnUser("U")

        val sql = SqlList()
        sql.ln(SELECT)
        fields(prj, u).forEach { sql.field(it) }
        sql.ln(FROM, u.tableRef())
        sql.ln(WHERE_TRUE)

        if (superAlias != null) {
            sql.ln(AND, EXISTS { ll ->
                ll.ln(SELECT, 1)
                ll.ln(FROM, superAlias)
                ll.ln(WHERE, superId, EQUAL, u.id)
            })
        }

        return sql
    }
}
