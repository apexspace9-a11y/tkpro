package vn.tietkiem.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY archived ASC, createdAt ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM budgets ORDER BY monthKey DESC")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDueAt ASC")
    fun observeRecurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM payees ORDER BY name")
    fun observePayees(): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM transaction_meta")
    fun observeTransactionMeta(): Flow<List<TransactionMetaEntity>>

    @Query("SELECT * FROM transaction_tags")
    fun observeTransactionTags(): Flow<List<TransactionTagEntity>>

    @Query("SELECT * FROM transaction_splits ORDER BY id")
    fun observeTransactionSplits(): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM budget_configs")
    fun observeBudgetConfigs(): Flow<List<BudgetConfigEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY occurredAt DESC, id DESC")
    fun observeGoalContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_links")
    fun observeGoalLinks(): Flow<List<GoalLinkEntity>>

    @Query("SELECT * FROM debt_payments ORDER BY occurredAt DESC, id DESC")
    fun observeDebtPayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM account_snapshots ORDER BY capturedAt DESC")
    fun observeAccountSnapshots(): Flow<List<AccountSnapshotEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAiMessages(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM premium_payments ORDER BY createdAt DESC")
    fun observePremiumPayments(): Flow<List<PremiumPaymentEntity>>

    @Query("SELECT * FROM accounts") suspend fun getAccounts(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun getCategories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun getTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets") suspend fun getBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM goals") suspend fun getGoals(): List<GoalEntity>
    @Query("SELECT * FROM debts") suspend fun getDebts(): List<DebtEntity>
    @Query("SELECT * FROM recurring") suspend fun getRecurring(): List<RecurringEntity>
    @Query("SELECT * FROM payees") suspend fun getPayees(): List<PayeeEntity>
    @Query("SELECT * FROM tags") suspend fun getTags(): List<TagEntity>
    @Query("SELECT * FROM transaction_meta") suspend fun getTransactionMeta(): List<TransactionMetaEntity>
    @Query("SELECT * FROM transaction_tags") suspend fun getTransactionTags(): List<TransactionTagEntity>
    @Query("SELECT * FROM transaction_splits") suspend fun getTransactionSplits(): List<TransactionSplitEntity>
    @Query("SELECT * FROM budget_configs") suspend fun getBudgetConfigs(): List<BudgetConfigEntity>
    @Query("SELECT * FROM goal_contributions") suspend fun getGoalContributions(): List<GoalContributionEntity>
    @Query("SELECT * FROM goal_links") suspend fun getGoalLinks(): List<GoalLinkEntity>
    @Query("SELECT * FROM debt_payments") suspend fun getDebtPayments(): List<DebtPaymentEntity>
    @Query("SELECT * FROM account_snapshots") suspend fun getAccountSnapshots(): List<AccountSnapshotEntity>
    @Query("SELECT * FROM ai_chat_messages") suspend fun getAiMessages(): List<AiChatMessageEntity>
    @Query("SELECT * FROM premium_payments") suspend fun getPremiumPayments(): List<PremiumPaymentEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1") suspend fun getAccount(id: Long): AccountEntity?
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1") suspend fun getTransaction(id: Long): TransactionEntity?
    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1") suspend fun getGoal(id: Long): GoalEntity?
    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1") suspend fun getDebt(id: Long): DebtEntity?
    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDueAt <= :now ORDER BY nextDueAt") suspend fun getDueRecurring(now: Long): List<RecurringEntity>
    @Query("SELECT * FROM transaction_meta WHERE transactionId = :transactionId LIMIT 1") suspend fun getTransactionMeta(transactionId: Long): TransactionMetaEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(item: AccountEntity): Long
    @Update suspend fun updateAccount(item: AccountEntity)
    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :id") suspend fun adjustBalance(id: Long, delta: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategory(item: CategoryEntity): Long
    @Update suspend fun updateCategory(item: CategoryEntity)
    @Delete suspend fun deleteCategory(item: CategoryEntity)

    @Insert suspend fun insertTransaction(item: TransactionEntity): Long
    @Update suspend fun updateTransaction(item: TransactionEntity)
    @Delete suspend fun deleteTransaction(item: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(item: BudgetEntity): Long
    @Delete suspend fun deleteBudget(item: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(item: GoalEntity): Long
    @Delete suspend fun deleteGoal(item: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDebt(item: DebtEntity): Long
    @Delete suspend fun deleteDebt(item: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRecurring(item: RecurringEntity): Long
    @Delete suspend fun deleteRecurring(item: RecurringEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPayee(item: PayeeEntity): Long
    @Delete suspend fun deletePayee(item: PayeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTag(item: TagEntity): Long
    @Delete suspend fun deleteTag(item: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransactionMeta(item: TransactionMetaEntity)
    @Query("DELETE FROM transaction_meta WHERE transactionId = :transactionId") suspend fun deleteTransactionMeta(transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactionTags(items: List<TransactionTagEntity>)
    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId") suspend fun clearTransactionTags(transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactionSplits(items: List<TransactionSplitEntity>)
    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId") suspend fun clearTransactionSplits(transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudgetConfig(item: BudgetConfigEntity)

    @Insert suspend fun insertGoalContribution(item: GoalContributionEntity): Long
    @Delete suspend fun deleteGoalContribution(item: GoalContributionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoalLink(item: GoalLinkEntity)

    @Insert suspend fun insertDebtPayment(item: DebtPaymentEntity): Long
    @Delete suspend fun deleteDebtPayment(item: DebtPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccountSnapshots(items: List<AccountSnapshotEntity>)
    @Query("DELETE FROM account_snapshots WHERE capturedAt < :before") suspend fun deleteOldSnapshots(before: Long)

    @Insert suspend fun insertAiMessage(item: AiChatMessageEntity): Long
    @Query("DELETE FROM ai_chat_messages") suspend fun clearAiMessages()

    @Insert suspend fun insertPremiumPayment(item: PremiumPaymentEntity): Long
    @Update suspend fun updatePremiumPayment(item: PremiumPaymentEntity)

    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM premium_payments") suspend fun clearPremiumPayments()
    @Query("DELETE FROM ai_chat_messages") suspend fun clearAiChatMessages()
    @Query("DELETE FROM account_snapshots") suspend fun clearAccountSnapshots()
    @Query("DELETE FROM debt_payments") suspend fun clearDebtPayments()
    @Query("DELETE FROM goal_links") suspend fun clearGoalLinks()
    @Query("DELETE FROM goal_contributions") suspend fun clearGoalContributions()
    @Query("DELETE FROM budget_configs") suspend fun clearBudgetConfigs()
    @Query("DELETE FROM transaction_splits") suspend fun clearTransactionSplits()
    @Query("DELETE FROM transaction_tags") suspend fun clearTransactionTags()
    @Query("DELETE FROM transaction_meta") suspend fun clearTransactionMeta()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM payees") suspend fun clearPayees()

    @Insert suspend fun insertAccountsRaw(items: List<AccountEntity>)
    @Insert suspend fun insertCategoriesRaw(items: List<CategoryEntity>)
    @Insert suspend fun insertTransactionsRaw(items: List<TransactionEntity>)
    @Insert suspend fun insertBudgetsRaw(items: List<BudgetEntity>)
    @Insert suspend fun insertGoalsRaw(items: List<GoalEntity>)
    @Insert suspend fun insertDebtsRaw(items: List<DebtEntity>)
    @Insert suspend fun insertRecurringRaw(items: List<RecurringEntity>)
    @Insert suspend fun insertPayeesRaw(items: List<PayeeEntity>)
    @Insert suspend fun insertTagsRaw(items: List<TagEntity>)
    @Insert suspend fun insertTransactionMetaRaw(items: List<TransactionMetaEntity>)
    @Insert suspend fun insertTransactionTagsRaw(items: List<TransactionTagEntity>)
    @Insert suspend fun insertTransactionSplitsRaw(items: List<TransactionSplitEntity>)
    @Insert suspend fun insertBudgetConfigsRaw(items: List<BudgetConfigEntity>)
    @Insert suspend fun insertGoalContributionsRaw(items: List<GoalContributionEntity>)
    @Insert suspend fun insertGoalLinksRaw(items: List<GoalLinkEntity>)
    @Insert suspend fun insertDebtPaymentsRaw(items: List<DebtPaymentEntity>)
    @Insert suspend fun insertAccountSnapshotsRaw(items: List<AccountSnapshotEntity>)
    @Insert suspend fun insertAiMessagesRaw(items: List<AiChatMessageEntity>)
    @Insert suspend fun insertPremiumPaymentsRaw(items: List<PremiumPaymentEntity>)
}
