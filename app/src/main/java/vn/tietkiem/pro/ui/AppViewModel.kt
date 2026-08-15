package vn.tietkiem.pro.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.tietkiem.pro.TietKiemProApplication
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.security.PinSecurity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppViewModel(private val app: TietKiemProApplication) : ViewModel() {
    private val repo = app.financeRepository
    private val settingsRepo = app.settingsRepository
    private val backup = app.backupManager

    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debts = repo.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurring = repo.recurring.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepo.settings.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

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
        viewModelScope.launch { repo.ensureSeedData(); repo.postDueRecurring() }
    }

    fun consumeMessage() { _message.value = null }
    fun unlockWithoutPin() { _unlocked.value = true }
    fun biometricSucceeded() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }

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

    fun saveAccount(item: AccountEntity) = launchAction("Đã lưu ví") { repo.saveAccount(item) }
    fun saveCategory(item: CategoryEntity) = launchAction("Đã lưu danh mục") { repo.saveCategory(item) }
    fun deleteCategory(item: CategoryEntity) = launchAction("Đã xóa danh mục") { repo.deleteCategory(item) }
    fun archiveAccount(item: AccountEntity, archived: Boolean) = launchAction(if (archived) "Đã ẩn ví" else "Đã khôi phục ví") { repo.setAccountArchived(item, archived) }
    fun saveTransaction(item: TransactionEntity) = launchAction("Đã lưu giao dịch") { repo.saveTransaction(item) }
    fun deleteTransaction(item: TransactionEntity) = launchAction("Đã xóa giao dịch") { repo.deleteTransaction(item) }
    fun saveBudget(item: BudgetEntity) = launchAction("Đã lưu ngân sách") { repo.saveBudget(item) }
    fun deleteBudget(item: BudgetEntity) = launchAction("Đã xóa ngân sách") { repo.deleteBudget(item) }
    fun saveGoal(item: GoalEntity) = launchAction("Đã lưu mục tiêu") { repo.saveGoal(item) }
    fun deleteGoal(item: GoalEntity) = launchAction("Đã xóa mục tiêu") { repo.deleteGoal(item) }
    fun saveDebt(item: DebtEntity) = launchAction("Đã lưu khoản nợ") { repo.saveDebt(item) }
    fun deleteDebt(item: DebtEntity) = launchAction("Đã xóa khoản nợ") { repo.deleteDebt(item) }
    fun saveRecurring(item: RecurringEntity) = launchAction("Đã lưu giao dịch định kỳ") { repo.saveRecurring(item) }
    fun deleteRecurring(item: RecurringEntity) = launchAction("Đã xóa giao dịch định kỳ") { repo.deleteRecurring(item) }
    fun postDueRecurring() = launchAction("Đã cập nhật giao dịch đến hạn") { repo.postDueRecurring() }
    fun exportBackup(uri: Uri) = launchAction("Đã xuất bản sao lưu") { backup.exportTo(uri) }
    fun exportTransactionsCsv(uri: Uri) = launchAction("Đã xuất CSV giao dịch") { backup.exportTransactionsCsv(uri) }
    fun importBackup(uri: Uri) = launchAction("Đã khôi phục dữ liệu") { backup.importFrom(uri) }

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
