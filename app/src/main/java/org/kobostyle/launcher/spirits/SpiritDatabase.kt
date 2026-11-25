package org.kobostyle.launcher.spirits

import android.content.Context
import androidx.room.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

suspend fun <T> SpiritDatabase.transaction(block: suspend () -> T): T = useWriterConnection { connection ->
    connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
}

@Entity(tableName = "catalog", indices = [Index("title"), Index("author"), Index("category"), Index("lastOpened")])
data class CatalogEntry(@PrimaryKey val identity: String, val title: String, val author: String, val category: String,
    val lastOpened: Long, val available: Boolean, val version: String)
@Entity(tableName = "spirits")
data class Spirit(@PrimaryKey val bookId: String, val spiritId: String, val name: String, val tone: String,
    val opening: String, val version: String, val refined: Boolean = false)
@Entity(tableName = "boundaries")
data class ReadingBoundary(@PrimaryKey val bookId: String, val chapter: Int? = null, val spoilers: Boolean = false)
@Entity(tableName = "packs")
data class KnowledgePack(@PrimaryKey val bookId: String, val packId: String, val digest: String, val version: String,
    val title: String, val author: String, val source: String)
@Entity(tableName = "sections", primaryKeys = ["bookId", "sectionId"], indices = [Index(value = ["bookId", "chapter"])])
data class KnowledgeSection(val bookId: String, val sectionId: String, val chapter: Int, val title: String,
    val content: String, val locator: String, val digest: String)
@Entity(tableName = "usage", indices = [Index("day"), Index("month")])
data class UsageEvent(@PrimaryKey val requestId: String, val action: String, val model: String, val day: String,
    val month: String, val started: Long, val reservedMicros: Long, val chargedMicros: Long,
    val status: String, val inputTokens: Int = 0, val outputTokens: Int = 0)

@Dao
interface SpiritDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun catalog(entry: CatalogEntry)
    @Query("DELETE FROM catalog WHERE identity = :id") suspend fun removeCatalog(id: String)
    @Query("SELECT COUNT(*) FROM catalog") fun catalogCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM catalog") suspend fun count(): Int
    @RawQuery suspend fun catalogQuery(query: RoomRawQuery): List<CatalogEntry>
    @RawQuery suspend fun number(query: RoomRawQuery): Int
    @RawQuery suspend fun groups(query: RoomRawQuery): List<CatalogGroup>
    @Query("SELECT * FROM spirits WHERE bookId = :id") suspend fun spirit(id: String): Spirit?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun spirit(value: Spirit)
    @Query("DELETE FROM spirits WHERE bookId = :id") suspend fun deleteSpirit(id: String)
    @Query("SELECT * FROM boundaries WHERE bookId = :id") suspend fun boundary(id: String): ReadingBoundary?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun boundary(value: ReadingBoundary)
    @Query("SELECT * FROM packs WHERE bookId = :id") suspend fun pack(id: String): KnowledgePack?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun pack(value: KnowledgePack)
    @Insert suspend fun sections(values: List<KnowledgeSection>)
    @Query("DELETE FROM sections WHERE bookId = :id") suspend fun deleteSections(id: String)
    @Query("DELETE FROM packs WHERE bookId = :id") suspend fun deletePack(id: String)
    @Query("DELETE FROM boundaries WHERE bookId = :id") suspend fun deleteBoundary(id: String)
    @RawQuery suspend fun evidence(query: RoomRawQuery): List<KnowledgeSection>
    @Query("SELECT * FROM usage WHERE requestId = :id") suspend fun event(id: String): UsageEvent?
    @Insert suspend fun reserve(value: UsageEvent)
    @Update suspend fun updateUsage(value: UsageEvent)
    @Query("SELECT COALESCE(SUM(chargedMicros),0) FROM usage WHERE day = :day") suspend fun dayCost(day: String): Long
    @Query("SELECT COALESCE(SUM(chargedMicros),0) FROM usage WHERE month = :month") suspend fun monthCost(month: String): Long
    @Query("SELECT COUNT(*) FROM usage WHERE day = :day") suspend fun dayRequests(day: String): Int
    @Query("SELECT MAX(day) FROM usage") suspend fun latestDay(): String?
    @Query("SELECT * FROM usage ORDER BY started DESC LIMIT 20") suspend fun latestUsage(): List<UsageEvent>
    @Query("UPDATE usage SET status = 'UNKNOWN_BILLING' WHERE status IN ('RESERVED','IN_FLIGHT')") suspend fun recoverReservations()
}
data class CatalogGroup(val name: String, val count: Int)

@Database(entities = [CatalogEntry::class, Spirit::class, ReadingBoundary::class, KnowledgePack::class,
    KnowledgeSection::class, UsageEvent::class], version = 1, exportSchema = true)
abstract class SpiritDatabase : RoomDatabase() {
    abstract fun dao(): SpiritDao
    companion object {
        // FTS is derived, maintained by triggers, never an authority for the book or its URI.
        val SEARCH = object : Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                connection.execSQL("CREATE VIRTUAL TABLE catalog_fts USING fts5(identity UNINDEXED,title,author,tokenize='trigram')")
                connection.execSQL("CREATE TRIGGER catalog_ai AFTER INSERT ON catalog BEGIN INSERT INTO catalog_fts(identity,title,author) VALUES(new.identity,new.title,new.author); END")
                connection.execSQL("CREATE TRIGGER catalog_ad AFTER DELETE ON catalog BEGIN DELETE FROM catalog_fts WHERE identity=old.identity; END")
                connection.execSQL("CREATE TRIGGER catalog_au AFTER UPDATE ON catalog BEGIN DELETE FROM catalog_fts WHERE identity=old.identity; INSERT INTO catalog_fts(identity,title,author) VALUES(new.identity,new.title,new.author); END")
                connection.execSQL("CREATE VIRTUAL TABLE knowledge_fts USING fts5(bookId UNINDEXED,sectionId UNINDEXED,title,content,tokenize='trigram')")
                connection.execSQL("CREATE TRIGGER section_ai AFTER INSERT ON sections BEGIN INSERT INTO knowledge_fts(bookId,sectionId,title,content) VALUES(new.bookId,new.sectionId,new.title,new.content); END")
                connection.execSQL("CREATE TRIGGER section_ad AFTER DELETE ON sections BEGIN DELETE FROM knowledge_fts WHERE bookId=old.bookId AND sectionId=old.sectionId; END")
            }
        }
        @Volatile private var instance: SpiritDatabase? = null
        fun get(context: Context): SpiritDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SpiritDatabase::class.java, "spirits.db")
                .setDriver(BundledSQLiteDriver()).addCallback(SEARCH).build().also { instance = it }
        }
    }
}
