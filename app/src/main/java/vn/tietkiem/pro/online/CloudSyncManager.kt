package vn.tietkiem.pro.online

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import vn.tietkiem.pro.data.BackupManager
import vn.tietkiem.pro.data.SettingsRepository

class CloudSyncManager(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    val api: CloudApi = CloudApi()
) {
    private val mutex = Mutex()
    private var revision: Int = 0

    data class SessionResult(
        val profile: CloudApi.UserProfile,
        val uploadedExistingLocalData: Boolean
    )

    suspend fun restoreSession(): SessionResult? = mutex.withLock {
        val settings = settingsRepository.settings.first()
        val token = settingsRepository.cloudToken()
        if (settings.serverUrl.isBlank() || token.isBlank()) return@withLock null
        bootstrapLocked(settings.serverUrl, token, cloudDirty = settings.cloudDirty)
    }

    suspend fun login(serverUrl: String, email: String, password: String, register: Boolean): SessionResult = mutex.withLock {
        require(api.health(serverUrl)) { "Không kết nối được server" }
        val auth = if (register) api.register(serverUrl, email, password) else api.login(serverUrl, email, password)
        settingsRepository.saveCloudSession(serverUrl, auth.user.email, auth.token)
        settingsRepository.applyRemoteProfile(auth.user.premiumTier, auth.user.premiumExpiry)
        bootstrapLocked(serverUrl.trim().trimEnd('/'), auth.token, auth.user, cloudDirty = false)
    }

    suspend fun logout() = mutex.withLock {
        revision = 0
        settingsRepository.clearCloudSession()
    }

    suspend fun pushCurrent(): Int = mutex.withLock {
        val settings = settingsRepository.settings.first()
        val token = settingsRepository.cloudToken()
        require(settings.serverUrl.isNotBlank() && token.isNotBlank()) { "Chưa đăng nhập cloud" }
        val payload = JSONObject(backupManager.exportJson())
        revision = try {
            api.putSnapshot(settings.serverUrl, token, revision, payload)
        } catch (error: CloudApiException) {
            if (error.statusCode != 409) throw error
            val current = api.getSnapshot(settings.serverUrl, token)
            revision = current.revision
            api.putSnapshot(settings.serverUrl, token, revision, payload)
        }
        settingsRepository.setCloudDirty(false)
        revision
    }

    suspend fun pullRemote(): CloudApi.UserProfile = mutex.withLock {
        val settings = settingsRepository.settings.first()
        val token = settingsRepository.cloudToken()
        require(settings.serverUrl.isNotBlank() && token.isNotBlank()) { "Chưa đăng nhập cloud" }
        val profile = api.me(settings.serverUrl, token)
        val snapshot = api.getSnapshot(settings.serverUrl, token)
        if (settings.cloudDirty) {
            revision = snapshot.revision
            val localPayload = JSONObject(backupManager.exportJson())
            revision = api.putSnapshot(settings.serverUrl, token, revision, localPayload)
            settingsRepository.setCloudDirty(false)
        } else {
            if (snapshot.payload != null) backupManager.importJson(snapshot.payload.toString())
            revision = snapshot.revision
        }
        applyProfileAndPublicConfig(settings.serverUrl, profile)
        profile
    }

    suspend fun health(): Boolean {
        val settings = settingsRepository.settings.first()
        return settings.serverUrl.isNotBlank() && api.health(settings.serverUrl)
    }

    private suspend fun bootstrapLocked(
        baseUrl: String,
        token: String,
        knownProfile: CloudApi.UserProfile? = null,
        cloudDirty: Boolean
    ): SessionResult {
        val profile = knownProfile ?: api.me(baseUrl, token)
        val snapshot = api.getSnapshot(baseUrl, token)
        var uploadedLocal = false
        when {
            cloudDirty -> {
                revision = snapshot.revision
                val localPayload = JSONObject(backupManager.exportJson())
                revision = api.putSnapshot(baseUrl, token, revision, localPayload)
                settingsRepository.setCloudDirty(false)
                uploadedLocal = true
            }
            snapshot.payload == null -> {
                val localPayload = JSONObject(backupManager.exportJson())
                revision = api.putSnapshot(baseUrl, token, snapshot.revision, localPayload)
                settingsRepository.setCloudDirty(false)
                uploadedLocal = true
            }
            else -> {
                backupManager.importJson(snapshot.payload.toString())
                revision = snapshot.revision
            }
        }
        applyProfileAndPublicConfig(baseUrl, profile)
        return SessionResult(profile, uploadedLocal)
    }

    private suspend fun applyProfileAndPublicConfig(baseUrl: String, profile: CloudApi.UserProfile) {
        settingsRepository.applyRemoteProfile(profile.premiumTier, profile.premiumExpiry)
        runCatching { api.publicConfig(baseUrl) }.getOrNull()?.let { config ->
            settingsRepository.applyPublicConfig(
                config.bankName,
                config.bankAccount,
                config.bankOwner,
                config.plusPrice,
                config.proPrice
            )
        }
    }
}
