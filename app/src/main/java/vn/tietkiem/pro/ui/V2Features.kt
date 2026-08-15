@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package vn.tietkiem.pro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.tietkiem.pro.data.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private enum class V2Period(val label: String) {
    WEEK("7N"), MONTH("Tháng"), QUARTER("3T"), ALL("Tất cả")
}

private data class V2MonthFlow(
    val label: String,
    val income: Long,
    val expense: Long
) {
    val net: Long get() = income - expense
}

@Composable
fun V2TransactionsScreen(vm: AppViewModel) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var periodName by rememberSaveable { mutableStateOf(V2Period.MONTH.name) }
    var accountFilter by rememberSaveable { mutableLongStateOf(-1L) }
    var categoryFilter by rememberSaveable { mutableLongStateOf(-1L) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val period = V2Period.valueOf(periodName)
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val startAt = remember(period) { v2PeriodStart(period) }

    val shown = remember(transactions, typeFilter, period, accountFilter, categoryFilter, query, accounts, categories) {
        transactions.filter { tx ->
            val typeOk = typeFilter == "ALL" || tx.type == typeFilter
            val periodOk = startAt == null || tx.occurredAt >= startAt
            val accountOk = accountFilter < 0 || tx.accountId == accountFilter || tx.toAccountId == accountFilter
            val categoryOk = categoryFilter < 0 || tx.categoryId == categoryFilter
            val haystack = listOf(
                tx.note,
                accountMap[tx.accountId]?.name.orEmpty(),
                tx.toAccountId?.let { accountMap[it]?.name }.orEmpty(),
                tx.categoryId?.let { categoryMap[it]?.name }.orEmpty(),
                tx.amount.toString()
            ).joinToString(" ").lowercase(viLocaleV2)
            typeOk && periodOk && accountOk && categoryOk &&
                (query.isBlank() || haystack.contains(query.trim().lowercase(viLocaleV2)))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Giao dịch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("V2 • lọc sâu & tổng hợp tức thời", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AssistChip(onClick = {}, label = { Text("${shown.size} mục") }, leadingIcon = { Icon(Icons.Default.FilterAlt, null) })
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm ghi chú, ví, danh mục, số tiền") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Xóa tìm kiếm") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "ALL" to "Tất cả",
                        TransactionType.INCOME.name to "Thu",
                        TransactionType.EXPENSE.name to "Chi",
                        TransactionType.TRANSFER.name to "Chuyển"
                    ).forEach { (value, label) ->
                        FilterChip(selected = typeFilter == value, onClick = { typeFilter = value }, label = { Text(label) })
                    }
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    V2Period.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = period == item,
                            onClick = { periodName = item.name },
                            shape = SegmentedButtonDefaults.itemShape(index, V2Period.entries.size)
                        ) { Text(item.label) }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V2ChoiceLongField(
                        label = "Ví",
                        selected = accountFilter,
                        options = listOf(-1L to "Tất cả ví") + accounts.filterNot { it.archived }.map { it.id to it.name },
                        onSelected = { accountFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    V2ChoiceLongField(
                        label = "Danh mục",
                        selected = categoryFilter,
                        options = listOf(-1L to "Tất cả danh mục") + categories.map { it.id to it.name },
                        onSelected = { categoryFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                V2TransactionSummary(shown)
            }

            if (shown.isEmpty()) {
                V2EmptyState("Không có giao dịch khớp bộ lọc")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(shown, key = { it.id }) { tx ->
                        val sign = when (tx.type) {
                            TransactionType.INCOME.name -> "+"
                            TransactionType.EXPENSE.name -> "−"
                            else -> ""
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    tx.categoryId?.let { categoryMap[it]?.name }
                                        ?: if (tx.type == TransactionType.TRANSFER.name) "Chuyển tiền" else "Giao dịch",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(v2DateTime(tx.occurredAt))
                                        append(" • ")
                                        append(accountMap[tx.accountId]?.name ?: "Ví")
                                        if (tx.type == TransactionType.TRANSFER.name) {
                                            append(" → ")
                                            append(tx.toAccountId?.let { accountMap[it]?.name } ?: "Ví nhận")
                                        }
                                        if (tx.note.isNotBlank()) {
                                            append(" • ")
                                            append(tx.note)
                                        }
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    when (tx.type) {
                                        TransactionType.INCOME.name -> Icons.Default.ArrowDownward
                                        TransactionType.EXPENSE.name -> Icons.Default.ArrowUpward
                                        else -> Icons.Default.SwapHoriz
                                    },
                                    null
                                )
                            },
                            trailingContent = { Text("$sign${v2Money(tx.amount)}", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.clickable { editing = tx }
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(92.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Icon(Icons.Default.Add, "Thêm giao dịch") }
    }

    if (creating || editing != null) {
        V2TransactionDialog(
            item = editing,
            accounts = accounts.filterNot { it.archived },
            categories = categories,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveTransaction(it); creating = false; editing = null },
            onDelete = editing?.let { tx -> { vm.deleteTransaction(tx); editing = null } }
        )
    }
}

@Composable
private fun V2TransactionSummary(items: List<TransactionEntity>) {
    val income = items.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
    val expense = items.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            V2MiniMetric("Thu", income)
            V2MiniMetric("Chi", expense)
            V2MiniMetric("Dòng tiền", income - expense)
        }
    }
}

@Composable
private fun V2MiniMetric(label: String, value: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v2Money(value), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun V2InsightsScreen(vm: AppViewModel) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportTransactionsCsv)
    }

    val monthRows = remember(transactions) {
        (-5..0).map { offset ->
            val (start, end) = AppViewModel.monthBounds(offset)
            val rows = transactions.filter { it.occurredAt in start until end }
            V2MonthFlow(
                label = SimpleDateFormat("MM/yy", viLocaleV2).format(start),
                income = rows.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount },
                expense = rows.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
            )
        }
    }

    val now = Calendar.getInstance()
    val day = max(1, now.get(Calendar.DAY_OF_MONTH))
    val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    val projectedExpense = if (dashboard.expenseThisMonth > 0) dashboard.expenseThisMonth * daysInMonth / day else 0L
    val budgetRatio = if (dashboard.budgetLimit > 0) dashboard.budgetSpent.toDouble() / dashboard.budgetLimit else 0.0
    val overdueDebts = debts.filter { it.remainingAmount > 0 && it.dueDate != null && it.dueDate < System.currentTimeMillis() }
    val upcomingRecurring = recurring.count {
        it.active && it.nextDueAt in System.currentTimeMillis()..(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
    }
    val goalProgress = if (goals.isEmpty()) 0.0 else goals.map {
        if (it.targetAmount > 0) it.savedAmount.toDouble() / it.targetAmount else 0.0
    }.average()

    val healthScore = remember(dashboard, budgets, goals, debts) {
        var score = 20
        score += when {
            dashboard.savingRate >= 0.20 -> 30
            dashboard.savingRate >= 0.10 -> 22
            dashboard.savingRate >= 0.0 -> 12
            else -> 0
        }
        score += when {
            dashboard.budgetLimit <= 0 -> 10
            budgetRatio <= 0.80 -> 25
            budgetRatio <= 1.0 -> 15
            else -> 3
        }
        if (dashboard.netWorth > 0) score += 15
        score += when {
            goals.isEmpty() -> 5
            goalProgress >= 0.60 -> 10
            goalProgress > 0 -> 6
            else -> 0
        }
        score -= overdueDebts.size * 10
        score.coerceIn(0, 100)
    }

    val warnings = buildList {
        if (dashboard.budgetLimit > 0 && projectedExpense > dashboard.budgetLimit) {
            add("Theo tốc độ hiện tại, chi tháng có thể vượt ngân sách khoảng ${v2Money(projectedExpense - dashboard.budgetLimit)}.")
        }
        if (overdueDebts.isNotEmpty()) add("Có ${overdueDebts.size} khoản nợ đã quá hạn nhưng vẫn còn số dư.")
        if (dashboard.incomeThisMonth > 0 && dashboard.expenseThisMonth > dashboard.incomeThisMonth) {
            add("Chi tháng này đang cao hơn thu ${v2Money(dashboard.expenseThisMonth - dashboard.incomeThisMonth)}.")
        }
        val urgentGoals = goals.count { it.deadline != null && it.deadline > System.currentTimeMillis() && it.deadline < System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000 && it.savedAmount < it.targetAmount }
        if (urgentGoals > 0) add("Có $urgentGoals mục tiêu đến hạn trong 90 ngày tới.")
        if (upcomingRecurring > 0) add("Có $upcomingRecurring giao dịch định kỳ dự kiến đến hạn trong 30 ngày tới.")
    }

    val maxFlow = monthRows.maxOfOrNull { max(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
    val threeMonthNet = monthRows.takeLast(3).sumOf { it.net } / 3
    val scoreLabel = when {
        healthScore >= 80 -> "Tốt"
        healthScore >= 60 -> "Ổn"
        healthScore >= 40 -> "Cần chú ý"
        else -> "Rủi ro"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Phân tích V2", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Dự báo • sức khỏe tài chính • 6 tháng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { csvLauncher.launch("TietKiemPro-V2-${AppViewModel.monthKey(System.currentTimeMillis())}.csv") }) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(6.dp))
                    Text("CSV")
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Điểm sức khỏe tài chính", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$healthScore/100 • $scoreLabel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.HealthAndSafety, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { healthScore / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Điểm dựa trên tỷ lệ tiết kiệm, ngân sách, tài sản ròng, mục tiêu và nợ quá hạn.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2InsightMetric("Dự báo chi tháng", v2Money(projectedExpense), Modifier.weight(1f))
                V2InsightMetric("Dòng tiền TB 3T", v2Money(threeMonthNet), Modifier.weight(1f))
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Cảnh báo chủ động", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (warnings.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Chưa có cảnh báo lớn từ dữ liệu hiện tại.")
                        }
                    } else {
                        warnings.forEach { warning ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(warning, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Dòng tiền 6 tháng", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(monthRows, key = { it.label }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.label, fontWeight = FontWeight.Bold)
                        Text("Ròng ${v2Money(row.net)}", fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Thu ${v2Money(row.income)}", style = MaterialTheme.typography.bodySmall)
                        Text("Chi ${v2Money(row.expense)}", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = { row.income.toFloat() / maxFlow }, modifier = Modifier.fillMaxWidth())
                    LinearProgressIndicator(progress = { row.expense.toFloat() / maxFlow }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            val topCategory = categories
                .filter { it.type == CategoryType.EXPENSE.name }
                .map { category ->
                    category to transactions.filter {
                        it.type == TransactionType.EXPENSE.name && it.categoryId == category.id &&
                            AppViewModel.monthKey(it.occurredAt) == AppViewModel.monthKey(System.currentTimeMillis())
                    }.sumOf { it.amount }
                }
                .maxByOrNull { it.second }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Điểm nổi bật tháng", fontWeight = FontWeight.Bold)
                    Text(
                        if (topCategory != null && topCategory.second > 0) "Danh mục chi lớn nhất: ${topCategory.first.name} • ${v2Money(topCategory.second)}"
                        else "Chưa đủ dữ liệu để xác định danh mục chi lớn nhất."
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("Ngân sách đã dùng ${(budgetRatio * 100).coerceIn(0.0, 999.0).toInt()}% • ${goals.size} mục tiêu • ${debts.size} khoản nợ")
                }
            }
        }

        item {
            Text("Tiết Kiệm Pro V2 • dữ liệu V1 được giữ nguyên", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun V2InsightMetric(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun V2TransactionDialog(
    item: TransactionEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var type by remember(item) { mutableStateOf(item?.type ?: TransactionType.EXPENSE.name) }
    var amount by remember(item) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var accountId by remember(item, accounts) { mutableLongStateOf(item?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var toAccountId by remember(item, accounts) { mutableStateOf(item?.toAccountId ?: accounts.firstOrNull { it.id != accountId }?.id) }
    var categoryId by remember(item, categories, type) { mutableStateOf(item?.categoryId ?: categories.firstOrNull { it.type == type }?.id) }
    var note by remember(item) { mutableStateOf(item?.note.orEmpty()) }
    var date by remember(item) { mutableStateOf(v2DateOnly(item?.occurredAt ?: System.currentTimeMillis())) }

    LaunchedEffect(type) {
        if (type == TransactionType.TRANSFER.name) categoryId = null
        else if (categories.none { it.id == categoryId && it.type == type }) categoryId = categories.firstOrNull { it.type == type }?.id
    }
    LaunchedEffect(accountId) {
        if (toAccountId == accountId) toAccountId = accounts.firstOrNull { it.id != accountId }?.id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Thêm giao dịch" else "Sửa giao dịch") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TransactionType.EXPENSE.name to "Chi",
                        TransactionType.INCOME.name to "Thu",
                        TransactionType.TRANSFER.name to "Chuyển"
                    ).forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                    }
                }
                V2MoneyField(amount, { amount = it }, "Số tiền")
                V2ChoiceLongField("Ví", accountId, accounts.map { it.id to it.name }, { accountId = it })
                if (type == TransactionType.TRANSFER.name) {
                    V2ChoiceLongField("Ví nhận", toAccountId ?: -1L, accounts.filter { it.id != accountId }.map { it.id to it.name }, { toAccountId = it })
                } else {
                    V2ChoiceLongField("Danh mục", categoryId ?: -1L, categories.filter { it.type == type }.map { it.id to it.name }, { categoryId = it })
                }
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Ngày dd/MM/yyyy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            val canSave = v2ParseMoney(amount) > 0 && accountId > 0 &&
                (type != TransactionType.TRANSFER.name || (toAccountId != null && toAccountId != accountId))
            TextButton(
                enabled = canSave,
                onClick = {
                    val occurredAt = v2ParseDate(date) ?: System.currentTimeMillis()
                    onSave(
                        item?.copy(
                            type = type,
                            amount = v2ParseMoney(amount),
                            accountId = accountId,
                            toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                            categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                            note = note.trim(),
                            occurredAt = occurredAt
                        ) ?: TransactionEntity(
                            type = type,
                            amount = v2ParseMoney(amount),
                            accountId = accountId,
                            toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                            categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                            note = note.trim(),
                            occurredAt = occurredAt
                        )
                    )
                }
            ) { Text("Lưu") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Xóa") }
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        }
    )
}

@Composable
private fun V2MoneyField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(15)) },
        label = { Text(label) },
        suffix = { Text("₫") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun V2ChoiceLongField(
    label: String,
    selected: Long,
    options: List<Pair<Long, String>>,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Chọn"
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(value); expanded = false })
            }
        }
    }
}

@Composable
private fun V2EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun v2PeriodStart(period: V2Period): Long? = when (period) {
    V2Period.ALL -> null
    V2Period.WEEK -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    V2Period.MONTH -> Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    V2Period.QUARTER -> Calendar.getInstance().apply {
        add(Calendar.MONTH, -2)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private val viLocaleV2 = Locale("vi", "VN")
private fun v2Money(value: Long): String = NumberFormat.getNumberInstance(viLocaleV2).format(value) + " ₫"
private fun v2ParseMoney(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L
private fun v2DateOnly(time: Long): String = SimpleDateFormat("dd/MM/yyyy", viLocaleV2).format(time)
private fun v2DateTime(time: Long): String = SimpleDateFormat("dd/MM HH:mm", viLocaleV2).format(time)
private fun v2ParseDate(value: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", viLocaleV2).apply { isLenient = false }.parse(value)?.time
}.getOrNull()
