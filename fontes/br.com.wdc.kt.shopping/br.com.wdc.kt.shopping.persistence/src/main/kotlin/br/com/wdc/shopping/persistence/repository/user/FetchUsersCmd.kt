package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import org.jdbi.v3.core.Jdbi
import java.io.StringReader
import java.sql.Connection

class FetchUsersCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, userId: Long, projection: User?): User? {
            val list = FetchUsersCmd().execute(connection, UserCriteria()
                .withUserId(userId)
                .withProjection(projection))
            return list.firstOrNull()
        }

        fun byCriteria(connection: Connection, criteria: UserCriteria): List<User> =
            FetchUsersCmd().execute(connection, criteria)

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

    fun execute(connection: Connection, criteria: UserCriteria): List<User> {
        val sql = SqlList()

        val cteUser = EnUser("cteUser")
        sql.ln(WITH, cteUser.alias, AS, '(')
        sql.ln(cteUser(criteria, criteria.projection, null, null).toText("  "))
        sql.ln(')')
        sql.ln(SELECT)

        val fieldsList = fields(criteria.projection, cteUser)
        val fJsonData = sql.strColumn(SqlUtils.toJsonField(fieldsList), AS, "json_data")
        sql.ln(FROM, cteUser.alias)

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)

            val userMap = mutableMapOf<Long, User>()
            return query.map { rs, _ -> fromJson(fJsonData(rs)!!, userMap) }.list()
        }
    }

    fun cteUser(criteria: UserCriteria?, prj: User?, superAlias: String?, superId: DbField?): SqlList {
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

        if (criteria == null) return sql

        val applier = ApplyUserCriteria(this)
        applier.criteria = criteria
        applier.root = u
        applier.apply(sql)

        criteria.orderBy?.let {
            when (it) {
                UserCriteria.OrderBy.ASCENDING -> sql.ln(ORDER_BY(u.id.asc()))
                UserCriteria.OrderBy.DESCENDING -> sql.ln(ORDER_BY(u.id.desc()))
            }
        }

        criteria.limit?.let { sql.ln(LIMIT, it) }
        criteria.offset?.let { sql.ln(OFFSET, it) }

        return sql
    }
}
