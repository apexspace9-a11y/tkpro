package vn.tietkiem.pro.ui

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
import vn.tietkiem.pro.ai.AiService
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.security.PinSecurity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AppViewModel(private val app: TietKiemProApplication) : ViewModel() {
    private val repo = app.financeRepository
    private val settingsRepo = app.settingsRepository
    private val backup = app.backupManager
    private val aiService = AiService(settingsRepo)

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
    val premiumPayments = repo.premiumPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepo.settings.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy
    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked
    private val _adminSetupRequested = MutableStateFlow(false)
    val adminSetupRequested: StateFlow<Boolean> = _adminSetupRequested

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
            repo.postDueRecurring()
            repo.captureAccountSnapshots()
        }
    }

    fun consumeMessage() { _message.value = null }
    fun unlockWithoutPin() { _unlocked.value = true }
    fun biometricSucceeded() { _unlocked.value = true }
    fun lock() { _unlocked.value = false; _adminUnlocked.value = false }

    fun verifyPin(pin: String) {
        val s = settings.value ?: return
        _unlocked.value = s.hasPin && PinSecurity.verify(pin, s.pinSalt, s.pinHash)
        if (!_unlocked.value) _message.value = "PIN không đúng"
    }

    fun setPin(pin: String, confirm: String) = launchAction("Đã bật khóa PIN") {
        require(pin == confirm) { "PIN xác nhận không khớp" }
        settingsRepo.setPin(pin)
        _unlocked.value = true
    }

    fun clearPin() = launchAction("Đã tắt khóa PIN") { settingsRepo.clearPin() }
    fun setTheme(theme: String) = viewModelScope.launch { settingsRepo.setTheme(theme) }
    fun setBiometric(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBiometric(enabled) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepo.setNotifications(enabled) }
    fun setPrivacyMode(enabled: Boolean) = viewModelScope.launch { settingsRepo.setPrivacyMode(enabled) }

    fun setupAdminKey(key: String, confirm: String) = launchAction("Đã tạo khóa quản trị") {
        require(key == confirm) { "Khóa xác nhận không khớp" }
        settingsRepo.setAdminKey(key)
        _adminSetupRequested.value = false
        _adminUnlocked.value = true
    }

    fun dismissAdminSetup() { _adminSetupRequested.value = false }
    fun closeAdmin() { _adminUnlocked.value = false }

    fun handleAdminCommand(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/admincp", ignoreCase = true)) return false
        val arg = trimmed.substringAfter("/admincp", "").trim()
        val current = settings.value ?: return true
        if (!current.hasAdminKey) {
            if (arg.equals("setup", ignoreCase = true)) _adminSetupRequested.value = true
            else _message.value = "Dùng /admincp setup để tạo khóa quản trị"
            return true
        }
        if (arg.isBlank()) {
            _message.value = "Thiếu khóa quản trị"
            return true
        }
        viewModelScope.launch {
            val ok = settingsRepo.verifyAdminKey(arg)
            _adminUnlocked.value = ok
            _message.value = if (ok) "Đã mở Admin CP" else "Khóa quản trị không đúng"
        }
        return true
    }

    fun saveAiConfig(endpoint: String, model: String, apiKey: String, systemPrompt: String, includeFinance: Boolean) =
        launchAction("Đã lưu cấu hình AI") {
            settingsRepo.setAiConfig(endpoint, model, systemPrompt, includeFinance)
            if (apiKey.isNotBlank()) settingsRepo.setAiApiKey(apiKey)
        }

    fun sendAiMessage(text: String) {
        if (handleAdminCommand(text)) return
        val s = settings.value ?: return
        if (!(s.premiumActive && s.premiumTier == PremiumTier.PRO.name) && !_adminUnlocked.value) {
            _message.value = "AI online thuộc gói Pro"
            return
        }
        if (_aiBusy.value || text.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            runCatching {
                repo.addAiMessage("user", text)
                val reply = aiService.chat(text, buildFinancialContext(), s)
                repo.addAiMessage("assistant", reply)
            }.onFailure {
                _message.value = it.message ?: "Không thể gọi AI"
            }
            _aiBusy.value = false
        }
    }

    fun clearAiChat() = launchAction("Đã xóa cuộc trò chuyện") { repo.clearAiMessages() }

    fun saveAccount(item: AccountEntity) = launchAction("Đã lưu ví") { repo.saveAccount(item) }
    fun saveCategory(item: CategoryEntity) = launchAction("Đã lưu danh mục") { repo.saveCategory(item) }
    fun deleteCategory(item: CategoryEntity) = launchAction("Đã xóa danh mục") { repo.deleteCategory(item) }
    fun archiveAccount(item: AccountEntity, archived: Boolean) = launchAction(if (archived) "Đã ẩn ví" else "Đã khôi phục ví") { repo.setAccountArchived(item, archived) }
    fun saveTransaction(item: TransactionEntity) = launchAction("Đã lưu giao dịch") { repo.saveTransaction(item) }
    fun saveRichTransaction(item: TransactionEntity, meta: TransactionMetaEntity?, tagIds: List<Long>, splits: List<TransactionSplitEntity>) =
        launchAction("Đã lưu giao dịch") { repo.saveRichTransaction(item, meta, tagIds, splits) }
    fun deleteTransaction(item: TransactionEntity) = launchAction("Đã xóa giao dịch") { repo.deleteTransaction(item) }
    fun savePayee(item: PayeeEntity) = launchAction("Đã lưu người nhận") { repo.savePayee(item) }
    fun deletePayee(item: PayeeEntity) = launchAction("Đã xóa người nhận") { repo.deletePayee(item) }
    fun saveTag(item: TagEntity) = launchAction("Đã lưu thẻ") { repo.saveTag(item) }
    fun deleteTag(item: TagEntity) = launchAction("Đã xóa thẻ") { repo.deleteTag(item) }
    fun saveBudget(item: BudgetEntity) = launchAction("Đã lưu ngân sách") { repo.saveBudget(item) }
    fun deleteBudget(item: BudgetEntity) = launchAction("Đã xóa ngân sách") { repo.deleteBudget(item) }
    fun saveBudgetConfig(item: BudgetConfigEntity) = launchAction("Đã lưu cấu hình ngân sách") { repo.saveBudgetConfig(item) }
    fun saveGoal(item: GoalEntity) = launchAction("Đã lưu mục tiêu") { repo.saveGoal(item) }
    fun deleteGoal(item: GoalEntity) = launchAction("Đã xóa mục tiêu") { repo.deleteGoal(item) }
    fun addGoalContribution(item: GoalContributionEntity) = launchAction("Đã thêm tiền vào mục tiêu") { repo.addGoalContribution(item) }
    fun saveGoalLink(item: GoalLinkEntity) = launchAction("Đã liên kết mục tiêu") { repo.saveGoalLink(item) }
    fun saveDebt(item: DebtEntity) = launchAction("Đã lưu khoản nợ") { repo.saveDebt(item) }
    fun deleteDebt(item: DebtEntity) = launchAction("Đã xóa khoản nợ") { repo.deleteDebt(item) }
    fun addDebtPayment(item: DebtPaymentEntity) = launchAction("Đã ghi nhận thanh toán") { repo.addDebtPayment(item) }
    fun saveRecurring(item: RecurringEntity) = launchAction("Đã lưu giao dịch định kỳ") { repo.saveRecurring(item) }
    fun deleteRecurring(item: RecurringEntity) = launchAction("Đã xóa giao dịch định kỳ") { repo.deleteRecurring(item) }
    fun postDueRecurring() = launchAction("Đã cập nhật giao dịch đến hạn") { repo.postDueRecurring() }
    fun exportBackup(uri: Uri) = launchAction("Đã xuất bản sao lưu") { backup.exportTo(uri) }
    fun exportTransactionsCsv(uri: Uri) = launchAction("Đã xuất CSV giao dịch") { backup.exportTransactionsCsv(uri) }
    fun importBackup(uri: Uri) = launchAction("Đã khôi phục dữ liệu") { backup.importFrom(uri) }

    fun createPremiumPayment(tier: PremiumTier) = launchAction("Đã tạo yêu cầu chuyển khoản") {
        val s = settings.value ?: error("Chưa tải cài đặt")
        val amount = when (tier) {
            PremiumTier.PLUS -> s.plusPrice
            PremiumTier.PRO -> s.proPrice
            PremiumTier.FREE -> 0L
        }
        require(amount > 0) { "Gói chưa có giá" }
        val code = "TKP-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)}"
        repo.createPremiumPayment(tier, amount, code)
    }

    fun approvePremiumPayment(payment: PremiumPaymentEntity, months: Int) = launchAction("Đã kích hoạt Premium") {
        require(_adminUnlocked.value) { "Cần quyền quản trị" }
        repo.updatePremiumPayment(payment.copy(status = PaymentStatus.APPROVED.name))
        settingsRepo.activatePremium(PremiumTier.valueOf(payment.plan), months)
    }

    fun rejectPremiumPayment(payment: PremiumPaymentEntity) = launchAction("Đã từ chối yêu cầu") {
        require(_adminUnlocked.value) { "Cần quyền quản trị" }
        repo.updatePremiumPayment(payment.copy(status = PaymentStatus.REJECTED.name))
    }

    fun activatePremium(tier: PremiumTier, months: Int) = launchAction("Đã cập nhật gói Premium") {
        require(_adminUnlocked.value) { "Cần quyền quản trị" }
        settingsRepo.activatePremium(tier, months)
    }

    fun deactivatePremium() = launchAction("Đã tắt Premium") {
        require(_adminUnlocked.value) { "Cần quyền quản trị" }
        settingsRepo.deactivatePremium()
    }

    fun saveBankConfig(bank: String, account: String, owner: String, plusPrice: Long, proPrice: Long) =
        launchAction("Đã lưu cấu hình thanh toán") {
            require(_adminUnlocked.value) { "Cần quyền quản trị" }
            settingsRepo.setBankConfig(bank, account, owner, plusPrice, proPrice)
        }

    private fun buildFinancialContext(): String {
        val d = dashboard.value
        val top = categorySpend.value.take(5).joinToString { "${it.category.name}: ${it.amount}" }
        val goalText = goals.value.take(5).joinToString { "${it.name} ${it.savedAmount}/${it.targetAmount}" }
        val debtText = debts.value.filter { it.remainingAmount > 0 }.take(5).joinToString { "${it.name}: ${it.remainingAmount}" }
        val recurringText = recurring.value.filter { it.active }.take(5).joinToString { "${it.name}: ${it.amount}" }
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

    private fun launchAction(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _message.value = success }
                .onFailure { _message.value = it.message ?: "Không thể hoàn tất" }
        }
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
