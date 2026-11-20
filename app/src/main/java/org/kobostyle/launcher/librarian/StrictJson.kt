package org.kobostyle.launcher.librarian

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.StringReader

/** Android JSONObject is lenient; validate syntax, duplicate keys and depth before using it. */
internal object StrictJson {
    fun obj(text: String): JSONObject {
        require(text.length <= 262_144)
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            require(reader.peek() == JsonToken.BEGIN_OBJECT)
            var nodes = 0
            fun value(depth: Int) {
                require(depth <= 32 && ++nodes <= 20_000)
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        val keys = hashSetOf<String>()
                        while (reader.hasNext()) { require(keys.add(reader.nextName())); value(depth + 1) }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) value(depth + 1); reader.endArray() }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid JSON")
                }
            }
            value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        return JSONObject(text)
    }
}
