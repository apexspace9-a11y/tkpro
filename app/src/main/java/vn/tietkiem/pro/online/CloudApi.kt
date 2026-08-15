package vn.tietkiem.pro.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class CloudApi {
    data class UserProfile(
        val id: Long,
        val email: String,
        val premiumTier: String,
        val premiumExpiry: Long,
        val premiumActive: Boolean
    )

    data class AuthResult(val token: String, val user: UserProfile)
    data class Snapshot(val revision: Int, val payload: JSONObject?, val updatedAt: Long)
    data class PublicConfig(
        val bankName: String,
        val bankAccount: String,
        val bankOwner: String,
        val plusPrice: Long,
        val proPrice: Long
    )

    data class RemotePayment(
        val id: Long,
        val email: String = "",
        val plan: String,
        val amount: Long,
        val transferCode: String,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    data class AdminConfig(
        val aiEndpoint: String,
        val aiModel: String,
        val aiApiKeySet: Boolean,
        val aiSystemPrompt: String,
        val bankName: String,
        val bankAccount: String,
        val bankOwner: String,
        val plusPrice: Long,
        val proPrice: Long
    )

    suspend fun health(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { request(baseUrl, "GET", "/v1/health").optBoolean("ok", false) }.getOrDefault(false)
    }

    suspend fun register(baseUrl: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        parseAuth(request(baseUrl, "POST", "/v1/auth/register", body = body))
    }

    suspend fun login(baseUrl: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        parseAuth(request(baseUrl, "POST", "/v1/auth/login", body = body))
    }

    suspend fun me(baseUrl: String, token: String): UserProfile = withContext(Dispatchers.IO) {
        parseUser(request(baseUrl, "GET", "/v1/me", token = token).getJSONObject("user"))
    }

    suspend fun publicConfig(baseUrl: String): PublicConfig = withContext(Dispatchers.IO) {
        val o = request(baseUrl, "GET", "/v1/config/public")
        PublicConfig(
            bankName = o.optString("bank_name"),
            bankAccount = o.optString("bank_account"),
            bankOwner = o.optString("bank_owner"),
            plusPrice = o.optLong("plus_price"),
            proPrice = o.optLong("pro_price")
        )
    }

    suspend fun getSnapshot(baseUrl: String, token: String): Snapshot = withContext(Dispatchers.IO) {
        val o = request(baseUrl, "GET", "/v1/snapshot", token = token)
        Snapshot(
            revision = o.optInt("revision", 0),
            payload = if (o.isNull("payload")) null else o.optJSONObject("payload"),
            updatedAt = o.optLong("updated_at", 0)
        )
    }

    suspend fun putSnapshot(baseUrl: String, token: String, revision: Int, payload: JSONObject): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("revision", revision).put("payload", payload)
        request(baseUrl, "PUT", "/v1/snapshot", token = token, body = body).getInt("revision")
    }

    suspend fun createPayment(baseUrl: String, token: String, plan: String): RemotePayment = withContext(Dispatchers.IO) {
        val result = request(baseUrl, "POST", "/v1/payments", token = token, body = JSONObject().put("plan", plan))
        parsePayment(result.getJSONObject("payment"))
    }

    suspend fun payments(baseUrl: String, token: String): List<RemotePayment> = withContext(Dispatchers.IO) {
        parsePayments(request(baseUrl, "GET", "/v1/payments", token = token).optJSONArray("payments"))
    }

    suspend fun aiChat(baseUrl: String, token: String, message: String, context: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("message", message).put("context", context)
        request(baseUrl, "POST", "/v1/ai/chat", token = token, body = body).getString("reply")
    }

    suspend fun verifyAdmin(baseUrl: String, adminKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { request(baseUrl, "POST", "/v1/admin/verify", adminKey = adminKey).optBoolean("ok") }.getOrDefault(false)
    }

    suspend fun adminConfig(baseUrl: String, adminKey: String): AdminConfig = withContext(Dispatchers.IO) {
        val c = request(baseUrl, "GET", "/v1/admin/config", adminKey = adminKey).getJSONObject("config")
        AdminConfig(
            aiEndpoint = c.optString("ai_endpoint"),
            aiModel = c.optString("ai_model"),
            aiApiKeySet = c.optBoolean("ai_api_key_set", false),
            aiSystemPrompt = c.optString("ai_system_prompt"),
            bankName = c.optString("bank_name"),
            bankAccount = c.optString("bank_account"),
            bankOwner = c.optString("bank_owner"),
            plusPrice = c.optString("plus_price").toLongOrNull() ?: c.optLong("plus_price"),
            proPrice = c.optString("pro_price").toLongOrNull() ?: c.optLong("pro_price")
        )
    }

    suspend fun saveAdminConfig(baseUrl: String, adminKey: String, config: AdminConfig, newApiKey: String): AdminConfig = withContext(Dispatchers.IO) {
        val c = JSONObject()
            .put("ai_endpoint", config.aiEndpoint)
            .put("ai_model", config.aiModel)
            .put("ai_system_prompt", config.aiSystemPrompt)
            .put("bank_name", config.bankName)
            .put("bank_account", config.bankAccount)
            .put("bank_owner", config.bankOwner)
            .put("plus_price", config.plusPrice.toString())
            .put("pro_price", config.proPrice.toString())
        if (newApiKey.isNotBlank()) c.put("ai_api_key", newApiKey.trim())
        val result = request(baseUrl, "PUT", "/v1/admin/config", adminKey = adminKey, body = JSONObject().put("config", c))
        val saved = result.getJSONObject("config")
        AdminConfig(
            aiEndpoint = saved.optString("ai_endpoint"),
            aiModel = saved.optString("ai_model"),
            aiApiKeySet = saved.optBoolean("ai_api_key_set", false),
            aiSystemPrompt = saved.optString("ai_system_prompt"),
            bankName = saved.optString("bank_name"),
            bankAccount = saved.optString("bank_account"),
            bankOwner = saved.optString("bank_owner"),
            plusPrice = saved.optString("plus_price").toLongOrNull() ?: saved.optLong("plus_price"),
            proPrice = saved.optString("pro_price").toLongOrNull() ?: saved.optLong("pro_price")
        )
    }

    suspend fun adminPayments(baseUrl: String, adminKey: String): List<RemotePayment> = withContext(Dispatchers.IO) {
        parsePayments(request(baseUrl, "GET", "/v1/admin/payments", adminKey = adminKey).optJSONArray("payments"))
    }

    suspend fun reviewPayment(baseUrl: String, adminKey: String, paymentId: Long, approve: Boolean, months: Int = 1) = withContext(Dispatchers.IO) {
        val action = if (approve) "approve" else "reject"
        request(
            baseUrl,
            "POST",
            "/v1/admin/payments/$paymentId/$action",
            adminKey = adminKey,
            body = JSONObject().put("months", months.coerceIn(1, 120))
        )
        Unit
    }

    suspend fun setPremium(baseUrl: String, adminKey: String, email: String, tier: String, months: Int) = withContext(Dispatchers.IO) {
        request(
            baseUrl,
            "POST",
            "/v1/admin/premium",
            adminKey = adminKey,
            body = JSONObject().put("email", email.trim()).put("tier", tier).put("months", months.coerceIn(1, 120))
        )
        Unit
    }

    private fun parseAuth(o: JSONObject): AuthResult = AuthResult(o.getString("token"), parseUser(o.getJSONObject("user")))

    private fun parseUser(o: JSONObject): UserProfile = UserProfile(
        id = o.getLong("id"),
        email = o.getString("email"),
        premiumTier = o.optString("premium_tier", "FREE"),
        premiumExpiry = o.optLong("premium_expiry", 0),
        premiumActive = o.optBoolean("premium_active", false)
    )

    private fun parsePayments(a: JSONArray?): List<RemotePayment> = if (a == null) emptyList() else (0 until a.length()).map { parsePayment(a.getJSONObject(it)) }

    private fun parsePayment(o: JSONObject): RemotePayment = RemotePayment(
        id = o.getLong("id"),
        email = o.optString("email"),
        plan = o.getString("plan"),
        amount = o.getLong("amount"),
        transferCode = o.getString("transfer_code"),
        status = o.getString("status"),
        createdAt = o.getLong("created_at"),
        updatedAt = o.optLong("updated_at", o.getLong("created_at"))
    )

    private fun request(
        baseUrl: String,
        method: String,
        path: String,
        token: String? = null,
        adminKey: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) { "Địa chỉ server không hợp lệ" }
        val conn = URL(normalized + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 12_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            if (!adminKey.isNullOrBlank()) conn.setRequestProperty("X-Admin-Key", adminKey)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText) }.orEmpty()
            val parsed = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) throw CloudApiException(code, parsed.optString("error", "Server lỗi $code"), parsed.optInt("revision", -1))
            return parsed
        } finally {
            conn.disconnect()
        }
    }
}

class CloudApiException(val statusCode: Int, override val message: String, val revision: Int = -1) : Exception(message)
