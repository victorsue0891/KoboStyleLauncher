package org.kobostyle.launcher

import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.librarian.*

/** Explicit opt-in audit of our own library. No book titles/URIs leave the device; no live API/key. */
@RunWith(AndroidJUnit4::class)
class LibrarianRealLibraryTest {
    @Test fun offlineAndMockAiRecommendOnlyReadablePersonalLibraryBooks() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = LibraryDatabase.get(context)
        val before = db.library().observeBooks().first()
        val sources = db.library().sources()
        val eligible = LocalCurator.rank(before, Choice())
        assertTrue("Import actual books before running this explicit audit", eligible.isNotEmpty())
        val preferenceName = "acceptance_real_library_${java.util.UUID.randomUUID()}"
        val prefs = AiPreferences(context.getSharedPreferences(preferenceName, 0))
        val vault = object : KeyVault {
            override fun exists() = true
            override fun read() = "synthetic-key-never-sent-to-network"
            override fun save(value: String) = error("Audit does not save keys")
            override fun delete() = Unit
        }
        var mockCalls = 0
        val transport = CuratorTransport { _, body ->
            mockCalls++
            val request = JSONObject(body)
            val data = JSONObject(request.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"))
            val candidates = data.getJSONArray("candidate_books")
            assertTrue(candidates.length() in 1..20)
            val recommendations = JSONArray()
            repeat(minOf(3, candidates.length())) { i -> recommendations.put(JSONObject().put("book_id", candidates.getJSONObject(i).getString("book_id"))
                .put("reason", "裝置本機假回應，只驗證館藏關聯。").put("reading_tip", "這是驗收資料，不是線上 AI 回應。")) }
            val result = JSONObject().put("expression", "RECOMMEND").put("dialogue", "本機驗收")
                .put("recommendations", recommendations).put("options", JSONArray().put(JSONObject().put("label", "返回").put("action", "BACK").put("book_id", JSONObject.NULL)))
            WireResponse(200, JSONObject().put("status", "completed").put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant")
                .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", result.toString()))))).toString())
        }
        val model = LibrarianViewModel(LibraryRepository(context, db.library()), prefs, vault, CuratorService(prefs, vault, transport, { true })) { book ->
            context.contentResolver.openFileDescriptor(Uri.parse(book.uri), "r")?.use { true } == true
        }
        try {
            for (online in listOf(false, true)) {
                prefs.save(AiConfig(enabled = online))
                withContext(Dispatchers.Main) {
                    model.dispatch(DialogueAction.RESET)
                    model.dispatch(DialogueAction.CHOOSE); model.dispatch(DialogueAction.MOOD, "surprise")
                    model.dispatch(DialogueAction.DURATION, "30"); model.dispatch(DialogueAction.PREFERENCE, "yes")
                    model.dispatch(if (online) DialogueAction.REQUEST else DialogueAction.OFFLINE)
                }
                val result = withTimeout(30_000) { model.state.first { it.stage == DialogueStage.CARDS }.result!! }
                assertEquals(minOf(3, result.checkedCandidates), result.cards.size)
                assertTrue("Actual readable candidates required", result.cards.isNotEmpty())
                assertEquals(eligible.size, result.indexedBooks)
                assertEquals(if (online) "openai" else "offline", result.source) // fake transport in the AI branch
                for (card in result.cards) {
                    assertFalse(LocalCurator.isAcceptanceFixture(card.book))
                    assertTrue("Card must be an unchanged current Room book", before.any { it == card.book })
                    assertTrue(context.contentResolver.openFileDescriptor(Uri.parse(card.book.uri), "r")?.use { true } == true)
                }
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nREAL_LIBRARY mode=${if (online) "MOCK_AI" else "OFFLINE"} indexed=${before.size} fixtures=${before.count(LocalCurator::isAcceptanceFixture)} eligible=${result.indexedBooks} readableCandidates=${result.checkedCandidates} cards=${result.cards.size} membership=PASS realFiles=PASS liveApiCalls=0\n") })
            }
            assertEquals(1, mockCalls)
            assertTrue("Audit must not change library rows", before == db.library().observeBooks().first())
            assertTrue("Audit must not change sources", sources == db.library().sources())
        } finally {
            withContext(Dispatchers.Main) { model.clearSessionAndCache() }
            context.deleteSharedPreferences(preferenceName)
        }
    }
}
