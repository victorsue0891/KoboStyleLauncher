package org.kobostyle.launcher.librarian

import org.json.JSONArray
import org.json.JSONObject
import org.kobostyle.launcher.data.db.Book

fun sampleBook(id: Int) = Book("private-document-$id", "content://private.provider/document/$id", "content://private.provider/tree/books",
    "PRIVATE-CARD:books/$id", "合成書籍 $id", "合成作者", "application/pdf", 1234, 123,
    displayName = "[小說]-合成故事 $id-合成作者.pdf")
fun validPayload(ids: List<String> = listOf("b0", "b1", "b2")): JSONObject = JSONObject()
    .put("expression", "RECOMMEND").put("dialogue", "先看看這幾本。")
    .put("recommendations", JSONArray(ids.map { JSONObject().put("book_id", it).put("reason", "這本來自提供的館藏分類，尚未提供全文。").put("reading_tip", "先看看目錄。") }))
    .put("options", JSONArray().put(JSONObject().put("label", "看看這本").put("action", "VIEW_BOOK").put("book_id", ids.firstOrNull() ?: "b0")))
fun envelope(payload: String = validPayload().toString()) = JSONObject().put("status", "completed").put("error", JSONObject.NULL)
    .put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant").put("status", "completed")
        .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", payload)))))
    .put("usage", JSONObject().put("input_tokens", 200).put("output_tokens", 100))
class FakeVault(var value: String? = "test-key-not-real") : KeyVault {
    var reads = 0
    override fun exists() = value != null
    override fun save(value: String) { this.value = value }
    override fun read(): String? { reads++; return value }
    override fun delete() { value = null }
}
