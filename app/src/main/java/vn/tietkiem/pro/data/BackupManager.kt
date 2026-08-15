package vn.tietkiem.pro.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val dao = db.financeDao()

    suspend fun exportTo(uri: Uri) {
        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", JSONArray().apply { dao.getAccounts().forEach { put(it.toJson()) } })
            put("categories", JSONArray().apply { dao.getCategories().forEach { put(it.toJson()) } })
            put("transactions", JSONArray().apply { dao.getTransactions().forEach { put(it.toJson()) } })
            put("budgets", JSONArray().apply { dao.getBudgets().forEach { put(it.toJson()) } })
            put("goals", JSONArray().apply { dao.getGoals().forEach { put(it.toJson()) } })
            put("debts", JSONArray().apply { dao.getDebts().forEach { put(it.toJson()) } })
            put("recurring", JSONArray().apply { dao.getRecurring().forEach { put(it.toJson()) } })
            put("payees", JSONArray().apply { dao.getPayees().forEach { put(it.toJson()) } })
            put("tags", JSONArray().apply { dao.getTags().forEach { put(it.toJson()) } })
            put("transactionMeta", JSONArray().apply { dao.getTransactionMeta().forEach { put(it.toJson()) } })
            put("transactionTags", JSONArray().apply { dao.getTransactionTags().forEach { put(it.toJson()) } })
            put("transactionSplits", JSONArray().apply { dao.getTransactionSplits().forEach { put(it.toJson()) } })
            put("budgetConfigs", JSONArray().apply { dao.getBudgetConfigs().forEach { put(it.toJson()) } })
            put("goalContributions", JSONArray().apply { dao.getGoalContributions().forEach { put(it.toJson()) } })
            put("goalLinks", JSONArray().apply { dao.getGoalLinks().forEach { put(it.toJson()) } })
            put("debtPayments", JSONArray().apply { dao.getDebtPayments().forEach { put(it.toJson()) } })
            put("accountSnapshots", JSONArray().apply { dao.getAccountSnapshots().forEach { put(it.toJson()) } })
            put("premiumPayments", JSONArray().apply { dao.getPremiumPayments().forEach { put(it.toJson()) } })
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString()) }
    }

    suspend fun exportTransactionsCsv(uri: Uri) {
        val accountMap = dao.getAccounts().associateBy { it.id }
        val categoryMap = dao.getCategories().associateBy { it.id }
        val payeeMap = dao.getPayees().associateBy { it.id }
        val tagMap = dao.getTags().associateBy { it.id }
        val metaMap = dao.getTransactionMeta().associateBy { it.transactionId }
        val tagsByTx = dao.getTransactionTags().groupBy { it.transactionId }
        val transactions = dao.getTransactions().sortedWith(compareByDescending<TransactionEntity> { it.occurredAt }.thenByDescending { it.id })
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { writer ->
            writer.appendLine("id,type,amount,account,to_account,category,payee,tags,note,occurred_at")
            transactions.forEach { tx ->
                val typeLabel = when (tx.type) {
                    TransactionType.INCOME.name -> "Thu"
                    TransactionType.EXPENSE.name -> "Chi"
                    TransactionType.TRANSFER.name -> "Chuyển"
                    else -> tx.type
                }
                val meta = metaMap[tx.id]
                val tags = tagsByTx[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name }.joinToString("|")
                val row = listOf(
                    tx.id.toString(), typeLabel, tx.amount.toString(),
                    accountMap[tx.accountId]?.name.orEmpty(),
                    tx.toAccountId?.let { accountMap[it]?.name }.orEmpty(),
                    tx.categoryId?.let { categoryMap[it]?.name }.orEmpty(),
                    meta?.payeeId?.let { payeeMap[it]?.name }.orEmpty(),
                    tags, tx.note, dateFormat.format(tx.occurredAt)
                ).joinToString(",") { csvCell(it) }
                writer.appendLine(row)
            }
        }
    }

    suspend fun importFrom(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val version = root.getInt("schemaVersion")
        require(version in 1..2) { "Phiên bản bản sao lưu không được hỗ trợ" }

        val accounts = root.getJSONArray("accounts").mapObjects(::accountFromJson)
        val categories = root.getJSONArray("categories").mapObjects(::categoryFromJson)
        val transactions = root.getJSONArray("transactions").mapObjects(::transactionFromJson)
        val budgets = root.getJSONArray("budgets").mapObjects(::budgetFromJson)
        val goals = root.getJSONArray("goals").mapObjects(::goalFromJson)
        val debts = root.getJSONArray("debts").mapObjects(::debtFromJson)
        val recurring = root.getJSONArray("recurring").mapObjects(::recurringFromJson)

        val payees = root.optJSONArray("payees").mapObjectsOrEmpty(::payeeFromJson)
        val tags = root.optJSONArray("tags").mapObjectsOrEmpty(::tagFromJson)
        val metas = root.optJSONArray("transactionMeta").mapObjectsOrEmpty(::metaFromJson)
        val txTags = root.optJSONArray("transactionTags").mapObjectsOrEmpty(::txTagFromJson)
        val splits = root.optJSONArray("transactionSplits").mapObjectsOrEmpty(::splitFromJson)
        val budgetConfigs = root.optJSONArray("budgetConfigs").mapObjectsOrEmpty(::budgetConfigFromJson)
        val contributions = root.optJSONArray("goalContributions").mapObjectsOrEmpty(::contributionFromJson)
        val goalLinks = root.optJSONArray("goalLinks").mapObjectsOrEmpty(::goalLinkFromJson)
        val debtPayments = root.optJSONArray("debtPayments").mapObjectsOrEmpty(::debtPaymentFromJson)
        val snapshots = root.optJSONArray("accountSnapshots").mapObjectsOrEmpty(::snapshotFromJson)
        val premiumPayments = root.optJSONArray("premiumPayments").mapObjectsOrEmpty(::premiumPaymentFromJson)

        db.withTransaction {
            dao.clearAiChatMessages(); dao.clearPremiumPayments(); dao.clearAccountSnapshots(); dao.clearDebtPayments()
            dao.clearGoalLinks(); dao.clearGoalContributions(); dao.clearBudgetConfigs(); dao.clearTransactionSplits()
            dao.clearTransactionTags(); dao.clearTransactionMeta(); dao.clearTags(); dao.clearPayees()
            dao.clearRecurring(); dao.clearBudgets(); dao.clearTransactions(); dao.clearGoals(); dao.clearDebts(); dao.clearCategories(); dao.clearAccounts()

            dao.insertAccountsRaw(accounts)
            dao.insertCategoriesRaw(categories)
            dao.insertTransactionsRaw(transactions)
            dao.insertBudgetsRaw(budgets)
            dao.insertGoalsRaw(goals)
            dao.insertDebtsRaw(debts)
            dao.insertRecurringRaw(recurring)
            if (payees.isNotEmpty()) dao.insertPayeesRaw(payees)
            if (tags.isNotEmpty()) dao.insertTagsRaw(tags)
            if (metas.isNotEmpty()) dao.insertTransactionMetaRaw(metas)
            if (txTags.isNotEmpty()) dao.insertTransactionTagsRaw(txTags)
            if (splits.isNotEmpty()) dao.insertTransactionSplitsRaw(splits)
            if (budgetConfigs.isNotEmpty()) dao.insertBudgetConfigsRaw(budgetConfigs)
            if (contributions.isNotEmpty()) dao.insertGoalContributionsRaw(contributions)
            if (goalLinks.isNotEmpty()) dao.insertGoalLinksRaw(goalLinks)
            if (debtPayments.isNotEmpty()) dao.insertDebtPaymentsRaw(debtPayments)
            if (snapshots.isNotEmpty()) dao.insertAccountSnapshotsRaw(snapshots)
            if (premiumPayments.isNotEmpty()) dao.insertPremiumPaymentsRaw(premiumPayments)
        }
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> = (0 until length()).map { mapper(getJSONObject(it)) }
    private fun <T> JSONArray?.mapObjectsOrEmpty(mapper: (JSONObject) -> T): List<T> = this?.let { a -> (0 until a.length()).map { mapper(a.getJSONObject(it)) } } ?: emptyList()
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

    private fun AccountEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("balance", balance); put("archived", archived); put("createdAt", createdAt) }
    private fun CategoryEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("icon", icon); put("sortOrder", sortOrder) }
    private fun TransactionEntity.toJson() = JSONObject().apply { put("id", id); put("type", type); put("amount", amount); put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL); put("categoryId", categoryId ?: JSONObject.NULL); put("note", note); put("occurredAt", occurredAt); put("createdAt", createdAt) }
    private fun BudgetEntity.toJson() = JSONObject().apply { put("id", id); put("monthKey", monthKey); put("categoryId", categoryId); put("limitAmount", limitAmount) }
    private fun GoalEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("targetAmount", targetAmount); put("savedAmount", savedAmount); put("deadline", deadline ?: JSONObject.NULL); put("createdAt", createdAt) }
    private fun DebtEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("originalAmount", originalAmount); put("remainingAmount", remainingAmount); put("annualInterestRate", annualInterestRate); put("dueDate", dueDate ?: JSONObject.NULL); put("note", note); put("createdAt", createdAt) }
    private fun RecurringEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type); put("amount", amount); put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL); put("categoryId", categoryId ?: JSONObject.NULL); put("interval", interval); put("nextDueAt", nextDueAt); put("note", note); put("active", active) }
    private fun PayeeEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("defaultCategoryId", defaultCategoryId ?: JSONObject.NULL); put("createdAt", createdAt) }
    private fun TagEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("createdAt", createdAt) }
    private fun TransactionMetaEntity.toJson() = JSONObject().apply { put("transactionId", transactionId); put("payeeId", payeeId ?: JSONObject.NULL); put("attachmentUri", attachmentUri); put("merchantText", merchantText); put("isSubscription", isSubscription) }
    private fun TransactionTagEntity.toJson() = JSONObject().apply { put("transactionId", transactionId); put("tagId", tagId) }
    private fun TransactionSplitEntity.toJson() = JSONObject().apply { put("id", id); put("transactionId", transactionId); put("categoryId", categoryId ?: JSONObject.NULL); put("amount", amount); put("note", note) }
    private fun BudgetConfigEntity.toJson() = JSONObject().apply { put("categoryId", categoryId); put("rolloverEnabled", rolloverEnabled); put("carryAmount", carryAmount); put("envelopeTarget", envelopeTarget) }
    private fun GoalContributionEntity.toJson() = JSONObject().apply { put("id", id); put("goalId", goalId); put("amount", amount); put("occurredAt", occurredAt); put("note", note) }
    private fun GoalLinkEntity.toJson() = JSONObject().apply { put("goalId", goalId); put("accountId", accountId ?: JSONObject.NULL) }
    private fun DebtPaymentEntity.toJson() = JSONObject().apply { put("id", id); put("debtId", debtId); put("amount", amount); put("interestAmount", interestAmount); put("occurredAt", occurredAt); put("note", note) }
    private fun AccountSnapshotEntity.toJson() = JSONObject().apply { put("id", id); put("accountId", accountId); put("balance", balance); put("capturedAt", capturedAt) }
    private fun PremiumPaymentEntity.toJson() = JSONObject().apply { put("id", id); put("plan", plan); put("amount", amount); put("transferCode", transferCode); put("status", status); put("createdAt", createdAt) }

    private fun accountFromJson(o: JSONObject) = AccountEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("balance"), o.getBoolean("archived"), o.getLong("createdAt"))
    private fun categoryFromJson(o: JSONObject) = CategoryEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getString("icon"), o.getInt("sortOrder"))
    private fun transactionFromJson(o: JSONObject) = TransactionEntity(o.getLong("id"), o.getString("type"), o.getLong("amount"), o.getLong("accountId"), o.nullableLong("toAccountId"), o.nullableLong("categoryId"), o.getString("note"), o.getLong("occurredAt"), o.getLong("createdAt"))
    private fun budgetFromJson(o: JSONObject) = BudgetEntity(o.getLong("id"), o.getString("monthKey"), o.getLong("categoryId"), o.getLong("limitAmount"))
    private fun goalFromJson(o: JSONObject) = GoalEntity(o.getLong("id"), o.getString("name"), o.getLong("targetAmount"), o.getLong("savedAmount"), o.nullableLong("deadline"), o.getLong("createdAt"))
    private fun debtFromJson(o: JSONObject) = DebtEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("originalAmount"), o.getLong("remainingAmount"), o.getDouble("annualInterestRate"), o.nullableLong("dueDate"), o.getString("note"), o.getLong("createdAt"))
    private fun recurringFromJson(o: JSONObject) = RecurringEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("amount"), o.getLong("accountId"), o.nullableLong("toAccountId"), o.nullableLong("categoryId"), o.getString("interval"), o.getLong("nextDueAt"), o.getString("note"), o.getBoolean("active"))
    private fun payeeFromJson(o: JSONObject) = PayeeEntity(o.getLong("id"), o.getString("name"), o.nullableLong("defaultCategoryId"), o.getLong("createdAt"))
    private fun tagFromJson(o: JSONObject) = TagEntity(o.getLong("id"), o.getString("name"), o.getLong("createdAt"))
    private fun metaFromJson(o: JSONObject) = TransactionMetaEntity(o.getLong("transactionId"), o.nullableLong("payeeId"), o.optString("attachmentUri"), o.optString("merchantText"), o.optBoolean("isSubscription", false))
    private fun txTagFromJson(o: JSONObject) = TransactionTagEntity(o.getLong("transactionId"), o.getLong("tagId"))
    private fun splitFromJson(o: JSONObject) = TransactionSplitEntity(o.getLong("id"), o.getLong("transactionId"), o.nullableLong("categoryId"), o.getLong("amount"), o.optString("note"))
    private fun budgetConfigFromJson(o: JSONObject) = BudgetConfigEntity(o.getLong("categoryId"), o.optBoolean("rolloverEnabled", false), o.optLong("carryAmount", 0), o.optLong("envelopeTarget", 0))
    private fun contributionFromJson(o: JSONObject) = GoalContributionEntity(o.getLong("id"), o.getLong("goalId"), o.getLong("amount"), o.getLong("occurredAt"), o.optString("note"))
    private fun goalLinkFromJson(o: JSONObject) = GoalLinkEntity(o.getLong("goalId"), o.nullableLong("accountId"))
    private fun debtPaymentFromJson(o: JSONObject) = DebtPaymentEntity(o.getLong("id"), o.getLong("debtId"), o.getLong("amount"), o.optLong("interestAmount", 0), o.getLong("occurredAt"), o.optString("note"))
    private fun snapshotFromJson(o: JSONObject) = AccountSnapshotEntity(o.getLong("id"), o.getLong("accountId"), o.getLong("balance"), o.getLong("capturedAt"))
    private fun premiumPaymentFromJson(o: JSONObject) = PremiumPaymentEntity(o.getLong("id"), o.getString("plan"), o.getLong("amount"), o.getString("transferCode"), o.getString("status"), o.getLong("createdAt"))
}
