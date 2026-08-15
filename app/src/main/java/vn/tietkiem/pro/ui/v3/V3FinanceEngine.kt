package vn.tietkiem.pro.ui.v3

import vn.tietkiem.pro.data.*
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object V3FinanceEngine {
    fun safeToSpend(
        dashboard: DashboardState,
        recurring: List<RecurringEntity>,
        now: Long = System.currentTimeMillis()
    ): Long {
        val end = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val upcomingFixed = recurring.filter {
            it.active && it.type == TransactionType.EXPENSE.name && it.nextDueAt in now until end
        }.sumOf { it.amount }
        val currentFlowRoom = (dashboard.incomeThisMonth - dashboard.expenseThisMonth - upcomingFixed).coerceAtLeast(0)
        if (dashboard.budgetLimit <= 0) return currentFlowRoom
        val budgetRoom = (dashboard.budgetLimit - dashboard.budgetSpent).coerceAtLeast(0)
        return min(currentFlowRoom, budgetRoom)
    }

    fun daysToNextIncome(recurring: List<RecurringEntity>, now: Long = System.currentTimeMillis()): Int? {
        val next = recurring.filter { it.active && it.type == TransactionType.INCOME.name && it.nextDueAt >= now }
            .minByOrNull { it.nextDueAt } ?: return null
        return max(0, TimeUnit.MILLISECONDS.toDays(next.nextDueAt - now).toInt())
    }

    fun healthScore(
        dashboard: DashboardState,
        debts: List<DebtEntity>,
        goals: List<GoalEntity>,
        now: Long = System.currentTimeMillis()
    ): Int {
        var score = 55
        score += when {
            dashboard.savingRate >= 0.30 -> 20
            dashboard.savingRate >= 0.20 -> 15
            dashboard.savingRate >= 0.10 -> 8
            dashboard.savingRate >= 0 -> 2
            else -> -18
        }
        if (dashboard.netWorth > 0) score += 8 else if (dashboard.netWorth < 0) score -= 12
        if (dashboard.budgetLimit > 0) {
            val ratio = dashboard.budgetSpent.toDouble() / dashboard.budgetLimit
            score += when {
                ratio <= 0.75 -> 8
                ratio <= 1.0 -> 2
                else -> -12
            }
        }
        val overdue = debts.count { it.type == DebtType.PAYABLE.name && it.remainingAmount > 0 && it.dueDate != null && it.dueDate < now }
        score -= overdue * 8
        if (goals.any { it.targetAmount > 0 && it.savedAmount >= it.targetAmount }) score += 4
        return score.coerceIn(0, 100)
    }

    fun insights(
        dashboard: DashboardState,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        debts: List<DebtEntity>,
        goals: List<GoalEntity>,
        recurring: List<RecurringEntity>,
        now: Long = System.currentTimeMillis()
    ): List<FinancialInsight> {
        val result = mutableListOf<FinancialInsight>()
        if (dashboard.budgetLimit > 0) {
            val ratio = dashboard.budgetSpent.toDouble() / dashboard.budgetLimit
            if (ratio >= 1) result += FinancialInsight("Ngân sách đã vượt", "Vượt ${v3Money(dashboard.budgetSpent - dashboard.budgetLimit)}", 2)
            else if (ratio >= .8) result += FinancialInsight("Ngân sách đang căng", "Đã dùng ${(ratio * 100).toInt()}% hạn mức", 1)
        }
        if (dashboard.expenseThisMonth > dashboard.incomeThisMonth && dashboard.incomeThisMonth > 0) {
            result += FinancialInsight("Chi đang vượt thu", "Dòng tiền tháng này âm ${v3Money(dashboard.expenseThisMonth - dashboard.incomeThisMonth)}", 2)
        }
        val overdue = debts.filter { it.type == DebtType.PAYABLE.name && it.remainingAmount > 0 && it.dueDate != null && it.dueDate < now }
        if (overdue.isNotEmpty()) result += FinancialInsight("Có nợ quá hạn", "${overdue.size} khoản cần xử lý", 2)

        val week = now + TimeUnit.DAYS.toMillis(7)
        val goalDue = goals.filter { it.savedAmount < it.targetAmount && it.deadline != null && it.deadline in now..week }
        if (goalDue.isNotEmpty()) result += FinancialInsight("Mục tiêu sắp đến hạn", goalDue.joinToString { it.name }, 1)

        val month = AppMonth.currentBounds(now)
        val prev = AppMonth.previousBounds(now)
        val catMap = categories.associateBy { it.id }
        val currentByCat = transactions.filter { it.type == TransactionType.EXPENSE.name && it.occurredAt in month.first until month.second }
            .groupBy { it.categoryId }.mapValues { it.value.sumOf(TransactionEntity::amount) }
        val prevByCat = transactions.filter { it.type == TransactionType.EXPENSE.name && it.occurredAt in prev.first until prev.second }
            .groupBy { it.categoryId }.mapValues { it.value.sumOf(TransactionEntity::amount) }
        currentByCat.entries.mapNotNull { (catId, current) ->
            val old = prevByCat[catId] ?: return@mapNotNull null
            if (old > 0 && current > old * 1.35) Triple(catId, current, old) else null
        }.sortedByDescending { it.second - it.third }.take(2).forEach { (catId, current, old) ->
            val name = catId?.let { catMap[it]?.name } ?: "Một danh mục"
            val pct = ((current - old).toDouble() / old * 100).toInt()
            result += FinancialInsight("$name tăng bất thường", "Cao hơn tháng trước khoảng $pct%", 1)
        }

        val dueSoon = recurring.filter { it.active && it.nextDueAt in now..(now + TimeUnit.DAYS.toMillis(3)) }
        if (dueSoon.isNotEmpty()) result += FinancialInsight("Sắp có khoản định kỳ", "${dueSoon.size} khoản trong 3 ngày tới", 0)

        if (result.isEmpty()) result += FinancialInsight("Dòng tiền đang ổn", "Chưa phát hiện cảnh báo đáng kể", 0)
        return result.take(6)
    }

    fun forecastSixMonths(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        recurring: List<RecurringEntity>,
        now: Long = System.currentTimeMillis()
    ): List<ForecastPoint> {
        val activeBalance = accounts.filterNot { it.archived }.sumOf { it.balance }
        val history = (-3..-1).map { offset ->
            val bounds = AppMonth.offsetBounds(now, offset)
            val rows = transactions.filter { it.occurredAt in bounds.first until bounds.second }
            val income = rows.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
            val expense = rows.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
            income to expense
        }
        val avgIncome = history.map { it.first }.filter { it > 0 }.averageOrZero().toLong()
        val avgExpense = history.map { it.second }.filter { it > 0 }.averageOrZero().toLong()
        val recurringIncome = recurring.filter { it.active && it.type == TransactionType.INCOME.name && it.interval == RecurringInterval.MONTHLY.name }.sumOf { it.amount }
        val recurringExpense = recurring.filter { it.active && it.type == TransactionType.EXPENSE.name && it.interval == RecurringInterval.MONTHLY.name }.sumOf { it.amount }
        val projectedIncome = max(avgIncome, recurringIncome)
        val projectedExpense = max(avgExpense, recurringExpense)

        var balance = activeBalance
        return (1..6).map { monthIndex ->
            balance += projectedIncome - projectedExpense
            val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, monthIndex) }
            ForecastPoint(
                label = "T${cal.get(Calendar.MONTH) + 1}",
                projectedBalance = balance,
                projectedIncome = projectedIncome,
                projectedExpense = projectedExpense
            )
        }
    }

    fun debtAvalanche(debts: List<DebtEntity>): List<DebtEntity> = debts
        .filter { it.type == DebtType.PAYABLE.name && it.remainingAmount > 0 }
        .sortedWith(compareByDescending<DebtEntity> { it.annualInterestRate }.thenBy { it.remainingAmount })

    fun debtSnowball(debts: List<DebtEntity>): List<DebtEntity> = debts
        .filter { it.type == DebtType.PAYABLE.name && it.remainingAmount > 0 }
        .sortedBy { it.remainingAmount }

    private fun List<Long>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}

private object AppMonth {
    fun currentBounds(now: Long): Pair<Long, Long> = offsetBounds(now, 0)
    fun previousBounds(now: Long): Pair<Long, Long> = offsetBounds(now, -1)
    fun offsetBounds(now: Long, offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}
