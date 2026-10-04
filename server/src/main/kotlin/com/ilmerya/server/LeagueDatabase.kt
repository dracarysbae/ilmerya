package com.ilmerya.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager

/** Use the JVM's trusted CA bundle in containers, without a user-local root.crt file. */
internal fun verifiedPostgresUrl(url: String): String {
    require(url.startsWith("jdbc:postgresql://")) { "Expected a PostgreSQL JDBC URL" }
    val properties = requireNotNull(org.postgresql.Driver.parseURL(url, null)) { "Invalid PostgreSQL URL" }
    val local = properties.getProperty("PGHOST").split(',').all { it == "localhost" || it == "127.0.0.1" }
    if (local) return url
    val mode = properties.getProperty("sslmode")
    // Provider strings often say "require" (encryption only). Upgrade it to full certificate and
    // hostname verification; a mode that permits plaintext is refused outright.
    require(mode in listOf(null, "require", "verify-ca", "verify-full")) {
        "Remote PostgreSQL requires certificate and hostname verification"
    }
    if (mode != "verify-full") {
        val upgraded = if (mode == null) url + (if ('?' in url) "&" else "?") + "sslmode=verify-full"
            else url.replace(Regex("([?&])sslmode=$mode(?=&|$)"), "$1sslmode=verify-full")
        return verifiedPostgresUrl(upgraded)
    }
    val factory = "org.postgresql.ssl.DefaultJavaSSLFactory"
    require(properties.getProperty("sslfactory") in listOf(null, factory)) {
        "Remote PostgreSQL requires the JVM trusted certificate factory"
    }
    require(properties.getProperty("sslhostnameverifier") == null) {
        "Custom hostname verification is not allowed"
    }
    return if (properties.getProperty("sslfactory") == factory) url
    else url + (if ('?' in url) "&" else "?") + "sslfactory=$factory"
}

/** SQLite for local tests; externally hosted PostgreSQL for disposable application servers. */
internal class LeagueDatabase(path: String, jdbcUrl: String?) : AutoCloseable {
    val postgres = !jdbcUrl.isNullOrBlank()
    private val active = ThreadLocal<Connection>()
    private val sqlite: Connection?
    private val pool: HikariDataSource?

    init {
        if (postgres) {
            val secureUrl = verifiedPostgresUrl(jdbcUrl!!)
            sqlite = null
            pool = HikariDataSource(HikariConfig().apply {
                this.jdbcUrl = secureUrl
                maximumPoolSize = 2
                minimumIdle = 0
                idleTimeout = 60_000
                maxLifetime = 240_000
                keepaliveTime = 0 // Allow the free database compute to sleep when unused.
                connectionTimeout = 30_000
                validationTimeout = 5_000
                initializationFailTimeout = -1
                poolName = "league"
            })
        } else {
            pool = null
            Class.forName("org.sqlite.JDBC")
            sqlite = DriverManager.getConnection("jdbc:sqlite:$path")
            sqlite.createStatement().use {
                it.execute("PRAGMA foreign_keys=ON")
                it.execute("PRAGMA journal_mode=WAL")
                it.execute("PRAGMA busy_timeout=5000")
                it.execute("PRAGMA secure_delete=ON")
            }
        }
    }

    fun <T> connection(block: (Connection) -> T): T {
        active.get()?.let { return block(it) }
        val connection = sqlite ?: pool!!.connection
        active.set(connection)
        try { return block(connection) }
        finally {
            active.remove()
            if (postgres) connection.close()
        }
    }

    fun <T> transaction(block: () -> T): T = connection { db ->
        check(db.autoCommit) { "Nested transaction" }
        db.autoCommit = false
        try {
            // Serializes writes across overlapping Render deployments as well as this instance.
            // Transaction-scoped locks also work through a transaction-mode pooler.
            if (postgres) db.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(24003541)") }
            val result = block()
            db.commit()
            result
        } catch (failure: Throwable) {
            try { db.rollback() } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        } finally { db.autoCommit = true }
    }

    override fun close() { pool?.close(); sqlite?.close() }
}
