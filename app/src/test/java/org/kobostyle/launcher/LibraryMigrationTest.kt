package org.kobostyle.launcher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.kobostyle.launcher.data.db.LibraryDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LibraryMigrationTest {
    @Test fun versionOneUpgradePreservesBooksSourcesAndHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE books (identity TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, sourceUri TEXT NOT NULL, documentId TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, mime TEXT NOT NULL, lastModified INTEGER NOT NULL, size INTEGER NOT NULL, lastOpenedAt INTEGER NOT NULL, coverPath TEXT, scanStatus TEXT NOT NULL, scanToken TEXT NOT NULL)")
            old.execSQL("CREATE TABLE sources (uri TEXT NOT NULL PRIMARY KEY, tree INTEGER NOT NULL, status TEXT NOT NULL)")
            old.execSQL("INSERT INTO books VALUES ('id','content://provider/book','source','document','Title','Author','application/pdf',10,20,12345,NULL,'ok','token')")
            old.execSQL("INSERT INTO sources VALUES ('source',1,'ok')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val book = db.library().book("id")!!
            assertEquals("", book.displayName)
            assertEquals("Title", book.title)
            assertEquals(12345L, book.lastOpenedAt)
            assertEquals("token", book.scanToken)
            assertEquals("source", db.library().sources().single().uri)
            db.library().index(book.copy(displayName = "[小說]-書名-作者.pdf"))
            assertEquals(12345L, db.library().book("id")!!.lastOpenedAt)
            assertEquals("[小說]-書名-作者.pdf", db.library().book("id")!!.displayName)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
