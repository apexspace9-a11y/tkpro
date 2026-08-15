package vn.tietkiem.pro.ui.v4

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.tietkiem.pro.TietKiemProApplication
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.online.CloudApi
import vn.tietkiem.pro.online.CloudApiException
import vn.tietkiem.pro.online.CloudSyncManager
import vn.tietkiem.pro.security.PinSecurity
import vn.tietkiem.pro.worker.FinanceNotifications
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class V4OnlineStage { CHECKING, AUTH_REQUIRED, READY, ERROR }

data class V4OnlineState(
    val stage: V4OnlineStage = V4OnlineStage.CHECKING,
    val error: String = "",
    val email: String = "",
    val lastSyncAt: Long = 0L,
    val syncing: Boolean = false
)

class V4ViewModel(private val app: TietKiemProApplication) : ViewModel() {
    private val repo = app.financeRepository
    private val settingsRepo = app.settingsRepository
    private val backup = app.backupManager
    private val cloud = CloudSyncManager(settingsRepo, backup)

    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debts = repo.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurring = repo.recurring.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val payees = repo.payees.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactionMeta = repo.transactionMeta.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactionTags = repo.transactionTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactionSplits = repo.transactionSplits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgetConfigs = repo.budgetConfigs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goalContributions = repo.goalContributions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goalLinks = repo.goalLinks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debtPayments = repo.debtPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accountSnapshots = repo.accountSnapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val aiMessages = repo.aiMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepo.settings.map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _online = MutableStateFlow(V4OnlineState())
    val online: StateFlow<V4OnlineState> = _online
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy
    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked
    private val _adminConfig = MutableStateFlow<CloudApi.AdminConfig?>(null)
    val adminConfig: StateFlow<CloudApi.AdminConfig?> = _adminConfig
    private val _remotePayments = MutableStateFlow<List<CloudApi.RemotePayment>>(emptyList())
    val remotePayments: StateFlow<List<CloudApi.RemotePayment>> = _remotePayments
    private val _adminPayments = MutableStateFlow<List<CloudApi.RemotePayment>>(emptyList())
    val adminPayments: StateFlow<List<CloudApi.RemotePayment>> = _adminPayments

