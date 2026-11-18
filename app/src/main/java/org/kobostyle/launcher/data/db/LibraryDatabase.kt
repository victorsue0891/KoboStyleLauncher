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
    val scanStatus: String = "ok", val scanToken: String = ""
)
@Entity(tableName = "sources")
data class Source(@PrimaryKey val uri: String, val tree: Boolean, val status: String = "ok")

@Dao
abstract class LibraryDao {
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
@Database(entities = [Book::class, Source::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        @Volatile private var instance: LibraryDatabase? = null
        fun get(context: android.content.Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build().also { instance = it }
        }
    }
}
