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

    @Query("SELECT * FROM accounts") suspend fun getAccounts(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun getCategories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun getTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets") suspend fun getBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM goals") suspend fun getGoals(): List<GoalEntity>
    @Query("SELECT * FROM debts") suspend fun getDebts(): List<DebtEntity>
    @Query("SELECT * FROM recurring") suspend fun getRecurring(): List<RecurringEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1") suspend fun getAccount(id: Long): AccountEntity?
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1") suspend fun getTransaction(id: Long): TransactionEntity?
    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDueAt <= :now ORDER BY nextDueAt") suspend fun getDueRecurring(now: Long): List<RecurringEntity>

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

    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Insert suspend fun insertAccountsRaw(items: List<AccountEntity>)
    @Insert suspend fun insertCategoriesRaw(items: List<CategoryEntity>)
    @Insert suspend fun insertTransactionsRaw(items: List<TransactionEntity>)
    @Insert suspend fun insertBudgetsRaw(items: List<BudgetEntity>)
    @Insert suspend fun insertGoalsRaw(items: List<GoalEntity>)
    @Insert suspend fun insertDebtsRaw(items: List<DebtEntity>)
    @Insert suspend fun insertRecurringRaw(items: List<RecurringEntity>)
}