    val dashboard = combine(accounts, transactions, budgets, debts) { accs, txs, budgetList, debtList ->
        val (start, end) = monthBounds(0)
        val (prevStart, prevEnd) = monthBounds(-1)
        val current = txs.filter { it.occurredAt in start until end }
        val previous = txs.filter { it.occurredAt in prevStart until prevEnd }
        val income = current.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
        val expense = current.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
        val prevExpense = previous.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
        val monthKey = monthKey(System.currentTimeMillis())
        val activeBudgets = budgetList.filter { it.monthKey == monthKey }
        val budgetLimit = activeBudgets.sumOf { it.limitAmount }
        val budgetCategoryIds = activeBudgets.map { it.categoryId }.toSet()
        val budgetSpent = current.filter { it.type == TransactionType.EXPENSE.name && it.categoryId in budgetCategoryIds }.sumOf { it.amount }
        val accountTotal = accs.filterNot { it.archived }.sumOf { it.balance }
        val receivable = debtList.filter { it.type == DebtType.RECEIVABLE.name }.sumOf { it.remainingAmount }
        val payable = debtList.filter { it.type == DebtType.PAYABLE.name }.sumOf { it.remainingAmount }
        DashboardState(
            netWorth = accountTotal + receivable - payable,
            totalAccounts = accountTotal,
            incomeThisMonth = income,
            expenseThisMonth = expense,
            savingThisMonth = income - expense,
            savingRate = if (income > 0) (income - expense).toDouble() / income else 0.0,
            previousExpense = prevExpense,
            budgetLimit = budgetLimit,
            budgetSpent = budgetSpent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val categorySpend = combine(categories, transactions) { cats, txs ->
        val (start, end) = monthBounds(0)
        val sums = txs.asSequence()
            .filter { it.type == TransactionType.EXPENSE.name && it.occurredAt in start until end }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        cats.filter { it.type == CategoryType.EXPENSE.name }
            .map { CategorySpend(it, sums[it.id] ?: 0L) }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.ensureSeedData()
            restoreOnlineSession()
        }
    }

    fun consumeMessage() { _message.value = null }
    fun unlockWithoutPin() { _unlocked.value = true }
    fun biometricSucceeded() { _unlocked.value = true }
    fun lock() { _unlocked.value = false; _adminUnlocked.value = false }

    fun verifyPin(pin: String) {
        val current = settings.value ?: return
        _unlocked.value = current.hasPin && PinSecurity.verify(pin, current.pinSalt, current.pinHash)
        if (!_unlocked.value) _message.value = "PIN không đúng"
    }

    fun setPin(pin: String, confirm: String) = localAction("Đã cập nhật PIN") {
        require(pin.length in 4..12 && pin.all(Char::isDigit)) { "PIN cần 4–12 chữ số" }
        require(pin == confirm) { "PIN xác nhận không khớp" }
        settingsRepo.setPin(pin)
        _unlocked.value = true
    }

    fun clearPin() = localAction("Đã tắt PIN") { settingsRepo.clearPin() }
    fun setTheme(value: String) = viewModelScope.launch { settingsRepo.setTheme(value) }
    fun setBiometric(value: Boolean) = viewModelScope.launch { settingsRepo.setBiometric(value) }
    fun setNotifications(value: Boolean) = viewModelScope.launch { settingsRepo.setNotifications(value) }
    fun setPrivacyMode(value: Boolean) = viewModelScope.launch { settingsRepo.setPrivacyMode(value) }

    fun login(serverUrl: String, email: String, password: String, register: Boolean) {
        if (_online.value.syncing) return
        viewModelScope.launch {
            _online.value = V4OnlineState(V4OnlineStage.CHECKING, email = email.trim(), syncing = true)
            runCatching {
                val session = cloud.login(serverUrl, email, password, register)
                afterSessionReady(session.profile)
            }.onFailure { error ->
                _online.value = V4OnlineState(V4OnlineStage.AUTH_REQUIRED, error = error.message ?: "Không thể đăng nhập", email = email.trim())
            }
        }
    }

    fun retryOnline() { viewModelScope.launch { restoreOnlineSession() } }

    fun syncNow() {
        if (_online.value.stage != V4OnlineStage.READY || _online.value.syncing) return
        viewModelScope.launch {
            _online.value = _online.value.copy(syncing = true, error = "")
            runCatching {
                val profile = cloud.pullRemote()
                refreshPayments()
                _online.value = V4OnlineState(V4OnlineStage.READY, email = profile.email, lastSyncAt = System.currentTimeMillis())
                _message.value = "Đã đồng bộ cloud"
            }.onFailure { markOnlineFailure(it) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            cloud.logout()
            _adminUnlocked.value = false
            _adminConfig.value = null
            _remotePayments.value = emptyList()
            _adminPayments.value = emptyList()
            _online.value = V4OnlineState(V4OnlineStage.AUTH_REQUIRED)
        }
    }

    private suspend fun restoreOnlineSession() {
        _online.value = _online.value.copy(stage = V4OnlineStage.CHECKING, syncing = true, error = "")
        runCatching { cloud.restoreSession() }
            .onSuccess { session ->
                if (session == null) _online.value = V4OnlineState(V4OnlineStage.AUTH_REQUIRED)
                else afterSessionReady(session.profile)
            }
            .onFailure { error ->
                if (error is CloudApiException && error.statusCode == 401) {
                    cloud.logout()
                    _online.value = V4OnlineState(V4OnlineStage.AUTH_REQUIRED, error = "Phiên đăng nhập đã hết hạn")
                } else markOnlineFailure(error)
            }
    }

    private suspend fun afterSessionReady(profile: CloudApi.UserProfile) {
        val posted = repo.postDueRecurring()
        repo.captureAccountSnapshots()
        if (posted > 0) {
            settingsRepo.setCloudDirty(true)
            cloud.pushCurrent()
        }
        refreshPayments()
        _online.value = V4OnlineState(V4OnlineStage.READY, email = profile.email, lastSyncAt = System.currentTimeMillis())
    }

    private fun markOnlineFailure(error: Throwable) {
        _online.value = V4OnlineState(
            stage = V4OnlineStage.ERROR,
            error = error.message ?: "Mất kết nối server",
            email = settings.value?.cloudEmail.orEmpty(),
            lastSyncAt = _online.value.lastSyncAt,
            syncing = false
        )
    }

    private suspend fun refreshPayments() {
        val s = settings.value ?: return
        val token = settingsRepo.cloudToken()
        if (s.serverUrl.isBlank() || token.isBlank()) return
        _remotePayments.value = cloud.api.payments(s.serverUrl, token)
        val me = cloud.api.me(s.serverUrl, token)
        settingsRepo.applyRemoteProfile(me.premiumTier, me.premiumExpiry)
    }

    fun handleAdminCommand(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/admincp", ignoreCase = true)) return false
        val key = trimmed.substringAfter("/admincp", "").trim()
        if (key.isBlank() || key.equals("setup", ignoreCase = true)) {
            _message.value = "Nhập /admincp <admin key>"
            return true
        }
        verifyAdminKey(key)
        return true
    }

    fun verifyAdminKey(key: String) {
        if (key.isBlank()) { _message.value = "Admin key không được trống"; return }
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            if (s.serverUrl.isBlank()) { _message.value = "Chưa cấu hình server"; return@launch }
            runCatching { cloud.api.verifyAdmin(s.serverUrl, key) }
                .onSuccess { ok ->
                    if (!ok) {
                        _adminUnlocked.value = false
                        _message.value = "Admin key không đúng"
                    } else {
                        settingsRepo.saveServerAdminKey(key)
                        _adminUnlocked.value = true
                        loadAdminData()
                        _message.value = "Đã mở Admin CP"
                    }
                }
                .onFailure { _message.value = it.message ?: "Không xác thực được Admin" }
        }
    }

    fun closeAdmin() { _adminUnlocked.value = false; _adminConfig.value = null; _adminPayments.value = emptyList() }

    fun loadAdminData() {
        if (!_adminUnlocked.value) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val key = settingsRepo.serverAdminKey()
            runCatching {
                _adminConfig.value = cloud.api.adminConfig(s.serverUrl, key)
                _adminPayments.value = cloud.api.adminPayments(s.serverUrl, key)
            }.onFailure { _message.value = it.message ?: "Không tải được Admin CP" }
        }
    }

