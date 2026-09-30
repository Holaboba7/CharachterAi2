package com.example.characterai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Client per qualsiasi endpoint compatibile OpenAI (/chat/completions) con streaming SSE. */
object Llm {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()

    fun stream(baseUrl: String, key: String, model: String, messages: List<Pair<String, String>>): Flow<String> = flow {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.8)
            addProperty("stream", true)
            add("messages", JsonArray().apply {
                messages.forEach { (r, c) -> add(JsonObject().apply { addProperty("role", r); addProperty("content", c) }) }
            })
        }
        val req = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(req)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        call.execute().use { r ->
            if (!r.isSuccessful) throw IOException("Errore HTTP ${r.code}: ${r.body?.string()?.take(300)}")
            val src = r.body!!.source()
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val p = line.removePrefix("data:").trim()
                if (p == "[DONE]") break
                val d = runCatching {
                    JsonParser.parseString(p).asJsonObject.getAsJsonArray("choices")[0]
                        .asJsonObject.getAsJsonObject("delta").get("content")
                }.getOrNull()
                if (d != null && !d.isJsonNull) emit(d.asString)
            }
        }
    }.flowOn(Dispatchers.IO)
}
