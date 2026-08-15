package vn.tietkiem.pro.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType { CASH, BANK, EWALLET, SAVINGS, INVESTMENT, OTHER }
enum class TransactionType { INCOME, EXPENSE, TRANSFER }
enum class CategoryType { INCOME, EXPENSE }
enum class DebtType { PAYABLE, RECEIVABLE }
enum class RecurringInterval { DAILY, WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val balance: Long,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val icon: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("occurredAt")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId"), Index(value = ["monthKey", "categoryId"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monthKey: String,
    val categoryId: Long,
    val limitAmount: Long
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val savedAmount: Long,
    val deadline: Long?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val originalAmount: Long,
    val remainingAmount: Long,
    val annualInterestRate: Double = 0.0,
    val dueDate: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("nextDueAt")]
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val interval: String,
    val nextDueAt: Long,
    val note: String = "",
    val active: Boolean = true
)

data class DashboardState(
    val netWorth: Long = 0,
    val totalAccounts: Long = 0,
    val incomeThisMonth: Long = 0,
    val expenseThisMonth: Long = 0,
    val savingThisMonth: Long = 0,
    val savingRate: Double = 0.0,
    val previousExpense: Long = 0,
    val budgetLimit: Long = 0,
    val budgetSpent: Long = 0
)

data class CategorySpend(
    val category: CategoryEntity,
    val amount: Long
)