    fun saveAdminConfig(config: CloudApi.AdminConfig, newApiKey: String) {
        if (!_adminUnlocked.value) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val key = settingsRepo.serverAdminKey()
            runCatching {
                val saved = cloud.api.saveAdminConfig(s.serverUrl, key, config, newApiKey)
                _adminConfig.value = saved
                cloud.pullRemote()
            }.onSuccess { _message.value = "Đã lưu cấu hình server" }
                .onFailure { _message.value = it.message ?: "Không lưu được cấu hình" }
        }
    }

    fun testNotification() {
        val ok = FinanceNotifications.sendTest(app)
        _message.value = if (ok) "Đã gửi thông báo thử" else "Cần cấp quyền thông báo trước"
    }

    fun reviewPayment(payment: CloudApi.RemotePayment, approve: Boolean, months: Int) {
        if (!_adminUnlocked.value) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val key = settingsRepo.serverAdminKey()
            runCatching {
                cloud.api.reviewPayment(s.serverUrl, key, payment.id, approve, months)
                _adminPayments.value = cloud.api.adminPayments(s.serverUrl, key)
                refreshPayments()
            }.onSuccess { _message.value = if (approve) "Đã duyệt Premium" else "Đã từ chối yêu cầu" }
                .onFailure { _message.value = it.message ?: "Không cập nhật được yêu cầu" }
        }
    }

    fun setPremiumForUser(email: String, tier: String, months: Int) {
        if (!_adminUnlocked.value) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val key = settingsRepo.serverAdminKey()
            runCatching { cloud.api.setPremium(s.serverUrl, key, email, tier, months) }
                .onSuccess { _message.value = "Đã cập nhật Premium" }
                .onFailure { _message.value = it.message ?: "Không cập nhật được Premium" }
        }
    }

    fun createPremiumPayment(tier: PremiumTier) {
        if (tier == PremiumTier.FREE) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val token = settingsRepo.cloudToken()
            runCatching {
                cloud.api.createPayment(s.serverUrl, token, tier.name)
                refreshPayments()
            }.onSuccess { _message.value = "Đã tạo yêu cầu chuyển khoản" }
                .onFailure { _message.value = it.message ?: "Không tạo được yêu cầu" }
        }
    }

    fun sendAiMessage(text: String) {
        if (handleAdminCommand(text)) return
        if (_aiBusy.value || text.isBlank()) return
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            if (!(s.premiumActive && s.premiumTier == PremiumTier.PRO.name)) {
                _message.value = "AI online yêu cầu gói PRO"
                return@launch
            }
            _aiBusy.value = true
            runCatching {
                repo.addAiMessage("user", text.trim())
                val reply = cloud.api.aiChat(s.serverUrl, settingsRepo.cloudToken(), text.trim(), buildFinancialContext())
                repo.addAiMessage("assistant", reply)
            }.onFailure { _message.value = it.message ?: "Không thể gọi AI" }
            _aiBusy.value = false
        }
    }

    fun clearAiChat() = localAction("Đã xóa hội thoại") { repo.clearAiMessages() }

    fun saveAccount(item: AccountEntity) = financeAction("Đã lưu ví") { repo.saveAccount(item) }
    fun archiveAccount(item: AccountEntity, archived: Boolean) = financeAction(if (archived) "Đã ẩn ví" else "Đã khôi phục ví") { repo.setAccountArchived(item, archived) }
    fun saveCategory(item: CategoryEntity) = financeAction("Đã lưu danh mục") { repo.saveCategory(item) }
    fun deleteCategory(item: CategoryEntity) = financeAction("Đã xóa danh mục") { repo.deleteCategory(item) }
    fun saveTransaction(item: TransactionEntity) = financeAction("Đã lưu giao dịch") { repo.saveTransaction(item) }
    fun saveRichTransaction(item: TransactionEntity, meta: TransactionMetaEntity?, tagIds: List<Long>, splits: List<TransactionSplitEntity>) =
        financeAction("Đã lưu giao dịch") { repo.saveRichTransaction(item, meta, tagIds, splits) }
    fun deleteTransaction(item: TransactionEntity) = financeAction("Đã xóa giao dịch") { repo.deleteTransaction(item) }
    fun savePayee(item: PayeeEntity) = financeAction("Đã lưu đối tác") { repo.savePayee(item) }
    fun deletePayee(item: PayeeEntity) = financeAction("Đã xóa đối tác") { repo.deletePayee(item) }
    fun saveTag(item: TagEntity) = financeAction("Đã lưu thẻ") { repo.saveTag(item) }
    fun deleteTag(item: TagEntity) = financeAction("Đã xóa thẻ") { repo.deleteTag(item) }
    fun saveBudget(item: BudgetEntity) = financeAction("Đã lưu ngân sách") { repo.saveBudget(item) }
    fun deleteBudget(item: BudgetEntity) = financeAction("Đã xóa ngân sách") { repo.deleteBudget(item) }
    fun saveBudgetConfig(item: BudgetConfigEntity) = financeAction("Đã lưu cấu hình ngân sách") { repo.saveBudgetConfig(item) }
    fun saveGoal(item: GoalEntity) = financeAction("Đã lưu mục tiêu") { repo.saveGoal(item) }
    fun deleteGoal(item: GoalEntity) = financeAction("Đã xóa mục tiêu") { repo.deleteGoal(item) }
    fun addGoalContribution(item: GoalContributionEntity) = financeAction("Đã thêm đóng góp") { repo.addGoalContribution(item) }
    fun saveGoalLink(item: GoalLinkEntity) = financeAction("Đã liên kết mục tiêu") { repo.saveGoalLink(item) }
    fun saveDebt(item: DebtEntity) = financeAction("Đã lưu khoản nợ") { repo.saveDebt(item) }
    fun deleteDebt(item: DebtEntity) = financeAction("Đã xóa khoản nợ") { repo.deleteDebt(item) }
    fun addDebtPayment(item: DebtPaymentEntity) = financeAction("Đã ghi nhận thanh toán") { repo.addDebtPayment(item) }
    fun saveRecurring(item: RecurringEntity) = financeAction("Đã lưu định kỳ") { repo.saveRecurring(item) }
    fun deleteRecurring(item: RecurringEntity) = financeAction("Đã xóa định kỳ") { repo.deleteRecurring(item) }
    fun postDueRecurring() = financeAction("Đã cập nhật giao dịch đến hạn") { repo.postDueRecurring() }

    fun exportBackup(uri: Uri) = localAction("Đã xuất bản sao lưu") { backup.exportTo(uri) }
    fun exportTransactionsCsv(uri: Uri) = localAction("Đã xuất CSV") { backup.exportTransactionsCsv(uri) }
    fun importBackup(uri: Uri) = financeAction("Đã khôi phục và đồng bộ") { backup.importFrom(uri) }

    private fun financeAction(success: String, action: suspend () -> Unit) {
        if (_online.value.stage != V4OnlineStage.READY || _online.value.syncing) {
            _message.value = "Cần kết nối server trước"
            return
        }
        viewModelScope.launch {
            runCatching {
                action()
                settingsRepo.setCloudDirty(true)
                _online.value = _online.value.copy(syncing = true)
                cloud.pushCurrent()
            }.onSuccess {
                _online.value = _online.value.copy(stage = V4OnlineStage.READY, syncing = false, error = "", lastSyncAt = System.currentTimeMillis())
                _message.value = success
            }.onFailure { error ->
                markOnlineFailure(error)
                _message.value = "Đã lưu tạm, đang chờ đồng bộ: ${error.message ?: "lỗi kết nối"}"
            }
        }
    }

    private fun localAction(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _message.value = success }
                .onFailure { _message.value = it.message ?: "Không thể hoàn tất" }
        }
    }

    private fun buildFinancialContext(): String {
        val d = dashboard.value
        val top = categorySpend.value.take(8).joinToString { "${it.category.name}: ${it.amount}" }
        val goalText = goals.value.take(8).joinToString { "${it.name} ${it.savedAmount}/${it.targetAmount}" }
        val debtText = debts.value.filter { it.remainingAmount > 0 }.take(8).joinToString { "${it.name}: ${it.remainingAmount}" }
        val recurringText = recurring.value.filter { it.active }.take(8).joinToString { "${it.name}: ${it.amount}" }
        return buildString {
            appendLine("Tài sản ròng: ${d.netWorth}")
            appendLine("Thu tháng: ${d.incomeThisMonth}")
            appendLine("Chi tháng: ${d.expenseThisMonth}")
            appendLine("Tiết kiệm tháng: ${d.savingThisMonth}")
            appendLine("Ngân sách: ${d.budgetSpent}/${d.budgetLimit}")
            if (top.isNotBlank()) appendLine("Top chi: $top")
            if (goalText.isNotBlank()) appendLine("Mục tiêu: $goalText")
            if (debtText.isNotBlank()) appendLine("Nợ: $debtText")
            if (recurringText.isNotBlank()) appendLine("Định kỳ: $recurringText")
        }.trim()
    }

    companion object {
        fun monthKey(time: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(time)
        fun monthBounds(offset: Int): Pair<Long, Long> {
            val start = Calendar.getInstance().apply {
                add(Calendar.MONTH, offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            return start.timeInMillis to end.timeInMillis
        }
    }
}
