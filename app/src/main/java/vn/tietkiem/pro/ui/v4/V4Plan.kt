package vn.tietkiem.pro.ui.v4

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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.v3.V3FinanceEngine
import java.util.Calendar
import kotlin.math.max

@Composable
fun V4PlanScreen(vm: V4ViewModel, settings: AppSettings) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Ngân sách", "Mục tiêu", "Nợ", "Dự báo", "Định kỳ")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            labels.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> V4BudgetPane(vm, settings)
            1 -> V4GoalPane(vm, settings)
            2 -> V4DebtPane(vm, settings)
            3 -> V4ForecastPane(vm, settings)
            4 -> V4RecurringPane(vm, settings)
        }
    }
}

@Composable
private fun V4BudgetPane(vm: V4ViewModel, settings: AppSettings) {
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val configs by vm.budgetConfigs.collectAsStateWithLifecycle()
    val month = V4ViewModel.monthKey(System.currentTimeMillis())
    val active = budgets.filter { it.monthKey == month }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val configMap = remember(configs) { configs.associateBy { it.categoryId } }
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val spentTotal = remember(active, transactions) {
        active.sumOf { budget ->
            transactions.filter {
                it.type == TransactionType.EXPENSE.name &&
                    it.categoryId == budget.categoryId &&
                    V4ViewModel.monthKey(it.occurredAt) == month
            }.sumOf { it.amount }
        }
    }
    val availableTotal = active.sumOf { it.limitAmount + (configMap[it.categoryId]?.carryAmount ?: 0L) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                V4Card(Modifier.fillMaxWidth(), PaddingValues(20.dp)) {
                    Text("Còn có thể chi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v4HiddenMoney((availableTotal - spentTotal).coerceAtLeast(0L), settings.privacyMode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Đã dùng ${v4HiddenMoney(spentTotal, settings.privacyMode)} / ${v4HiddenMoney(availableTotal, settings.privacyMode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (availableTotal > 0) LinearProgressIndicator({ (spentTotal.toFloat() / availableTotal).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(9.dp))
                }
            }
            item { V4SectionHeader("Theo danh mục", "Rollover và phong bì được tính vào hạn mức") }
            if (active.isEmpty()) item { Text("Chưa có ngân sách tháng này", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(active, key = { it.id }) { budget ->
                val config = configMap[budget.categoryId]
                val spent = transactions.filter {
                    it.type == TransactionType.EXPENSE.name &&
                        it.categoryId == budget.categoryId &&
                        V4ViewModel.monthKey(it.occurredAt) == month
                }.sumOf { it.amount }
                val available = budget.limitAmount + (config?.carryAmount ?: 0L)
                val ratio = if (available > 0) spent.toFloat() / available else 0f
                Surface(
                    Modifier.fillMaxWidth().clickable { editing = budget },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(categoryMap[budget.categoryId]?.name ?: "Danh mục", fontWeight = FontWeight.Bold)
                                Text("${v4HiddenMoney(spent, settings.privacyMode)} / ${v4HiddenMoney(available, settings.privacyMode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (config?.rolloverEnabled == true) AssistChip(onClick = {}, label = { Text("Rollover") })
                        }
                        LinearProgressIndicator({ ratio.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp))
                        if (spent > available) Text("Vượt ${v4HiddenMoney(spent - available, settings.privacyMode)}", color = MaterialTheme.colorScheme.error)
                        if ((config?.envelopeTarget ?: 0L) > 0) Text("Mục tiêu phong bì ${v4HiddenMoney(config!!.envelopeTarget, settings.privacyMode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Ngân sách") }
        )
    }

    if (creating || editing != null) {
        V4BudgetDialog(
            item = editing,
            config = editing?.let { configMap[it.categoryId] },
            categories = categories.filter { it.type == CategoryType.EXPENSE.name },
            onDismiss = { creating = false; editing = null },
            onSave = { budget, config ->
                vm.saveBudget(budget)
                vm.saveBudgetConfig(config)
                creating = false
                editing = null
            },
            onDelete = editing?.let { row -> { vm.deleteBudget(row); editing = null } }
        )
    }
}

@Composable
private fun V4BudgetDialog(
    item: BudgetEntity?,
    config: BudgetConfigEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (BudgetEntity, BudgetConfigEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var categoryId by remember(item, categories) { mutableLongStateOf(item?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var amount by remember(item) { mutableStateOf(item?.limitAmount?.toString().orEmpty()) }
    var rollover by remember(config) { mutableStateOf(config?.rolloverEnabled ?: false) }
    var carry by remember(config) { mutableStateOf(config?.carryAmount?.toString().orEmpty()) }
    var envelope by remember(config) { mutableStateOf(config?.envelopeTarget?.toString().orEmpty()) }
    V4EditorDialog(
        title = if (item == null) "Thêm ngân sách" else "Sửa ngân sách",
        onDismiss = onDismiss,
        onDelete = onDelete,
        saveEnabled = categoryId > 0 && v4ParseMoney(amount) > 0,
        onSave = {
            val month = V4ViewModel.monthKey(System.currentTimeMillis())
            onSave(
                item?.copy(categoryId = categoryId, limitAmount = v4ParseMoney(amount), monthKey = month)
                    ?: BudgetEntity(monthKey = month, categoryId = categoryId, limitAmount = v4ParseMoney(amount)),
                BudgetConfigEntity(categoryId, rollover, v4ParseMoney(carry), v4ParseMoney(envelope))
            )
        }
    ) {
        V4ChoiceField("Danh mục", categoryId, categories.map { it.id to it.name }) { categoryId = it }
        V4MoneyField(amount, { amount = it }, "Hạn mức tháng")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rollover tiền dư", fontWeight = FontWeight.SemiBold)
                Text("Chuyển phần chưa dùng sang tháng sau", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(rollover, { rollover = it })
        }
        if (rollover) V4MoneyField(carry, { carry = it }, "Số dư chuyển sang")
        V4MoneyField(envelope, { envelope = it }, "Mục tiêu phong bì")
    }
}

@Composable
private fun V4GoalPane(vm: V4ViewModel, settings: AppSettings) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val links by vm.goalLinks.collectAsStateWithLifecycle()
    val linkMap = remember(links) { links.associateBy { it.goalId } }
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var contributing by remember { mutableStateOf<GoalEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { V4SectionHeader("Mục tiêu tiết kiệm", "Theo dõi tiến độ và đóng góp từng lần") }
            if (goals.isEmpty()) item { Text("Chưa có mục tiêu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(goals, key = { it.id }) { goal ->
                val progress = if (goal.targetAmount > 0) goal.savedAmount.toFloat() / goal.targetAmount else 0f
                val monthly = goal.deadline?.let { v4RequiredMonthly((goal.targetAmount - goal.savedAmount).coerceAtLeast(0L), it) }
                Surface(Modifier.fillMaxWidth().clickable { editing = goal }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(goal.deadline?.let { "Hạn ${v4Date(it)}" } ?: "Không đặt hạn", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${(progress * 100).toInt().coerceIn(0, 999)}%", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(v4HiddenMoney(goal.savedAmount, settings.privacyMode), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(v4HiddenMoney(goal.targetAmount, settings.privacyMode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (monthly != null && monthly > 0) Text("Nên thêm ${v4HiddenMoney(monthly, settings.privacyMode)}/tháng", color = MaterialTheme.colorScheme.primary)
                        linkMap[goal.id]?.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { Text("Liên kết: ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        TextButton(onClick = { contributing = goal }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Default.Savings, null); Spacer(Modifier.width(6.dp)); Text("Thêm đóng góp")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Mục tiêu") }
        )
    }

    if (creating || editing != null) {
        V4GoalDialog(
            item = editing,
            accounts = accounts.filterNot { it.archived },
            link = editing?.let { linkMap[it.id] },
            onDismiss = { creating = false; editing = null },
            onSave = { goal, accountId ->
                vm.saveGoal(goal)
                if (goal.id > 0) vm.saveGoalLink(GoalLinkEntity(goal.id, accountId))
                creating = false
                editing = null
            },
            onDelete = editing?.let { row -> { vm.deleteGoal(row); editing = null } }
        )
    }
    contributing?.let { goal ->
        V4ContributionDialog(goal, onDismiss = { contributing = null }) { amount, note ->
            vm.addGoalContribution(GoalContributionEntity(goalId = goal.id, amount = amount, note = note))
            contributing = null
        }
    }
}

@Composable
private fun V4GoalDialog(
    item: GoalEntity?,
    accounts: List<AccountEntity>,
    link: GoalLinkEntity?,
    onDismiss: () -> Unit,
    onSave: (GoalEntity, Long?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var target by remember(item) { mutableStateOf(item?.targetAmount?.toString().orEmpty()) }
    var saved by remember(item) { mutableStateOf(item?.savedAmount?.toString().orEmpty()) }
    var deadline by remember(item) { mutableStateOf(item?.deadline?.let(::v4Date).orEmpty()) }
    var accountId by remember(link) { mutableStateOf(link?.accountId) }
    V4EditorDialog(if (item == null) "Mục tiêu mới" else "Sửa mục tiêu", onDismiss, onDelete, name.isNotBlank() && v4ParseMoney(target) > 0, {
        onSave(
            item?.copy(name = name.trim(), targetAmount = v4ParseMoney(target), savedAmount = v4ParseMoney(saved), deadline = deadline.takeIf(String::isNotBlank)?.let(::v4ParseDate))
                ?: GoalEntity(name = name.trim(), targetAmount = v4ParseMoney(target), savedAmount = v4ParseMoney(saved), deadline = deadline.takeIf(String::isNotBlank)?.let(::v4ParseDate)),
            accountId
        )
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên mục tiêu") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V4MoneyField(target, { target = it }, "Số tiền mục tiêu")
        V4MoneyField(saved, { saved = it }, "Đã có")
        OutlinedTextField(deadline, { deadline = it.take(10) }, label = { Text("Hạn dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V4ChoiceField("Liên kết tài khoản", accountId, listOf(null to "Không liên kết") + accounts.map { it.id as Long? to it.name }) { accountId = it }
    }
}

@Composable
private fun V4ContributionDialog(goal: GoalEntity, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    V4EditorDialog("Đóng góp • ${goal.name}", onDismiss, null, v4ParseMoney(amount) > 0, { onSave(v4ParseMoney(amount), note.trim()) }) {
        V4MoneyField(amount, { amount = it }, "Số tiền")
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V4DebtPane(vm: V4ViewModel, settings: AppSettings) {
    val debts by vm.debts.collectAsStateWithLifecycle()
    var strategy by rememberSaveable { mutableStateOf("AVALANCHE") }
    var editing by remember { mutableStateOf<DebtEntity?>(null) }
    var paying by remember { mutableStateOf<DebtEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val ordered = remember(debts, strategy) {
        if (strategy == "AVALANCHE") V3FinanceEngine.debtAvalanche(debts) else V3FinanceEngine.debtSnowball(debts)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(strategy == "AVALANCHE", { strategy = "AVALANCHE" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Avalanche") }
                    SegmentedButton(strategy == "SNOWBALL", { strategy = "SNOWBALL" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Snowball") }
                }
            }
            item {
                val total = debts.filter { it.type == DebtType.PAYABLE.name }.sumOf { it.remainingAmount }
                V4Metric(
                    "Tổng cần trả",
                    v4HiddenMoney(total, settings.privacyMode),
                    Modifier.fillMaxWidth(),
                    if (strategy == "AVALANCHE") "Ưu tiên khoản có lãi suất cao" else "Ưu tiên khoản có số dư nhỏ",
                    emphasis = true
                )
            }
            if (ordered.isEmpty()) item { Text("Chưa có khoản nợ phải trả", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ordered, key = { it.id }) { debt ->
                val paid = (debt.originalAmount - debt.remainingAmount).coerceAtLeast(0L)
                val progress = if (debt.originalAmount > 0) paid.toFloat() / debt.originalAmount else 0f
                Surface(Modifier.fillMaxWidth().clickable { editing = debt }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(debt.name, fontWeight = FontWeight.Bold)
                                Text("${debt.annualInterestRate}%/năm${debt.dueDate?.let { " • hạn ${v4Date(it)}" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(v4HiddenMoney(debt.remainingAmount, settings.privacyMode), fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp))
                        TextButton(onClick = { paying = debt }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Default.Payments, null); Spacer(Modifier.width(6.dp)); Text("Ghi nhận thanh toán")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Khoản nợ") }
        )
    }

    if (creating || editing != null) {
        V4DebtDialog(
            item = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveDebt(it); creating = false; editing = null },
            onDelete = editing?.let { row -> { vm.deleteDebt(row); editing = null } }
        )
    }
    paying?.let { debt ->
        V4DebtPaymentDialog(debt, onDismiss = { paying = null }) { amount, interest, note ->
            vm.addDebtPayment(DebtPaymentEntity(debtId = debt.id, amount = amount, interestAmount = interest, note = note))
            paying = null
        }
    }
}

@Composable
private fun V4DebtDialog(item: DebtEntity?, onDismiss: () -> Unit, onSave: (DebtEntity) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var original by remember(item) { mutableStateOf(item?.originalAmount?.toString().orEmpty()) }
    var remaining by remember(item) { mutableStateOf(item?.remainingAmount?.toString().orEmpty()) }
    var interest by remember(item) { mutableStateOf(item?.annualInterestRate?.toString() ?: "0") }
    var due by remember(item) { mutableStateOf(item?.dueDate?.let(::v4Date).orEmpty()) }
    var note by remember(item) { mutableStateOf(item?.note.orEmpty()) }
    V4EditorDialog(if (item == null) "Khoản nợ mới" else "Sửa khoản nợ", onDismiss, onDelete, name.isNotBlank() && v4ParseMoney(original) > 0, {
        onSave(
            item?.copy(name = name.trim(), originalAmount = v4ParseMoney(original), remainingAmount = v4ParseMoney(remaining), annualInterestRate = interest.toDoubleOrNull() ?: 0.0, dueDate = due.takeIf(String::isNotBlank)?.let(::v4ParseDate), note = note.trim())
                ?: DebtEntity(name = name.trim(), type = DebtType.PAYABLE.name, originalAmount = v4ParseMoney(original), remainingAmount = v4ParseMoney(remaining).takeIf { it > 0 } ?: v4ParseMoney(original), annualInterestRate = interest.toDoubleOrNull() ?: 0.0, dueDate = due.takeIf(String::isNotBlank)?.let(::v4ParseDate), note = note.trim())
        )
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên khoản nợ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        V4MoneyField(original, { original = it; if (item == null && remaining.isBlank()) remaining = it }, "Ban đầu")
        V4MoneyField(remaining, { remaining = it }, "Còn lại")
        OutlinedTextField(interest, { interest = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Lãi suất %/năm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(due, { due = it.take(10) }, label = { Text("Hạn dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
private fun V4DebtPaymentDialog(debt: DebtEntity, onDismiss: () -> Unit, onSave: (Long, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    V4EditorDialog("Thanh toán • ${debt.name}", onDismiss, null, v4ParseMoney(amount) > 0 && v4ParseMoney(interest) <= v4ParseMoney(amount), {
        onSave(v4ParseMoney(amount), v4ParseMoney(interest), note.trim())
    }) {
        V4MoneyField(amount, { amount = it }, "Tổng thanh toán")
        V4MoneyField(interest, { interest = it }, "Phần tiền lãi")
        Text("Gốc giảm: ${v4Money((v4ParseMoney(amount) - v4ParseMoney(interest)).coerceAtLeast(0L))}")
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V4ForecastPane(vm: V4ViewModel, settings: AppSettings) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    var incomeDelta by rememberSaveable { mutableStateOf("0") }
    var oneTimeExpense by rememberSaveable { mutableStateOf("0") }
    val base = remember(accounts, transactions, recurring) { V3FinanceEngine.forecastSixMonths(accounts, transactions, recurring) }
    val delta = incomeDelta.toLongOrNull() ?: 0L
    val shock = v4ParseMoney(oneTimeExpense)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            V4Card(Modifier.fillMaxWidth()) {
                V4SectionHeader("What-if", "Mô phỏng thay đổi thu nhập và một khoản chi lớn")
                OutlinedTextField(incomeDelta, { incomeDelta = it.filter { c -> c.isDigit() || c == '-' }.take(12) }, label = { Text("Thay đổi thu nhập mỗi tháng") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                V4MoneyField(oneTimeExpense, { oneTimeExpense = it }, "Chi phí phát sinh một lần")
            }
        }
        item { V4SectionHeader("Kết quả 6 tháng") }
        items(base, key = { it.label }) { point ->
            val index = base.indexOf(point) + 1
            val scenario = point.projectedBalance + delta * index - shock
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(point.label, Modifier.width(52.dp), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(v4HiddenMoney(scenario, settings.privacyMode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Thu ${v4HiddenMoney(point.projectedIncome + delta, settings.privacyMode)} • Chi ${v4HiddenMoney(point.projectedExpense + if (index == 1) shock else 0L, settings.privacyMode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (scenario >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (scenario >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun V4RecurringPane(vm: V4ViewModel, settings: AppSettings) {
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                V4SectionHeader("Giao dịch định kỳ", "Lương, hóa đơn, subscription") {
                    TextButton(onClick = vm::postDueRecurring) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Cập nhật") }
                }
            }
            if (recurring.isEmpty()) item { Text("Chưa có giao dịch định kỳ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(recurring, key = { it.id }) { row ->
                Surface(Modifier.fillMaxWidth().clickable { editing = row }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontWeight = FontWeight.Bold)
                            Text("${v4RecurringLabel(row.interval)} • ${v4Date(row.nextDueAt)}${if (!row.active) " • Tạm dừng" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(v4HiddenMoney(row.amount, settings.privacyMode), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Định kỳ") }
        )
    }

    if (creating || editing != null) {
        V4RecurringDialog(
            item = editing,
            accounts = accounts.filterNot { it.archived },
            categories = categories,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveRecurring(it); creating = false; editing = null },
            onDelete = editing?.let { row -> { vm.deleteRecurring(row); editing = null } }
        )
    }
}

@Composable
private fun V4RecurringDialog(
    item: RecurringEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var type by remember(item) { mutableStateOf(item?.type ?: TransactionType.EXPENSE.name) }
    var amount by remember(item) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var accountId by remember(item, accounts) { mutableLongStateOf(item?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var toAccountId by remember(item, accounts) { mutableStateOf(item?.toAccountId ?: accounts.firstOrNull { it.id != accountId }?.id) }
    var categoryId by remember(item, categories) { mutableStateOf(item?.categoryId ?: categories.firstOrNull { it.type == type }?.id) }
    var interval by remember(item) { mutableStateOf(item?.interval ?: RecurringInterval.MONTHLY.name) }
    var due by remember(item) { mutableStateOf(v4Date(item?.nextDueAt ?: System.currentTimeMillis())) }
    var note by remember(item) { mutableStateOf(item?.note.orEmpty()) }
    var active by remember(item) { mutableStateOf(item?.active ?: true) }

    LaunchedEffect(type) {
        if (type == TransactionType.TRANSFER.name) categoryId = null
        else if (categories.none { it.id == categoryId && it.type == type }) categoryId = categories.firstOrNull { it.type == type }?.id
    }
    LaunchedEffect(accountId) { if (toAccountId == accountId) toAccountId = accounts.firstOrNull { it.id != accountId }?.id }

    V4EditorDialog(
        if (item == null) "Giao dịch định kỳ mới" else "Sửa giao dịch định kỳ",
        onDismiss,
        onDelete,
        name.isNotBlank() && v4ParseMoney(amount) > 0 && accountId > 0 && (type != TransactionType.TRANSFER.name || (toAccountId != null && toAccountId != accountId)),
        {
            val next = v4ParseDate(due) ?: System.currentTimeMillis()
            onSave(
                item?.copy(
                    name = name.trim(), type = type, amount = v4ParseMoney(amount), accountId = accountId,
                    toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                    categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                    interval = interval, nextDueAt = next, note = note.trim(), active = active
                ) ?: RecurringEntity(
                    name = name.trim(), type = type, amount = v4ParseMoney(amount), accountId = accountId,
                    toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                    categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                    interval = interval, nextDueAt = next, note = note.trim(), active = active
                )
            )
        }
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TransactionType.EXPENSE.name to "Chi", TransactionType.INCOME.name to "Thu", TransactionType.TRANSFER.name to "Chuyển").forEach { (value, label) ->
                FilterChip(type == value, { type = value }, label = { Text(label) })
            }
        }
        V4MoneyField(amount, { amount = it }, "Số tiền")
        V4ChoiceField("Tài khoản", accountId, accounts.map { it.id to it.name }) { accountId = it }
        if (type == TransactionType.TRANSFER.name) {
            V4ChoiceField("Tài khoản nhận", toAccountId, accounts.filter { it.id != accountId }.map { it.id as Long? to it.name }) { toAccountId = it }
        } else {
            V4ChoiceField("Danh mục", categoryId, categories.filter { it.type == type }.map { it.id as Long? to it.name }) { categoryId = it }
        }
        V4StringChoice("Chu kỳ", interval, RecurringInterval.entries.map { it.name to v4RecurringLabel(it.name) }) { interval = it }
        OutlinedTextField(due, { due = it.take(10) }, label = { Text("Ngày tiếp theo dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Đang hoạt động", Modifier.weight(1f)); Switch(active, { active = it }) }
    }
}

@Composable
fun V4MoneyField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        { onValue(it.filter(Char::isDigit).take(15)) },
        label = { Text(label) },
        suffix = { Text("₫") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun V4EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 680.dp).heightIn(max = 820.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Đóng") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Xóa") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = saveEnabled) { Text("Lưu") }
                }
            }
        }
    }
}

private fun v4RequiredMonthly(remaining: Long, deadline: Long): Long? {
    if (remaining <= 0 || deadline <= System.currentTimeMillis()) return null
    val now = Calendar.getInstance()
    val due = Calendar.getInstance().apply { timeInMillis = deadline }
    val months = max(1, (due.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12 + due.get(Calendar.MONTH) - now.get(Calendar.MONTH) + 1)
    return (remaining + months - 1) / months
}

private fun v4RecurringLabel(value: String): String = when (value) {
    RecurringInterval.DAILY.name -> "Hàng ngày"
    RecurringInterval.WEEKLY.name -> "Hàng tuần"
    RecurringInterval.MONTHLY.name -> "Hàng tháng"
    RecurringInterval.YEARLY.name -> "Hàng năm"
    else -> value
}
