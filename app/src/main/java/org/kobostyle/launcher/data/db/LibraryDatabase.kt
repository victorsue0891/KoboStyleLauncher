package org.kobostyle.launcher.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val identity: String,
    val uri: String, val sourceUri: String, val documentId: String,
    val title: String, val author: String, val mime: String,
    val lastModified: Long, val size: Long,
    val lastOpenedAt: Long = 0, val coverPath: String? = null,
    val scanStatus: String = "ok", val scanToken: String = "",
    @ColumnInfo(defaultValue = "''") val displayName: String = ""
)
@Entity(tableName = "sources")
data class Source(@PrimaryKey val uri: String, val tree: Boolean, val status: String = "ok")

@Entity(tableName = "catalog_changes")
data class CatalogChange(@PrimaryKey val identity: String, val revision: String)
@Entity(tableName = "scan_jobs")
data class ScanJob(@PrimaryKey val source: String, val token: String, val books: Int = 0, val failures: Int = 0)
@Entity(tableName = "scan_nodes", primaryKeys = ["source", "id"], indices = [Index(value = ["source", "done"])])
data class ScanNode(val source: String, val id: String, val uri: String, val name: String, val mime: String,
    val size: Long, val modified: Long, val depth: Int, val done: Boolean = false)

@Dao
abstract class LibraryDao {
    @Query("SELECT * FROM books WHERE identity > :after ORDER BY identity LIMIT 50") abstract suspend fun page(after: String): List<Book>
    @Query("SELECT * FROM catalog_changes ORDER BY identity LIMIT 50") abstract suspend fun changes(): List<CatalogChange>
    @Query("DELETE FROM catalog_changes WHERE identity = :id AND revision = :revision") abstract suspend fun ack(id: String, revision: String)
    @Transaction open suspend fun acknowledge(changes: List<CatalogChange>) { changes.forEach { ack(it.identity, it.revision) } }
    @Query("SELECT COUNT(*) FROM books") abstract suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM catalog_changes") abstract fun observeChanges(): Flow<Int>
    @Query("SELECT * FROM scan_jobs WHERE source = :source") abstract suspend fun scanJob(source: String): ScanJob?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveJob(job: ScanJob)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun enqueue(node: ScanNode)
    @Query("SELECT * FROM scan_nodes WHERE source = :source AND done = 0 ORDER BY depth,id LIMIT 1") abstract suspend fun nextNode(source: String): ScanNode?
    @Query("SELECT COUNT(*) FROM scan_nodes WHERE source = :source") abstract suspend fun nodeCount(source: String): Int
    @Query("UPDATE scan_nodes SET done = 1 WHERE source = :source AND id = :id") abstract suspend fun done(source: String, id: String)
    @Query("DELETE FROM scan_nodes WHERE source = :source") abstract suspend fun clearNodes(source: String)
    @Query("DELETE FROM scan_jobs WHERE source = :source") abstract suspend fun clearJob(source: String)
    @Transaction open suspend fun startScan(job: ScanJob, root: ScanNode) { clearNodes(job.source); saveJob(job); enqueue(root) }
    @Transaction open suspend fun finishNode(node: ScanNode, job: ScanJob, book: Book?) {
        if (book != null) index(book)
        done(node.source, node.id); saveJob(job)
    }
    @Transaction open suspend fun completeScan(source: String, token: String) {
        markMissing(source, token); clearNodes(source); clearJob(source)
    }
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, title COLLATE NOCASE ASC")
    abstract fun observeBooks(): Flow<List<Book>>
    @Query("SELECT * FROM sources") abstract fun observeSources(): Flow<List<Source>>
    @Query("SELECT * FROM sources") abstract suspend fun sources(): List<Source>
    @Query("SELECT * FROM books WHERE identity = :id") abstract suspend fun book(id: String): Book?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveSource(source: Source)
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun insert(book: Book)
    @Transaction open suspend fun index(book: Book) {
        // A rescan must never erase an open event written while indexing.
        insert(book.copy(lastOpenedAt = book(book.identity)?.lastOpenedAt ?: 0))
    }
    @Query("UPDATE books SET lastOpenedAt = :time WHERE identity = :id") abstract suspend fun opened(id: String, time: Long)
    @Query("UPDATE books SET scanStatus = :status WHERE sourceUri = :source") abstract suspend fun sourceStatus(source: String, status: String)
    @Query("UPDATE books SET scanStatus = :status WHERE identity = :id") abstract suspend fun bookStatus(id: String, status: String)
    @Query("UPDATE books SET scanStatus = 'unavailable' WHERE sourceUri = :source AND scanToken != :token")
    abstract suspend fun markMissing(source: String, token: String)
}
@Database(entities = [Book::class, Source::class, CatalogChange::class, ScanJob::class, ScanNode::class], version = 3, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        private fun tracking(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            for (event in listOf("INSERT", "UPDATE", "DELETE")) {
                val row = if (event == "DELETE") "old" else "new"
                db.execSQL("CREATE TRIGGER IF NOT EXISTS books_change_${event.lowercase()} AFTER $event ON books BEGIN INSERT OR REPLACE INTO catalog_changes(identity,revision) VALUES($row.identity,lower(hex(randomblob(16)))); END")
            }
        }
        val TRACKING = object : Callback() { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { tracking(db) } }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog_changes (identity TEXT NOT NULL PRIMARY KEY, revision TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_jobs (source TEXT NOT NULL PRIMARY KEY, token TEXT NOT NULL, books INTEGER NOT NULL, failures INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_nodes (source TEXT NOT NULL, id TEXT NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, depth INTEGER NOT NULL, done INTEGER NOT NULL, PRIMARY KEY(source,id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_nodes_source_done ON scan_nodes(source,done)")
                db.execSQL("INSERT INTO catalog_changes SELECT identity,lower(hex(randomblob(16))) FROM books")
                tracking(db)
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
            }
        }
        @Volatile private var instance: LibraryDatabase? = null
        fun get(context: android.content.Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(TRACKING).build().also { instance = it }
        }
    }
}
