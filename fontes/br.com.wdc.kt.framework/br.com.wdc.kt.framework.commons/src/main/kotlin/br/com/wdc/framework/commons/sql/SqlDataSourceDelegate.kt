package br.com.wdc.framework.commons.sql

import java.io.PrintWriter
import java.sql.Connection
import java.sql.ConnectionBuilder
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.ShardingKeyBuilder
import java.util.logging.Logger
import javax.sql.DataSource

class SqlDataSourceDelegate(impl: DataSource? = null) : SqlDataSource {

    private var impl: DataSource = impl ?: UNAVAILABLE

    fun setImpl(impl: DataSource?) {
        this.impl = impl ?: UNAVAILABLE
    }

    override fun <T : Any?> unwrap(iface: Class<T>): T = impl.unwrap(iface)
    override fun isWrapperFor(iface: Class<*>): Boolean = impl.isWrapperFor(iface)
    override fun getConnection(): Connection = impl.connection
    override fun getConnection(username: String?, password: String?): Connection = impl.getConnection(username, password)
    override fun getParentLogger(): Logger = impl.parentLogger
    override fun getLogWriter(): PrintWriter = impl.logWriter
    override fun setLogWriter(out: PrintWriter?) { impl.logWriter = out }
    override fun setLoginTimeout(seconds: Int) { impl.loginTimeout = seconds }
    override fun getLoginTimeout(): Int = impl.loginTimeout
    override fun createConnectionBuilder(): ConnectionBuilder = impl.createConnectionBuilder()
    override fun createShardingKeyBuilder(): ShardingKeyBuilder = impl.createShardingKeyBuilder()

    private companion object {
        val UNAVAILABLE: DataSource = object : DataSource {
            private fun unavailable(): Nothing = throw RuntimeException("Service unavailable")

            override fun getParentLogger(): Logger = unavailable()
            override fun <T : Any?> unwrap(iface: Class<T>): T = unavailable()
            override fun isWrapperFor(iface: Class<*>): Boolean = unavailable()
            override fun getConnection(): Connection = unavailable()
            override fun getConnection(username: String?, password: String?): Connection = unavailable()
            override fun getLogWriter(): PrintWriter = unavailable()
            override fun setLogWriter(out: PrintWriter?) = unavailable()
            override fun setLoginTimeout(seconds: Int) = unavailable()
            override fun getLoginTimeout(): Int = unavailable()
        }
    }
}
