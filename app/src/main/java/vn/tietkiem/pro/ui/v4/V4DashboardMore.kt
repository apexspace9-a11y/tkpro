package vn.tietkiem.pro.ui.v4

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.v3.V3FinanceEngine
import kotlin.math.abs
import kotlin.math.max

@Composable
fun V4DashboardScreen(vm: V4ViewModel, settings: AppSettings, navigate: (V4Destination) -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val hidden = settings.privacyMode
    val safe = remember(dashboard, recurring) { V3FinanceEngine.safeToSpend(dashboard, recurring) }
    val health = remember(dashboard, debts, goals) { V3FinanceEngine.healthScore(dashboard, debts, goals) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            V4Card(Modifier.fillMaxWidth(), PaddingValues(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tài sản ròng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(v4HiddenMoney(dashboard.netWorth, hidden), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(v4Month(System.currentTimeMillis()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$health", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Sức khỏe", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 560.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        V4Metric("Thu tháng", v4HiddenMoney(dashboard.incomeThisMonth, hidden), Modifier.weight(1f))
                        V4Metric("Chi tháng", v4HiddenMoney(dashboard.expenseThisMonth, hidden), Modifier.weight(1f))
                        V4Metric("Có thể tiêu", v4HiddenMoney(safe, hidden), Modifier.weight(1f), emphasis = true)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        V4Metric("Có thể tiêu an toàn", v4HiddenMoney(safe, hidden), Modifier.fillMaxWidth(), emphasis = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            V4Metric("Thu tháng", v4HiddenMoney(dashboard.incomeThisMonth, hidden), Modifier.weight(1f))
                            V4Metric("Chi tháng", v4HiddenMoney(dashboard.expenseThisMonth, hidden), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            V4SectionHeader("Truy cập nhanh")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V4QuickAction(Icons.Default.AccountBalanceWallet, "Tài khoản", Modifier.weight(1f)) { navigate(V4Destination.ACCOUNTS) }
                V4QuickAction(Icons.Default.Insights, "Phân tích", Modifier.weight(1f)) { navigate(V4Destination.ANALYTICS) }
                V4QuickAction(Icons.Default.AutoAwesome, "AI", Modifier.weight(1f)) { navigate(V4Destination.AI) }
            }
        }

        item {
            V4SectionHeader("Ngân sách tháng", if (dashboard.budgetLimit > 0) "${v4Money(dashboard.budgetSpent)} / ${v4Money(dashboard.budgetLimit)}" else "Chưa đặt ngân sách")
            if (dashboard.budgetLimit > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (dashboard.budgetSpent.toFloat() / dashboard.budgetLimit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
            }
        }

        if (goals.isNotEmpty()) {
            item { V4SectionHeader("Mục tiêu", "${goals.size} mục tiêu đang theo dõi") }
            items(goals.take(3), key = { "home-goal-${it.id}" }) { goal ->
                val progress = if (goal.targetAmount > 0) goal.savedAmount.toFloat() / goal.targetAmount else 0f
                V4Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(goal.name, fontWeight = FontWeight.Bold)
                            Text("${v4HiddenMoney(goal.savedAmount, hidden)} / ${v4HiddenMoney(goal.targetAmount, hidden)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(progress * 100).toInt().coerceIn(0, 999)}%", fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                }
            }
        }

        item { V4SectionHeader("Giao dịch gần đây") }
        if (transactions.isEmpty()) item { Text("Chưa có giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(transactions.take(6), key = { "home-tx-${it.id}" }) { tx ->
            val label = tx.categoryId?.let { categoryMap[it]?.name }
                ?: if (tx.type == TransactionType.TRANSFER.name) "Chuyển tiền" else "Giao dịch"
            ListItem(
                headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(v4DateTime(tx.occurredAt)) },
                leadingContent = {
                    Icon(
                        when (tx.type) {
                            TransactionType.INCOME.name -> Icons.Default.SouthWest
                            TransactionType.EXPENSE.name -> Icons.Default.NorthEast
                            else -> Icons.Default.SwapHoriz
                        }, null
                    )
                },
                trailingContent = {
                    val sign = when (tx.type) { TransactionType.INCOME.name -> "+"; TransactionType.EXPENSE.name -> "−"; else -> "" }
                    Text("$sign${v4HiddenMoney(tx.amount, hidden)}", fontWeight = FontWeight.Bold)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun V4QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 18.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun V4MoreScreen(vm: V4ViewModel, settings: AppSettings, navigate: (V4Destination) -> Unit) {
    val online by vm.online.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(settings.cloudEmail, fontWeight = FontWeight.Bold)
                        Text("Đã kết nối cloud", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    V4StatusDot(true, if (online.syncing) "Đang đồng bộ" else "Online")
                }
            }
        }
        item { V4SectionHeader("Quản lý") }
        item { V4MenuRow(Icons.Default.AccountBalanceWallet, "Tài khoản & ví", "Số dư, loại tài khoản, lưu trữ", { navigate(V4Destination.ACCOUNTS) }) }
        item { V4MenuRow(Icons.Default.Insights, "Phân tích", "Dòng tiền, danh mục, xu hướng", { navigate(V4Destination.ANALYTICS) }) }
        item { V4MenuRow(Icons.Default.AutoAwesome, "Trợ lý AI", "Hỏi đáp và phân tích tài chính", { navigate(V4Destination.AI) }) }
        item { V4MenuRow(Icons.Default.WorkspacePremium, "Premium", if (settings.premiumActive) settings.premiumTier else "FREE", { navigate(V4Destination.PREMIUM) }) }
        item { V4SectionHeader("Hệ thống") }
        item { V4MenuRow(Icons.Default.Settings, "Cài đặt", "Bảo mật, thông báo, dữ liệu", { navigate(V4Destination.SETTINGS) }) }
        item { V4MenuRow(Icons.Default.AdminPanelSettings, "Admin CP", "Quản trị server, AI, Premium", { navigate(V4Destination.ADMIN) }) }
    }
}

@Composable
private fun V4MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun V4AccountsScreen(vm: V4ViewModel, settings: AppSettings) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val hidden = settings.privacyMode

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                V4SectionHeader("Tài khoản & ví", "${accounts.count { !it.archived }} đang hoạt động")
            }
            items(accounts, key = { it.id }) { account ->
                Surface(
                    Modifier.fillMaxWidth().clickable { editing = account },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = if (account.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.Bold)
                            Text(v4AccountType(account.type) + if (account.archived) " • Đã ẩn" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(v4HiddenMoney(account.balance, hidden), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Thêm tài khoản") }
        )
    }

    if (creating || editing != null) {
        V4AccountDialog(
            item = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveAccount(it); creating = false; editing = null },
            onArchive = editing?.let { a -> { vm.archiveAccount(a, !a.archived); editing = null } }
        )
    }
}

@Composable
private fun V4AccountDialog(item: AccountEntity?, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit, onArchive: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var balance by remember(item) { mutableStateOf(item?.balance?.toString().orEmpty()) }
    var type by remember(item) { mutableStateOf(item?.type ?: AccountType.CASH.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Thêm tài khoản" else "Sửa tài khoản") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(balance, { balance = it.filter(Char::isDigit).take(15) }, label = { Text("Số dư") }, suffix = { Text("₫") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                V4StringChoice("Loại tài khoản", type, AccountType.entries.map { it.name to v4AccountType(it.name) }) { type = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(item?.copy(name = name.trim(), balance = v4ParseMoney(balance), type = type) ?: AccountEntity(name = name.trim(), balance = v4ParseMoney(balance), type = type)) },
                enabled = name.isNotBlank()
            ) { Text("Lưu") }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text(if (item?.archived == true) "Khôi phục" else "Ẩn") }
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        }
    )
}

@Composable
fun V4AnalyticsScreen(vm: V4ViewModel, settings: AppSettings) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val categorySpend by vm.categorySpend.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val hidden = settings.privacyMode
    val forecast = remember(accounts, transactions, recurring) { V3FinanceEngine.forecastSixMonths(accounts, transactions, recurring) }
    val maxSpend = max(1L, categorySpend.maxOfOrNull { it.amount } ?: 1L)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 520.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        V4Metric("Tỷ lệ tiết kiệm", "${(dashboard.savingRate * 100).toInt()}%", Modifier.weight(1f))
                        V4Metric("Dòng tiền", v4HiddenMoney(dashboard.savingThisMonth, hidden), Modifier.weight(1f))
                        V4Metric("Tài sản ròng", v4HiddenMoney(dashboard.netWorth, hidden), Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        V4Metric("Dòng tiền tháng", v4HiddenMoney(dashboard.savingThisMonth, hidden), Modifier.fillMaxWidth(), emphasis = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            V4Metric("Tiết kiệm", "${(dashboard.savingRate * 100).toInt()}%", Modifier.weight(1f))
                            V4Metric("Tài sản", v4HiddenMoney(dashboard.netWorth, hidden), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { V4SectionHeader("Chi theo danh mục") }
        if (categorySpend.isEmpty()) item { Text("Chưa có dữ liệu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(categorySpend, key = { it.category.id }) { spend ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(spend.category.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(v4HiddenMoney(spend.amount, hidden))
                }
                LinearProgressIndicator({ spend.amount.toFloat() / maxSpend }, Modifier.fillMaxWidth().height(8.dp))
            }
        }
        item { V4SectionHeader("Dự báo 6 tháng") }
        items(forecast, key = { it.label }) { point ->
            V4Card(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(point.label, Modifier.width(52.dp), fontWeight = FontWeight.Bold)
                    Text(v4HiddenMoney(point.projectedBalance, hidden), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Icon(if (point.projectedBalance >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (point.projectedBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun <T> V4ChoiceField(label: String, selected: T?, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: "Chọn"
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, name) -> DropdownMenuItem({ Text(name) }, { onSelected(value); expanded = false }) }
        }
    }
}

@Composable
fun V4StringChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit) =
    V4ChoiceField(label, selected, options, onSelected)

private fun v4AccountType(type: String): String = when (type) {
    AccountType.CASH.name -> "Tiền mặt"
    AccountType.BANK.name -> "Ngân hàng"
    AccountType.EWALLET.name -> "Ví điện tử"
    AccountType.SAVINGS.name -> "Tiết kiệm"
    AccountType.INVESTMENT.name -> "Đầu tư"
    else -> "Khác"
}
