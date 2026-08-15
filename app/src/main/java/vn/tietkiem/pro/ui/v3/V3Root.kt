package vn.tietkiem.pro.ui.v3

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.AppViewModel
import vn.tietkiem.pro.ui.theme.TietKiemProTheme

private enum class V3Tab(val label: String) {
    HOME("Tổng quan"), TRANSACTIONS("Giao dịch"), PLAN("Kế hoạch"), AI("AI"), MORE("Thêm")
}

@Composable
fun V3AppRoot(vm: AppViewModel, onBiometricRequest: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val adminSetup by vm.adminSetupRequested.collectAsStateWithLifecycle()
    val current = settings ?: return

    TietKiemProTheme(current.theme) {
        V3GlassBackground {
            if (current.hasPin && !unlocked) {
                V3UnlockScreen(current.biometricEnabled, vm::verifyPin, onBiometricRequest)
            } else {
                if (!current.hasPin && !unlocked) LaunchedEffect(Unit) { vm.unlockWithoutPin() }
                V3MainShell(vm, current, onBiometricRequest)
            }

            if (message != null) V3Snackbar(message!!, vm::consumeMessage)
            if (adminSetup) V3AdminKeySetupDialog(vm)
        }
    }
}

@Composable
private fun V3MainShell(vm: AppViewModel, settings: AppSettings, onBiometricRequest: () -> Unit) {
    var tabName by rememberSaveable { mutableStateOf(V3Tab.HOME.name) }
    val tab = V3Tab.valueOf(tabName)

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            Surface(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    V3Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { tabName = item.name },
                            icon = {
                                Icon(
                                    when (item) {
                                        V3Tab.HOME -> Icons.Default.Home
                                        V3Tab.TRANSACTIONS -> Icons.Default.ReceiptLong
                                        V3Tab.PLAN -> Icons.Default.AutoGraph
                                        V3Tab.AI -> Icons.Default.AutoAwesome
                                        V3Tab.MORE -> Icons.Default.GridView
                                    },
                                    item.label
                                )
                            },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(tab, label = "v3-tab") { selected ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    V3Tab.HOME -> V3DashboardScreen(vm, settings, onOpenAi = { tabName = V3Tab.AI.name }, onOpenPremium = { tabName = V3Tab.MORE.name })
                    V3Tab.TRANSACTIONS -> V3TransactionsScreen(vm, settings)
                    V3Tab.PLAN -> V3PlanScreen(vm, settings)
                    V3Tab.AI -> V3AiScreen(vm, settings)
                    V3Tab.MORE -> V3MoreScreen(vm, settings, onBiometricRequest)
                }
            }
        }
    }
}

@Composable
private fun V3DashboardScreen(vm: AppViewModel, settings: AppSettings, onOpenAi: () -> Unit, onOpenPremium: () -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val hidden = settings.privacyMode

    val safe = remember(dashboard, recurring) { V3FinanceEngine.safeToSpend(dashboard, recurring) }
    val health = remember(dashboard, debts, goals) { V3FinanceEngine.healthScore(dashboard, debts, goals) }
    val daysIncome = remember(recurring) { V3FinanceEngine.daysToNextIncome(recurring) }
    val insights = remember(dashboard, txs, categories, debts, goals, recurring) {
        V3FinanceEngine.insights(dashboard, txs, categories, debts, goals, recurring)
    }
    val forecast = remember(accounts, txs, recurring) { V3FinanceEngine.forecastSixMonths(accounts, txs, recurring) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tiết Kiệm Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("V3 • ${v3Month(System.currentTimeMillis())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GlassPill(if (settings.premiumActive) settings.premiumTier else "FREE") {
                    Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp))
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(20.dp)) {
                Text("Tài sản ròng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hiddenMoney(dashboard.netWorth, hidden), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlassMetric("Thu", hiddenMoney(dashboard.incomeThisMonth, hidden), Modifier.weight(1f))
                    GlassMetric("Chi", hiddenMoney(dashboard.expenseThisMonth, hidden), Modifier.weight(1f))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassMetric(
                    "Có thể tiêu an toàn",
                    hiddenMoney(safe, hidden),
                    Modifier.weight(1.35f),
                    daysIncome?.let { "Còn $it ngày tới thu nhập định kỳ" }
                )
                GlassMetric("Sức khỏe", "$health/100", Modifier.weight(.65f), when { health >= 80 -> "Tốt"; health >= 60 -> "Ổn"; else -> "Cần chú ý" })
            }
        }

        item {
            V3SectionTitle("Dự báo 6 tháng", "Theo lịch sử + khoản định kỳ")
            Spacer(Modifier.height(8.dp))
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                forecast.forEachIndexed { index, point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(point.label, Modifier.width(42.dp), fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = {
                                val maxValue = forecast.maxOfOrNull { kotlin.math.abs(it.projectedBalance) }?.coerceAtLeast(1L) ?: 1L
                                (kotlin.math.abs(point.projectedBalance).toFloat() / maxValue).coerceIn(0.04f, 1f)
                            },
                            Modifier.weight(1f).height(8.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(hiddenMoney(point.projectedBalance, hidden), fontWeight = FontWeight.SemiBold)
                    }
                    if (index != forecast.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                }
            }
        }

        item { V3SectionTitle("Cần chú ý") }
        items(insights) { insight ->
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (insight.severity) { 2 -> Icons.Default.Warning; 1 -> Icons.Default.NotificationsActive; else -> Icons.Default.CheckCircle },
                        null,
                        tint = when (insight.severity) { 2 -> MaterialTheme.colorScheme.error; 1 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(insight.title, fontWeight = FontWeight.Bold)
                        Text(insight.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            GlassCard(
                Modifier.fillMaxWidth().clickable { if (settings.premiumActive && settings.premiumTier == PremiumTier.PRO.name) onOpenAi() else onOpenPremium() },
                PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI Financial Copilot", fontWeight = FontWeight.Bold)
                        Text(
                            if (settings.premiumActive && settings.premiumTier == PremiumTier.PRO.name) "Phân tích dữ liệu và hỏi đáp tài chính" else "Mở khóa trong gói Pro",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }

        item { V3SectionTitle("Ví & tài khoản") }
        items(accounts.filterNot { it.archived }.take(6), key = { it.id }) { account ->
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.Bold)
                        Text(account.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(hiddenMoney(account.balance, hidden), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V3UnlockScreen(biometric: Boolean, onUnlock: (String) -> Unit, onBiometric: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Lock, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Tiết Kiệm Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Mở khóa V3", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onUnlock(pin) }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Mở khóa") }
            if (biometric) TextButton(onClick = onBiometric, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Sinh trắc học")
            }
        }
    }
}

@Composable
private fun V3Snackbar(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.BottomCenter) {
        Snackbar(
            modifier = Modifier.padding(bottom = 88.dp),
            action = { TextButton(onClick = onDismiss) { Text("Đóng") } }
        ) { Text(message) }
    }
    LaunchedEffect(message) { delay(2800); onDismiss() }
}
