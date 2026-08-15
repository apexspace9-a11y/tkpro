package vn.tietkiem.pro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vn.tietkiem.pro.security.PinSecurity
import vn.tietkiem.pro.security.SecureSecretStore
import java.util.Calendar

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val theme: String = "SYSTEM",
    val pinSalt: String = "",
    val pinHash: String = "",
    val biometricEnabled: Boolean = false,
    val adminSalt: String = "",
    val adminHash: String = "",
    val notificationsEnabled: Boolean = true,
    val privacyMode: Boolean = false,
    val aiEndpoint: String = "",
    val aiModel: String = "",
    val aiSystemPrompt: String = "Bạn là trợ lý tài chính cá nhân. Hãy trả lời ngắn gọn, thực tế, ưu tiên bảo toàn dòng tiền và không bịa dữ liệu.",
    val aiFinancialContext: Boolean = true,
    val premiumTier: String = PremiumTier.FREE.name,
    val premiumExpiry: Long = 0L,
    val bankName: String = "",
    val bankAccount: String = "",
    val bankOwner: String = "",
    val plusPrice: Long = 49_000L,
    val proPrice: Long = 99_000L,
    val serverUrl: String = "",
    val cloudEmail: String = "",
    val cloudDirty: Boolean = false
) {
    val hasPin: Boolean get() = pinSalt.isNotBlank() && pinHash.isNotBlank()
    val hasAdminKey: Boolean get() = adminSalt.isNotBlank() && adminHash.isNotBlank()
    val premiumActive: Boolean get() = premiumTier != PremiumTier.FREE.name && (premiumExpiry == 0L || premiumExpiry > System.currentTimeMillis())
    val cloudConfigured: Boolean get() = serverUrl.isNotBlank() && cloudEmail.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private val secrets = SecureSecretStore(context)

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val ADMIN_SALT = stringPreferencesKey("admin_salt")
        val ADMIN_HASH = stringPreferencesKey("admin_hash")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val AI_ENDPOINT = stringPreferencesKey("ai_endpoint")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
        val AI_FINANCIAL_CONTEXT = booleanPreferencesKey("ai_financial_context")
        val PREMIUM_TIER = stringPreferencesKey("premium_tier")
        val PREMIUM_EXPIRY = longPreferencesKey("premium_expiry")
        val BANK_NAME = stringPreferencesKey("bank_name")
        val BANK_ACCOUNT = stringPreferencesKey("bank_account")
        val BANK_OWNER = stringPreferencesKey("bank_owner")
        val PLUS_PRICE = longPreferencesKey("plus_price")
        val PRO_PRICE = longPreferencesKey("pro_price")
        val SERVER_URL = stringPreferencesKey("server_url_v4")
        val CLOUD_EMAIL = stringPreferencesKey("cloud_email_v4")
        val CLOUD_DIRTY = booleanPreferencesKey("cloud_dirty_v4")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME] ?: "SYSTEM",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC] ?: false,
            adminSalt = prefs[Keys.ADMIN_SALT] ?: "",
            adminHash = prefs[Keys.ADMIN_HASH] ?: "",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            privacyMode = prefs[Keys.PRIVACY_MODE] ?: false,
            aiEndpoint = prefs[Keys.AI_ENDPOINT] ?: "",
            aiModel = prefs[Keys.AI_MODEL] ?: "",
            aiSystemPrompt = prefs[Keys.AI_SYSTEM_PROMPT] ?: "Bạn là trợ lý tài chính cá nhân. Hãy trả lời ngắn gọn, thực tế, ưu tiên bảo toàn dòng tiền và không bịa dữ liệu.",
            aiFinancialContext = prefs[Keys.AI_FINANCIAL_CONTEXT] ?: true,
            premiumTier = prefs[Keys.PREMIUM_TIER] ?: PremiumTier.FREE.name,
            premiumExpiry = prefs[Keys.PREMIUM_EXPIRY] ?: 0L,
            bankName = prefs[Keys.BANK_NAME] ?: "",
            bankAccount = prefs[Keys.BANK_ACCOUNT] ?: "",
            bankOwner = prefs[Keys.BANK_OWNER] ?: "",
            plusPrice = prefs[Keys.PLUS_PRICE] ?: 49_000L,
            proPrice = prefs[Keys.PRO_PRICE] ?: 99_000L,
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            cloudEmail = prefs[Keys.CLOUD_EMAIL] ?: "",
            cloudDirty = prefs[Keys.CLOUD_DIRTY] ?: false
        )
    }

    suspend fun setTheme(value: String) { context.settingsStore.edit { it[Keys.THEME] = value } }

    suspend fun setPin(pin: String) {
        val (salt, hash) = PinSecurity.create(pin)
        context.settingsStore.edit { it[Keys.PIN_SALT] = salt; it[Keys.PIN_HASH] = hash }
    }

    suspend fun clearPin() {
        context.settingsStore.edit {
            it.remove(Keys.PIN_SALT); it.remove(Keys.PIN_HASH); it[Keys.BIOMETRIC] = false
        }
    }

    suspend fun setBiometric(enabled: Boolean) { context.settingsStore.edit { it[Keys.BIOMETRIC] = enabled } }

    suspend fun setAdminKey(value: String) {
        require(value.length >= 6) { "Khóa admin cần ít nhất 6 ký tự" }
        val (salt, hash) = PinSecurity.create(value)
        context.settingsStore.edit { it[Keys.ADMIN_SALT] = salt; it[Keys.ADMIN_HASH] = hash }
    }

    suspend fun verifyAdminKey(value: String): Boolean {
        val current = settings.first()
        return current.hasAdminKey && PinSecurity.verify(value, current.adminSalt, current.adminHash)
    }

    suspend fun setNotifications(enabled: Boolean) { context.settingsStore.edit { it[Keys.NOTIFICATIONS] = enabled } }
    suspend fun setPrivacyMode(enabled: Boolean) { context.settingsStore.edit { it[Keys.PRIVACY_MODE] = enabled } }

    suspend fun setAiConfig(endpoint: String, model: String, systemPrompt: String, includeFinance: Boolean) {
        context.settingsStore.edit {
            it[Keys.AI_ENDPOINT] = endpoint.trim()
            it[Keys.AI_MODEL] = model.trim()
            it[Keys.AI_SYSTEM_PROMPT] = systemPrompt.trim()
            it[Keys.AI_FINANCIAL_CONTEXT] = includeFinance
        }
    }

    fun setAiApiKey(value: String) { secrets.put(SecureSecretStore.AI_API_KEY, value.trim()) }
    fun getAiApiKey(): String = secrets.get(SecureSecretStore.AI_API_KEY)

    suspend fun setBankConfig(name: String, account: String, owner: String, plusPrice: Long, proPrice: Long) {
        require(plusPrice >= 0 && proPrice >= 0) { "Giá gói không hợp lệ" }
        context.settingsStore.edit {
            it[Keys.BANK_NAME] = name.trim(); it[Keys.BANK_ACCOUNT] = account.trim(); it[Keys.BANK_OWNER] = owner.trim()
            it[Keys.PLUS_PRICE] = plusPrice; it[Keys.PRO_PRICE] = proPrice
        }
    }

    suspend fun activatePremium(tier: PremiumTier, months: Int) {
        require(months in 1..120) { "Thời hạn premium không hợp lệ" }
        val expiry = Calendar.getInstance().apply { add(Calendar.MONTH, months) }.timeInMillis
        context.settingsStore.edit {
            it[Keys.PREMIUM_TIER] = tier.name
            it[Keys.PREMIUM_EXPIRY] = if (tier == PremiumTier.FREE) 0L else expiry
        }
    }

    suspend fun deactivatePremium() {
        context.settingsStore.edit { it[Keys.PREMIUM_TIER] = PremiumTier.FREE.name; it[Keys.PREMIUM_EXPIRY] = 0L }
    }

    suspend fun saveCloudSession(serverUrl: String, email: String, token: String) {
        val normalized = serverUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) { "Địa chỉ server không hợp lệ" }
        require(email.isNotBlank()) { "Email không được trống" }
        secrets.put(SecureSecretStore.CLOUD_ACCESS_TOKEN, token)
        context.settingsStore.edit {
            it[Keys.SERVER_URL] = normalized
            it[Keys.CLOUD_EMAIL] = email.trim().lowercase()
        }
    }

    suspend fun setServerUrl(serverUrl: String) {
        val normalized = serverUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) { "Địa chỉ server không hợp lệ" }
        context.settingsStore.edit { it[Keys.SERVER_URL] = normalized }
    }

    fun cloudToken(): String = secrets.get(SecureSecretStore.CLOUD_ACCESS_TOKEN)

    suspend fun clearCloudSession() {
        secrets.remove(SecureSecretStore.CLOUD_ACCESS_TOKEN)
        secrets.remove(SecureSecretStore.SERVER_ADMIN_KEY)
        context.settingsStore.edit {
            it.remove(Keys.CLOUD_EMAIL)
            it[Keys.CLOUD_DIRTY] = false
        }
    }

    suspend fun setCloudDirty(value: Boolean) { context.settingsStore.edit { it[Keys.CLOUD_DIRTY] = value } }

    fun saveServerAdminKey(value: String) { secrets.put(SecureSecretStore.SERVER_ADMIN_KEY, value) }
    fun serverAdminKey(): String = secrets.get(SecureSecretStore.SERVER_ADMIN_KEY)
    fun clearServerAdminKey() { secrets.remove(SecureSecretStore.SERVER_ADMIN_KEY) }

    suspend fun applyRemoteProfile(tier: String, expiry: Long) {
        context.settingsStore.edit {
            it[Keys.PREMIUM_TIER] = tier
            it[Keys.PREMIUM_EXPIRY] = expiry
        }
    }

    suspend fun applyPublicConfig(bank: String, account: String, owner: String, plusPrice: Long, proPrice: Long) {
        context.settingsStore.edit {
            it[Keys.BANK_NAME] = bank
            it[Keys.BANK_ACCOUNT] = account
            it[Keys.BANK_OWNER] = owner
            it[Keys.PLUS_PRICE] = plusPrice
            it[Keys.PRO_PRICE] = proPrice
        }
    }
}
