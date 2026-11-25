package org.kobostyle.launcher.spirits

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SchemaMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)
    @Test fun v2ToV3RetainsOriginalRowsAndSeedsOnlyLocalProjectionWork() {
        // Room's helper compares its configured name with the path; an absolute name also
        // avoids treating Windows backslashes as Android slash-separated basenames.
        val name = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("migration-v4-${java.util.UUID.randomUUID()}").absolutePath
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO books VALUES('id','content://fake/book','source','doc','Synthetic','Author','application/pdf',10,20,12345,NULL,'ok','token','[小說]-合成-作者.pdf')")
            execSQL("INSERT INTO sources VALUES('source',1,'ok')"); close()
        }
        helper.runMigrationsAndValidate(name, 3, true, LibraryDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT title,uri,lastOpenedAt,displayName FROM books WHERE identity='id'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Synthetic", c.getString(0)); assertEquals("content://fake/book", c.getString(1)); assertEquals(12345L, c.getLong(2)); assertEquals("[小說]-合成-作者.pdf", c.getString(3))
            }
            db.query("SELECT identity FROM catalog_changes").use { c -> assertEquals(1, c.count) }
            db.execSQL("DELETE FROM catalog_changes")
            db.execSQL("UPDATE books SET lastOpenedAt=67890 WHERE identity='id'")
            db.query("SELECT identity FROM catalog_changes").use { c -> assertEquals(1, c.count) }
            db.query("SELECT * FROM scan_jobs").use { c -> assertEquals(0, c.count) }
        }
    }
}
