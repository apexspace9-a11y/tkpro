package vn.tietkiem.pro.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.tietkiem.pro.data.AppSettings
import vn.tietkiem.pro.data.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL

class AiService(private val settingsRepository: SettingsRepository) {

    suspend fun chat(userMessage: String, financialContext: String, settings: AppSettings): String = withContext(Dispatchers.IO) {
        require(userMessage.isNotBlank()) { "Tin nhắn trống" }
        require(settings.aiEndpoint.isNotBlank()) { "AI chưa có endpoint" }
        require(settings.aiModel.isNotBlank()) { "AI chưa có model" }

        val apiKey = settingsRepository.getAiApiKey()
        val systemText = buildString {
            append(settings.aiSystemPrompt.ifBlank { "Bạn là trợ lý tài chính cá nhân." })
            if (settings.aiFinancialContext && financialContext.isNotBlank()) {
                append("\n\nDữ liệu tài chính cục bộ do người dùng cho phép cung cấp:\n")
                append(financialContext)
            }
        }

        val payload = JSONObject().apply {
            put("model", settings.aiModel)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemText))
                put(JSONObject().put("role", "user").put("content", userMessage.trim()))
            })
            put("temperature", 0.35)
        }

        val connection = (URL(settings.aiEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                error(detail?.takeIf { it.isNotBlank() } ?: "AI trả lỗi HTTP $code")
            }
            parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): String {
        val root = JSONObject(body)
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it.trim() }

        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            first?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.trim() }
            first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        }

        val output = root.optJSONArray("output")
        if (output != null) {
            val pieces = mutableListOf<String>()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    part.optString("text").takeIf { it.isNotBlank() }?.let(pieces::add)
                }
            }
            if (pieces.isNotEmpty()) return pieces.joinToString("\n").trim()
        }

        error("Không đọc được nội dung phản hồi AI")
    }
}
