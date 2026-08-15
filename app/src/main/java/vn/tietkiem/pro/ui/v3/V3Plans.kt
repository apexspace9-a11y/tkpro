package vn.tietkiem.pro.ui.v3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.AppViewModel
import java.util.Calendar
import kotlin.math.max

@Composable
fun V3PlanScreen(vm: AppViewModel, settings: AppSettings) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Ngân sách", "Mục tiêu", "Nợ", "Dự báo", "Định kỳ")
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
            V3SectionTitle("Kế hoạch tài chính", "Budget Engine 2.0 • Goal • Debt • Forecast")
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            labels.forEachIndexed { index, label -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) }) }
        }
        when (tab) {
            0 -> V3BudgetPane(vm, settings)
            1 -> V3GoalPane(vm, settings)
            2 -> V3DebtPane(vm, settings)
            3 -> V3ForecastPane(vm, settings)
            4 -> V3RecurringPane(vm)
        }
    }
}

@Composable
private fun V3BudgetPane(vm: AppViewModel, settings: AppSettings) {
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val configs by vm.budgetConfigs.collectAsStateWithLifecycle()
    val month = AppViewModel.monthKey(System.currentTimeMillis())
    val active = budgets.filter { it.monthKey == month }
    val categoryMap = categories.associateBy { it.id }
    val configMap = configs.associateBy { it.categoryId }
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val spent = active.sumOf { b -> txs.filter { it.type == TransactionType.EXPENSE.name && it.categoryId == b.categoryId && AppViewModel.monthKey(it.occurredAt) == month }.sumOf { it.amount } }
                val limit = active.sumOf { it.limitAmount + (configMap[it.categoryId]?.carryAmount ?: 0) }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Ngân sách khả dụng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(hiddenMoney((limit - spent).coerceAtLeast(0), settings.privacyMode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Đã dùng ${hiddenMoney(spent, settings.privacyMode)} / ${hiddenMoney(limit, settings.privacyMode)}")
                }
            }
            if (active.isEmpty()) item { Text("Chưa có ngân sách tháng này", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(active, key = { it.id }) { budget ->
                val config = configMap[budget.categoryId]
                val spent = txs.filter { it.type == TransactionType.EXPENSE.name && it.categoryId == budget.categoryId && AppViewModel.monthKey(it.occurredAt) == month }.sumOf { it.amount }
                val available = budget.limitAmount + (config?.carryAmount ?: 0)
                val ratio = if (available > 0) spent.toFloat() / available else 0f
                GlassCard(Modifier.fillMaxWidth().clickable { editing = budget }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(categoryMap[budget.categoryId]?.name ?: "Danh mục", fontWeight = FontWeight.Bold)
                            Text("${hiddenMoney(spent, settings.privacyMode)} / ${hiddenMoney(available, settings.privacyMode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (config?.rolloverEnabled == true) GlassPill("Rollover") { Icon(Icons.Default.Sync, null, Modifier.size(15.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator({ ratio.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    if (spent > available) Text("Vượt ${hiddenMoney(spent - available, settings.privacyMode)}", color = MaterialTheme.colorScheme.error)
                    if ((config?.envelopeTarget ?: 0) > 0) Text("Envelope ${hiddenMoney(config!!.envelopeTarget, settings.privacyMode)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        FloatingActionButton(onClick = { creating = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, null) }
    }

    if (creating || editing != null) {
        V3BudgetDialog(
            item = editing,
            config = editing?.let { configMap[it.categoryId] },
            categories = categories.filter { it.type == CategoryType.EXPENSE.name },
            onDismiss = { creating = false; editing = null },
            onSave = { budget, config -> vm.saveBudget(budget); vm.saveBudgetConfig(config); creating = false; editing = null },
            onDelete = editing?.let { b -> { vm.deleteBudget(b); editing = null } }
        )
    }
}

@Composable
private fun V3BudgetDialog(
    item: BudgetEntity?, config: BudgetConfigEntity?, categories: List<CategoryEntity>, onDismiss: () -> Unit,
    onSave: (BudgetEntity, BudgetConfigEntity) -> Unit, onDelete: (() -> Unit)?
) {
    var categoryId by remember(item, categories) { mutableLongStateOf(item?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var amount by remember(item) { mutableStateOf(item?.limitAmount?.toString().orEmpty()) }
    var rollover by remember(config) { mutableStateOf(config?.rolloverEnabled ?: false) }
    var carry by remember(config) { mutableStateOf(config?.carryAmount?.toString().orEmpty()) }
    var envelope by remember(config) { mutableStateOf(config?.envelopeTarget?.toString().orEmpty()) }
    V3SimpleDialog(if (item == null) "Thêm ngân sách" else "Budget Engine", onDismiss, onDelete, saveEnabled = categoryId > 0 && v3ParseMoney(amount) > 0, onSave = {
        val month = AppViewModel.monthKey(System.currentTimeMillis())
        onSave(
            item?.copy(categoryId = categoryId, limitAmount = v3ParseMoney(amount), monthKey = month)
                ?: BudgetEntity(monthKey = month, categoryId = categoryId, limitAmount = v3ParseMoney(amount)),
            BudgetConfigEntity(categoryId, rollover, v3ParseMoney(carry), v3ParseMoney(envelope))
        )
    }) {
        V3Dropdown("Danh mục", categoryId, categories.map { it.id to it.name }, { categoryId = it })
        V3MoneyField(amount, { amount = it }, "Hạn mức tháng")
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Rollover tiền dư", Modifier.weight(1f)); Switch(rollover, { rollover = it }) }
        if (rollover) V3MoneyField(carry, { carry = it }, "Số dư chuyển sang")
        V3MoneyField(envelope, { envelope = it }, "Mục tiêu phong bì")
    }
}

@Composable
private fun V3GoalPane(vm: AppViewModel, settings: AppSettings) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val links by vm.goalLinks.collectAsStateWithLifecycle()
    val linkMap = links.associateBy { it.goalId }
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var contributing by remember { mutableStateOf<GoalEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (goals.isEmpty()) item { Text("Chưa có mục tiêu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(goals, key = { it.id }) { goal ->
                val progress = if (goal.targetAmount > 0) goal.savedAmount.toFloat() / goal.targetAmount else 0f
                val monthly = goal.deadline?.let { v3RequiredMonthly((goal.targetAmount - goal.savedAmount).coerceAtLeast(0), it) }
                GlassCard(Modifier.fillMaxWidth().clickable { editing = goal }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(goal.name, fontWeight = FontWeight.Bold)
                            Text(goal.deadline?.let { "Hạn ${v3Date(it)}" } ?: "Không đặt hạn", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(progress * 100).toInt().coerceIn(0, 999)}%", fontWeight = FontWeight.Black)
                    }
                    LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("${hiddenMoney(goal.savedAmount, settings.privacyMode)} / ${hiddenMoney(goal.targetAmount, settings.privacyMode)}")
                    if (monthly != null && monthly > 0) Text("Nên thêm ${hiddenMoney(monthly, settings.privacyMode)}/tháng", color = MaterialTheme.colorScheme.primary)
                    linkMap[goal.id]?.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { Text("Liên kết: ${it.name}", style = MaterialTheme.typography.bodySmall) } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { contributing = goal }) { Icon(Icons.Default.Savings, null); Spacer(Modifier.width(5.dp)); Text("Đóng góp") }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { creating = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, null) }
    }

    if (creating || editing != null) V3GoalDialog(editing, accounts, linkMap[editing?.id], { creating = false; editing = null }, { goal, accountId -> vm.saveGoal(goal); if (goal.id > 0) vm.saveGoalLink(GoalLinkEntity(goal.id, accountId)); creating = false; editing = null }, editing?.let { g -> { vm.deleteGoal(g); editing = null } })
    contributing?.let { goal -> V3ContributionDialog(goal, { contributing = null }) { amount, note -> vm.addGoalContribution(GoalContributionEntity(goalId = goal.id, amount = amount, note = note)); contributing = null } }
}

@Composable
private fun V3GoalDialog(item: GoalEntity?, accounts: List<AccountEntity>, link: GoalLinkEntity?, onDismiss: () -> Unit, onSave: (GoalEntity, Long?) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var target by remember(item) { mutableStateOf(item?.targetAmount?.toString().orEmpty()) }
    var saved by remember(item) { mutableStateOf(item?.savedAmount?.toString().orEmpty()) }
    var deadline by remember(item) { mutableStateOf(item?.deadline?.let(::v3Date).orEmpty()) }
    var accountId by remember(link) { mutableStateOf(link?.accountId) }
    V3SimpleDialog(if (item == null) "Mục tiêu mới" else "Sửa mục tiêu", onDismiss, onDelete, name.isNotBlank() && v3ParseMoney(target) > 0, {
        onSave(item?.copy(name = name.trim(), targetAmount = v3ParseMoney(target), savedAmount = v3ParseMoney(saved), deadline = deadline.takeIf(String::isNotBlank)?.let(::v3ParseDate))
            ?: GoalEntity(name = name.trim(), targetAmount = v3ParseMoney(target), savedAmount = v3ParseMoney(saved), deadline = deadline.takeIf(String::isNotBlank)?.let(::v3ParseDate)), accountId)
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên mục tiêu") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V3MoneyField(target, { target = it }, "Số tiền mục tiêu")
        V3MoneyField(saved, { saved = it }, "Đã có")
        OutlinedTextField(deadline, { deadline = it.take(10) }, label = { Text("Hạn dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V3Dropdown("Liên kết ví", accountId, listOf(null to "Không liên kết") + accounts.filterNot { it.archived }.map { it.id as Long? to it.name }, { accountId = it })
    }
}

@Composable
private fun V3ContributionDialog(goal: GoalEntity, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    V3SimpleDialog("Đóng góp • ${goal.name}", onDismiss, null, v3ParseMoney(amount) > 0, { onSave(v3ParseMoney(amount), note.trim()) }) {
        V3MoneyField(amount, { amount = it }, "Số tiền")
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V3DebtPane(vm: AppViewModel, settings: AppSettings) {
    val debts by vm.debts.collectAsStateWithLifecycle()
    var strategy by rememberSaveable { mutableStateOf("AVALANCHE") }
    var editing by remember { mutableStateOf<DebtEntity?>(null) }
    var paying by remember { mutableStateOf<DebtEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val ordered = remember(debts, strategy) { if (strategy == "AVALANCHE") V3FinanceEngine.debtAvalanche(debts) else V3FinanceEngine.debtSnowball(debts) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(strategy == "AVALANCHE", { strategy = "AVALANCHE" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Avalanche") }
                    SegmentedButton(strategy == "SNOWBALL", { strategy = "SNOWBALL" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Snowball") }
                }
            }
            item {
                val total = debts.filter { it.type == DebtType.PAYABLE.name }.sumOf { it.remainingAmount }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Tổng cần trả", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(hiddenMoney(total, settings.privacyMode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(if (strategy == "AVALANCHE") "Ưu tiên lãi suất cao" else "Ưu tiên khoản nhỏ", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (ordered.isEmpty()) item { Text("Chưa có khoản nợ phải trả", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ordered, key = { it.id }) { debt ->
                val paid = (debt.originalAmount - debt.remainingAmount).coerceAtLeast(0)
                val p = if (debt.originalAmount > 0) paid.toFloat() / debt.originalAmount else 0f
                GlassCard(Modifier.fillMaxWidth().clickable { editing = debt }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(debt.name, fontWeight = FontWeight.Bold)
                            Text("${debt.annualInterestRate}%/năm${debt.dueDate?.let { " • hạn ${v3Date(it)}" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(hiddenMoney(debt.remainingAmount, settings.privacyMode), fontWeight = FontWeight.Black)
                    }
                    LinearProgressIndicator({ p.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { paying = debt }) { Icon(Icons.Default.Payments, null); Text("Thanh toán") } }
                }
            }
        }
        FloatingActionButton(onClick = { creating = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, null) }
    }

    if (creating || editing != null) V3DebtDialog(editing, { creating = false; editing = null }, { vm.saveDebt(it); creating = false; editing = null }, editing?.let { d -> { vm.deleteDebt(d); editing = null } })
    paying?.let { debt -> V3DebtPaymentDialog(debt, { paying = null }) { amount, interest, note -> vm.addDebtPayment(DebtPaymentEntity(debtId = debt.id, amount = amount, interestAmount = interest, note = note)); paying = null } }
}

@Composable
private fun V3DebtDialog(item: DebtEntity?, onDismiss: () -> Unit, onSave: (DebtEntity) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var original by remember(item) { mutableStateOf(item?.originalAmount?.toString().orEmpty()) }
    var remaining by remember(item) { mutableStateOf(item?.remainingAmount?.toString().orEmpty()) }
    var interest by remember(item) { mutableStateOf(item?.annualInterestRate?.toString() ?: "0") }
    var due by remember(item) { mutableStateOf(item?.dueDate?.let(::v3Date).orEmpty()) }
    var note by remember(item) { mutableStateOf(item?.note.orEmpty()) }
    V3SimpleDialog(if (item == null) "Khoản nợ mới" else "Sửa khoản nợ", onDismiss, onDelete, name.isNotBlank() && v3ParseMoney(original) > 0, {
        onSave(item?.copy(name = name.trim(), originalAmount = v3ParseMoney(original), remainingAmount = v3ParseMoney(remaining), annualInterestRate = interest.toDoubleOrNull() ?: 0.0, dueDate = due.takeIf(String::isNotBlank)?.let(::v3ParseDate), note = note.trim())
            ?: DebtEntity(name = name.trim(), type = DebtType.PAYABLE.name, originalAmount = v3ParseMoney(original), remainingAmount = v3ParseMoney(remaining).takeIf { it > 0 } ?: v3ParseMoney(original), annualInterestRate = interest.toDoubleOrNull() ?: 0.0, dueDate = due.takeIf(String::isNotBlank)?.let(::v3ParseDate), note = note.trim()))
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên khoản nợ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V3MoneyField(original, { original = it; if (item == null && remaining.isBlank()) remaining = it }, "Ban đầu")
        V3MoneyField(remaining, { remaining = it }, "Còn lại")
        OutlinedTextField(interest, { interest = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Lãi suất %/năm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(due, { due = it.take(10) }, label = { Text("Hạn dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V3DebtPaymentDialog(debt: DebtEntity, onDismiss: () -> Unit, onSave: (Long, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    V3SimpleDialog("Thanh toán • ${debt.name}", onDismiss, null, v3ParseMoney(amount) > 0 && v3ParseMoney(interest) <= v3ParseMoney(amount), { onSave(v3ParseMoney(amount), v3ParseMoney(interest), note.trim()) }) {
        V3MoneyField(amount, { amount = it }, "Tổng thanh toán")
        V3MoneyField(interest, { interest = it }, "Phần tiền lãi")
        Text("Gốc giảm: ${v3Money((v3ParseMoney(amount) - v3ParseMoney(interest)).coerceAtLeast(0))}")
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V3ForecastPane(vm: AppViewModel, settings: AppSettings) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    var incomeDelta by rememberSaveable { mutableStateOf("0") }
    var oneTimeExpense by rememberSaveable { mutableStateOf("0") }
    val base = remember(accounts, txs, recurring) { V3FinanceEngine.forecastSixMonths(accounts, txs, recurring) }
    val delta = incomeDelta.toLongOrNull() ?: 0L
    val shock = v3ParseMoney(oneTimeExpense)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("What-if Lab", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("Mô phỏng thay đổi thu nhập và chi phí lớn", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(incomeDelta, { incomeDelta = it.filter { c -> c.isDigit() || c == '-' }.take(12) }, label = { Text("Thay đổi thu nhập/tháng") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                V3MoneyField(oneTimeExpense, { oneTimeExpense = it }, "Chi phí phát sinh một lần")
            }
        }
        items(base) { point ->
            val index = base.indexOf(point) + 1
            val scenario = point.projectedBalance + delta * index - shock
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(point.label, fontWeight = FontWeight.Black, modifier = Modifier.width(45.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hiddenMoney(scenario, settings.privacyMode), fontWeight = FontWeight.Bold)
                        Text("Thu ${hiddenMoney(point.projectedIncome + delta, settings.privacyMode)} • Chi ${hiddenMoney(point.projectedExpense + if (index == 1) shock else 0, settings.privacyMode)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (scenario >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (scenario >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun V3RecurringPane(vm: AppViewModel) {
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = vm::postDueRecurring) { Icon(Icons.Default.Refresh, null); Text("Cập nhật đến hạn") } } }
            items(recurring, key = { it.id }) { item ->
                GlassCard(Modifier.fillMaxWidth().clickable { editing = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Repeat, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold); Text("${item.interval} • ${v3Date(item.nextDueAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(v3Money(item.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { creating = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, null) }
    }
    if (creating || editing != null) V3RecurringDialog(editing, accounts.filterNot { it.archived }, categories, { creating = false; editing = null }, { vm.saveRecurring(it); creating = false; editing = null }, editing?.let { r -> { vm.deleteRecurring(r); editing = null } })
}

@Composable
private fun V3RecurringDialog(item: RecurringEntity?, accounts: List<AccountEntity>, categories: List<CategoryEntity>, onDismiss: () -> Unit, onSave: (RecurringEntity) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var type by remember(item) { mutableStateOf(item?.type ?: TransactionType.EXPENSE.name) }
    var amount by remember(item) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var accountId by remember(item, accounts) { mutableLongStateOf(item?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var categoryId by remember(item, categories) { mutableStateOf(item?.categoryId ?: categories.firstOrNull { it.type == type }?.id) }
    var interval by remember(item) { mutableStateOf(item?.interval ?: RecurringInterval.MONTHLY.name) }
    var due by remember(item) { mutableStateOf(v3Date(item?.nextDueAt ?: System.currentTimeMillis())) }
    V3SimpleDialog(if (item == null) "Định kỳ mới" else "Sửa định kỳ", onDismiss, onDelete, name.isNotBlank() && v3ParseMoney(amount) > 0 && accountId > 0, {
        onSave(item?.copy(name = name.trim(), type = type, amount = v3ParseMoney(amount), accountId = accountId, categoryId = categoryId, interval = interval, nextDueAt = v3ParseDate(due) ?: System.currentTimeMillis())
            ?: RecurringEntity(name = name.trim(), type = type, amount = v3ParseMoney(amount), accountId = accountId, categoryId = categoryId, interval = interval, nextDueAt = v3ParseDate(due) ?: System.currentTimeMillis()))
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(type == TransactionType.EXPENSE.name, { type = TransactionType.EXPENSE.name }, label = { Text("Chi") }); FilterChip(type == TransactionType.INCOME.name, { type = TransactionType.INCOME.name }, label = { Text("Thu") }) }
        V3MoneyField(amount, { amount = it }, "Số tiền")
        V3Dropdown("Ví", accountId, accounts.map { it.id to it.name }, { accountId = it })
        V3Dropdown("Danh mục", categoryId, categories.filter { it.type == type }.map { it.id as Long? to it.name }, { categoryId = it })
        V3Dropdown("Chu kỳ", interval, RecurringInterval.entries.map { it.name to it.name }, { interval = it })
        OutlinedTextField(due, { due = it.take(10) }, label = { Text("Ngày tiếp theo") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun V3MoneyField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(value, { onValue(it.filter(Char::isDigit).take(15)) }, label = { Text(label) }, suffix = { Text("₫") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
fun V3SimpleDialog(title: String, onDismiss: () -> Unit, onDelete: (() -> Unit)?, saveEnabled: Boolean, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 680.dp), PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Xóa") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Button(onClick = onSave, enabled = saveEnabled) { Text("Lưu") }
            }
        }
    }
}

private fun v3RequiredMonthly(remaining: Long, deadline: Long): Long? {
    if (remaining <= 0 || deadline <= System.currentTimeMillis()) return null
    val now = Calendar.getInstance()
    val due = Calendar.getInstance().apply { timeInMillis = deadline }
    val months = max(1, (due.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12 + due.get(Calendar.MONTH) - now.get(Calendar.MONTH) + 1)
    return (remaining + months - 1) / months
}
