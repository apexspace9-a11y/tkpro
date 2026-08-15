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
enum class PremiumTier { FREE, PLUS, PRO }
enum class PaymentStatus { PENDING, APPROVED, REJECTED }

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

@Entity(
    tableName = "payees",
    indices = [Index(value = ["name"], unique = true)]
)
data class PayeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultCategoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transaction_meta",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PayeeEntity::class, ["id"], ["payeeId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("payeeId")]
)
data class TransactionMetaEntity(
    @PrimaryKey val transactionId: Long,
    val payeeId: Long? = null,
    val attachmentUri: String = "",
    val merchantText: String = "",
    val isSubscription: Boolean = false
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class TransactionTagEntity(
    val transactionId: Long,
    val tagId: Long
)

@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("transactionId"), Index("categoryId")]
)
data class TransactionSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long? = null,
    val amount: Long,
    val note: String = ""
)

@Entity(
    tableName = "budget_configs",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)]
)
data class BudgetConfigEntity(
    @PrimaryKey val categoryId: Long,
    val rolloverEnabled: Boolean = false,
    val carryAmount: Long = 0,
    val envelopeTarget: Long = 0
)

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [ForeignKey(GoalEntity::class, ["id"], ["goalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goalId"), Index("occurredAt")]
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amount: Long,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(
    tableName = "goal_links",
    foreignKeys = [
        ForeignKey(GoalEntity::class, ["id"], ["goalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("accountId")]
)
data class GoalLinkEntity(
    @PrimaryKey val goalId: Long,
    val accountId: Long? = null
)

@Entity(
    tableName = "debt_payments",
    foreignKeys = [ForeignKey(DebtEntity::class, ["id"], ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId"), Index("occurredAt")]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val interestAmount: Long = 0,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(
    tableName = "account_snapshots",
    foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId"), Index("capturedAt")]
)
data class AccountSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val balance: Long,
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_chat_messages", indices = [Index("createdAt")])
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "premium_payments", indices = [Index("createdAt")])
data class PremiumPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plan: String,
    val amount: Long,
    val transferCode: String,
    val status: String = PaymentStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
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

data class ForecastPoint(
    val label: String,
    val projectedBalance: Long,
    val projectedIncome: Long,
    val projectedExpense: Long
)

data class FinancialInsight(
    val title: String,
    val detail: String,
    val severity: Int = 0
)
