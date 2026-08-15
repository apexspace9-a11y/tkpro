package vn.tietkiem.pro.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val dao = db.financeDao()

    suspend fun exportTo(uri: Uri) {
        val accounts = dao.getAccounts()
        val categories = dao.getCategories()
        val transactions = dao.getTransactions()
        val budgets = dao.getBudgets()
        val goals = dao.getGoals()
        val debts = dao.getDebts()
        val recurring = dao.getRecurring()

        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", JSONArray().apply { accounts.forEach { put(it.toJson()) } })
            put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
            put("transactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
            put("budgets", JSONArray().apply { budgets.forEach { put(it.toJson()) } })
            put("goals", JSONArray().apply { goals.forEach { put(it.toJson()) } })
            put("debts", JSONArray().apply { debts.forEach { put(it.toJson()) } })
            put("recurring", JSONArray().apply { recurring.forEach { put(it.toJson()) } })
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString()) }
    }

    suspend fun importFrom(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Phiên bản bản sao lưu không được hỗ trợ" }

        val accounts = root.getJSONArray("accounts").mapObjects(::accountFromJson)
        val categories = root.getJSONArray("categories").mapObjects(::categoryFromJson)
        val transactions = root.getJSONArray("transactions").mapObjects(::transactionFromJson)
        val budgets = root.getJSONArray("budgets").mapObjects(::budgetFromJson)
        val goals = root.getJSONArray("goals").mapObjects(::goalFromJson)
        val debts = root.getJSONArray("debts").mapObjects(::debtFromJson)
        val recurring = root.getJSONArray("recurring").mapObjects(::recurringFromJson)

        db.withTransaction {
            dao.clearRecurring(); dao.clearBudgets(); dao.clearTransactions(); dao.clearGoals(); dao.clearDebts(); dao.clearCategories(); dao.clearAccounts()
            dao.insertAccountsRaw(accounts)
            dao.insertCategoriesRaw(categories)
            dao.insertTransactionsRaw(transactions)
            dao.insertBudgetsRaw(budgets)
            dao.insertGoalsRaw(goals)
            dao.insertDebtsRaw(debts)
            dao.insertRecurringRaw(recurring)
        }
    }

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun AccountEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("type", type); put("balance", balance); put("archived", archived); put("createdAt", createdAt)
    }
    private fun CategoryEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("type", type); put("icon", icon); put("sortOrder", sortOrder)
    }
    private fun TransactionEntity.toJson() = JSONObject().apply {
        put("id", id); put("type", type); put("amount", amount); put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL); put("categoryId", categoryId ?: JSONObject.NULL); put("note", note); put("occurredAt", occurredAt); put("createdAt", createdAt)
    }
    private fun BudgetEntity.toJson() = JSONObject().apply { put("id", id); put("monthKey", monthKey); put("categoryId", categoryId); put("limitAmount", limitAmount) }
    private fun GoalEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("targetAmount", targetAmount); put("savedAmount", savedAmount); put("deadline", deadline ?: JSONObject.NULL); put("createdAt", createdAt) }
    private fun DebtEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("originalAmount", originalAmount); put("remainingAmount", remainingAmount); put("annualInterestRate", annualInterestRate); put("dueDate", dueDate ?: JSONObject.NULL); put("note", note); put("createdAt", createdAt) }
    private fun RecurringEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("amount", amount); put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL); put("categoryId", categoryId ?: JSONObject.NULL); put("interval", interval); put("nextDueAt", nextDueAt); put("note", note); put("active", active) }

    private fun accountFromJson(o: JSONObject) = AccountEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("balance"), o.getBoolean("archived"), o.getLong("createdAt"))
    private fun categoryFromJson(o: JSONObject) = CategoryEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getString("icon"), o.getInt("sortOrder"))
    private fun transactionFromJson(o: JSONObject) = TransactionEntity(o.getLong("id"), o.getString("type"), o.getLong("amount"), o.getLong("accountId"), o.nullableLong("toAccountId"), o.nullableLong("categoryId"), o.getString("note"), o.getLong("occurredAt"), o.getLong("createdAt"))
    private fun budgetFromJson(o: JSONObject) = BudgetEntity(o.getLong("id"), o.getString("monthKey"), o.getLong("categoryId"), o.getLong("limitAmount"))
    private fun goalFromJson(o: JSONObject) = GoalEntity(o.getLong("id"), o.getString("name"), o.getLong("targetAmount"), o.getLong("savedAmount"), o.nullableLong("deadline"), o.getLong("createdAt"))
    private fun debtFromJson(o: JSONObject) = DebtEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("originalAmount"), o.getLong("remainingAmount"), o.getDouble("annualInterestRate"), o.nullableLong("dueDate"), o.getString("note"), o.getLong("createdAt"))
    private fun recurringFromJson(o: JSONObject) = RecurringEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("amount"), o.getLong("accountId"), o.nullableLong("toAccountId"), o.nullableLong("categoryId"), o.getString("interval"), o.getLong("nextDueAt"), o.getString("note"), o.getBoolean("active"))
}
