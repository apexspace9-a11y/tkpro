package vn.tietkiem.pro.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FinanceRepository(private val db: AppDatabase) {
    private val dao = db.financeDao()

    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val budgets: Flow<List<BudgetEntity>> = dao.observeBudgets()
    val goals: Flow<List<GoalEntity>> = dao.observeGoals()
    val debts: Flow<List<DebtEntity>> = dao.observeDebts()
    val recurring: Flow<List<RecurringEntity>> = dao.observeRecurring()

    suspend fun ensureSeedData() = db.withTransaction {
        if (dao.getAccounts().isEmpty()) {
            dao.insertAccount(AccountEntity(name = "Tiền mặt", type = AccountType.CASH.name, balance = 0))
        }
        if (dao.getCategories().isEmpty()) {
            val defaults = listOf(
            CategoryEntity(name = "Ăn uống", type = CategoryType.EXPENSE.name, icon = "restaurant", sortOrder = 10),
            CategoryEntity(name = "Đi lại", type = CategoryType.EXPENSE.name, icon = "directions_car", sortOrder = 20),
            CategoryEntity(name = "Nhà ở", type = CategoryType.EXPENSE.name, icon = "home", sortOrder = 30),
            CategoryEntity(name = "Hóa đơn", type = CategoryType.EXPENSE.name, icon = "receipt", sortOrder = 40),
            CategoryEntity(name = "Mua sắm", type = CategoryType.EXPENSE.name, icon = "shopping_bag", sortOrder = 50),
            CategoryEntity(name = "Sức khỏe", type = CategoryType.EXPENSE.name, icon = "health", sortOrder = 60),
            CategoryEntity(name = "Giải trí", type = CategoryType.EXPENSE.name, icon = "movie", sortOrder = 70),
            CategoryEntity(name = "Giáo dục", type = CategoryType.EXPENSE.name, icon = "school", sortOrder = 80),
            CategoryEntity(name = "Khác", type = CategoryType.EXPENSE.name, icon = "more", sortOrder = 90),
            CategoryEntity(name = "Lương", type = CategoryType.INCOME.name, icon = "payments", sortOrder = 10),
            CategoryEntity(name = "Thưởng", type = CategoryType.INCOME.name, icon = "star", sortOrder = 20),
            CategoryEntity(name = "Đầu tư", type = CategoryType.INCOME.name, icon = "trending_up", sortOrder = 30),
            CategoryEntity(name = "Thu khác", type = CategoryType.INCOME.name, icon = "add", sortOrder = 40)
            )
            dao.insertCategories(defaults)
        }
    }

    suspend fun saveAccount(item: AccountEntity) {
        if (item.id == 0L) dao.insertAccount(item) else dao.updateAccount(item)
    }

    suspend fun setAccountArchived(account: AccountEntity, archived: Boolean) {
        dao.updateAccount(account.copy(archived = archived))
    }

    suspend fun saveTransaction(item: TransactionEntity) = db.withTransaction {
        saveTransactionCore(item)
    }

    private suspend fun saveTransactionCore(item: TransactionEntity) {
        require(item.amount > 0) { "Số tiền phải lớn hơn 0" }
        validateTransaction(item)
        val old = if (item.id == 0L) null else dao.getTransaction(item.id)
        if (old != null) reverseTransaction(old)
        if (old == null) dao.insertTransaction(item) else dao.updateTransaction(item)
        applyTransaction(item)
    }

    suspend fun deleteTransaction(item: TransactionEntity) = db.withTransaction {
        reverseTransaction(item)
        dao.deleteTransaction(item)
    }

    private suspend fun validateTransaction(item: TransactionEntity) {
        require(dao.getAccount(item.accountId) != null) { "Ví nguồn không tồn tại" }
        if (item.type == TransactionType.TRANSFER.name) {
            val target = requireNotNull(item.toAccountId) { "Thiếu ví nhận" }
            require(target != item.accountId) { "Ví nhận phải khác ví nguồn" }
            require(dao.getAccount(target) != null) { "Ví nhận không tồn tại" }
        }
    }

    private suspend fun applyTransaction(item: TransactionEntity) {
        when (TransactionType.valueOf(item.type)) {
            TransactionType.INCOME -> dao.adjustBalance(item.accountId, item.amount)
            TransactionType.EXPENSE -> dao.adjustBalance(item.accountId, -item.amount)
            TransactionType.TRANSFER -> {
                dao.adjustBalance(item.accountId, -item.amount)
                dao.adjustBalance(requireNotNull(item.toAccountId), item.amount)
            }
        }
    }

    private suspend fun reverseTransaction(item: TransactionEntity) {
        when (TransactionType.valueOf(item.type)) {
            TransactionType.INCOME -> dao.adjustBalance(item.accountId, -item.amount)
            TransactionType.EXPENSE -> dao.adjustBalance(item.accountId, item.amount)
            TransactionType.TRANSFER -> {
                dao.adjustBalance(item.accountId, item.amount)
                dao.adjustBalance(requireNotNull(item.toAccountId), -item.amount)
            }
        }
    }

    suspend fun saveCategory(item: CategoryEntity) {
        require(item.name.isNotBlank()) { "Tên danh mục không được trống" }
        if (item.id == 0L) dao.insertCategory(item) else dao.updateCategory(item)
    }
    suspend fun deleteCategory(item: CategoryEntity) = dao.deleteCategory(item)

    suspend fun saveBudget(item: BudgetEntity) {
        require(item.limitAmount > 0) { "Ngân sách phải lớn hơn 0" }
        dao.upsertBudget(item)
    }
    suspend fun deleteBudget(item: BudgetEntity) = dao.deleteBudget(item)

    suspend fun saveGoal(item: GoalEntity) {
        require(item.name.isNotBlank()) { "Tên mục tiêu không được trống" }
        require(item.targetAmount > 0) { "Số tiền mục tiêu phải lớn hơn 0" }
        require(item.savedAmount >= 0) { "Số tiền đã tiết kiệm không hợp lệ" }
        dao.upsertGoal(item)
    }
    suspend fun deleteGoal(item: GoalEntity) = dao.deleteGoal(item)

    suspend fun saveDebt(item: DebtEntity) {
        require(item.name.isNotBlank()) { "Tên khoản nợ không được trống" }
        require(item.originalAmount > 0) { "Số tiền khoản nợ phải lớn hơn 0" }
        require(item.remainingAmount >= 0) { "Số dư nợ không hợp lệ" }
        require(item.annualInterestRate >= 0) { "Lãi suất không hợp lệ" }
        dao.upsertDebt(item)
    }
    suspend fun deleteDebt(item: DebtEntity) = dao.deleteDebt(item)

    suspend fun saveRecurring(item: RecurringEntity) {
        require(item.name.isNotBlank()) { "Tên giao dịch định kỳ không được trống" }
        require(item.amount > 0) { "Số tiền phải lớn hơn 0" }
        validateTransaction(TransactionEntity(type = item.type, amount = item.amount, accountId = item.accountId, toAccountId = item.toAccountId, categoryId = item.categoryId))
        RecurringInterval.valueOf(item.interval)
        dao.upsertRecurring(item)
    }
    suspend fun deleteRecurring(item: RecurringEntity) = dao.deleteRecurring(item)

    suspend fun postDueRecurring(now: Long = System.currentTimeMillis()): Int = db.withTransaction {
        val due = dao.getDueRecurring(now)
        var posted = 0
        due.forEach { recurring ->
            var next = recurring.nextDueAt
            var safety = 0
            while (next <= now && safety < 120) {
                saveTransactionCore(
                    TransactionEntity(
                        type = recurring.type,
                        amount = recurring.amount,
                        accountId = recurring.accountId,
                        toAccountId = recurring.toAccountId,
                        categoryId = recurring.categoryId,
                        note = recurring.note.ifBlank { recurring.name },
                        occurredAt = next
                    )
                )
                posted++
                safety++
                next = nextOccurrence(next, recurring.interval)
            }
            dao.upsertRecurring(recurring.copy(nextDueAt = next))
        }
        posted
    }

    private fun nextOccurrence(current: Long, interval: String): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = current }
        when (RecurringInterval.valueOf(interval)) {
            RecurringInterval.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            RecurringInterval.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringInterval.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RecurringInterval.YEARLY -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
