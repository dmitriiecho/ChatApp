package krio.systemdesign.chatapp.server.db

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The server's SQLite file behind one connection. Every read and write is a transaction on one thread at a time:
// a write's checks ("is this clientId new?", "what is the next seq?") then hold until it commits.
class Database private constructor(private val connection: Connection) : AutoCloseable {

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun <T> transaction(block: Transaction.() -> T): T = withContext(dispatcher) {
        connection.autoCommit = false
        try {
            Transaction(connection).block().also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() = connection.close()

    companion object {
        fun open(path: String): Database = connect("jdbc:sqlite:$path")

        // For tests: gone when closed.
        fun inMemory(): Database = connect("jdbc:sqlite::memory:")

        private fun connect(url: String): Database {
            val connection = DriverManager.getConnection(url)
            connection.createStatement().use { statement ->
                // WAL: a crash mid-write leaves the file whole.
                statement.execute("PRAGMA journal_mode = WAL")
                statement.execute("PRAGMA foreign_keys = ON")
                Schema.statements.forEach(statement::execute)
            }
            return Database(connection)
        }
    }
}

// What a transaction can do: plain SQL with ? parameters (String, Long, Int, Boolean or null).
class Transaction internal constructor(private val connection: Connection) {

    fun update(
        sql: String,
        vararg args: Any?,
    ): Int = prepare(sql, args).use { it.executeUpdate() }

    // Inserts a row and returns its rowid (an AUTOINCREMENT key).
    fun insert(
        sql: String,
        vararg args: Any?,
    ): Long {
        update(sql, *args)
        return queryOne("SELECT last_insert_rowid()") { it.getLong(1) }!!
    }

    fun <T> query(
        sql: String,
        vararg args: Any?,
        map: (ResultSet) -> T,
    ): List<T> = prepare(sql, args).use { statement ->
        statement.executeQuery().use { rows ->
            buildList { while (rows.next()) add(map(rows)) }
        }
    }

    fun <T> queryOne(
        sql: String,
        vararg args: Any?,
        map: (ResultSet) -> T,
    ): T? = query(sql, *args, map = map).firstOrNull()

    private fun prepare(
        sql: String,
        args: Array<out Any?>,
    ): PreparedStatement {
        val statement = connection.prepareStatement(sql)
        args.forEachIndexed { index, arg ->
            // SQLite has no BOOLEAN: 0 and 1.
            statement.setObject(index + 1, if (arg is Boolean) (if (arg) 1 else 0) else arg)
        }
        return statement
    }
}

// A nullable Long column: getLong reads NULL as 0.
fun ResultSet.getLongOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }
