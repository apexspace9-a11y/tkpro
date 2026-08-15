package vn.tietkiem.pro.ui.v3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.AppViewModel

@Composable
fun V3AiScreen(vm: AppViewModel, settings: AppSettings) {
    val messages by vm.aiMessages.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val adminUnlocked by vm.adminUnlocked.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val localInsights = remember(dashboard, txs, categories, debts, goals, recurring) {
        V3FinanceEngine.insights(dashboard, txs, categories, debts, goals, recurring)
    }
    var input by remember { mutableStateOf("") }
    var showAdmin by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(adminUnlocked) { if (adminUnlocked) showAdmin = true }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            V3SectionTitle("AI Financial Copilot", "Chat có ngữ cảnh tài chính theo quyền bạn cấp") {
                GlassPill(if (settings.premiumActive && settings.premiumTier == PremiumTier.PRO.name) "PRO" else "LOCAL") {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                }
            }
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Text("Insight offline", fontWeight = FontWeight.Bold)
                localInsights.take(3).forEach { Text("• ${it.title}: ${it.detail}", style = MaterialTheme.typography.bodySmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Phân tích tháng này", "Tối ưu ngân sách", "Lập kế hoạch trả nợ", "Tôi có thể tiết kiệm thêm bao nhiêu?").forEach { prompt ->
                    AssistChip(onClick = { input = prompt }, label = { Text(prompt) })
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Bắt đầu bằng một câu hỏi về dòng tiền, ngân sách, mục tiêu hoặc nợ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!(settings.premiumActive && settings.premiumTier == PremiumTier.PRO.name)) {
                        Spacer(Modifier.height(8.dp))
                        Text("AI online cần Pro. Lệnh quản trị vẫn dùng được trong ô chat.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.widthIn(max = 330.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (msg.role == "user") MaterialTheme.colorScheme.primaryContainer.copy(alpha = .80f) else MaterialTheme.colorScheme.surface.copy(alpha = .76f)
                    ) {
                        Text(msg.content, Modifier.padding(14.dp))
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    input,
                    { input = it },
                    placeholder = { Text("Hỏi AI…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { val text = input.trim(); if (text.isNotBlank()) { vm.sendAiMessage(text); input = "" } }, enabled = !busy && input.isNotBlank()) {
                    Icon(Icons.Default.Send, null)
                }
            }
        }
    }

    if (showAdmin && adminUnlocked) V3AdminPanel(vm, settings, onDismiss = { showAdmin = false; vm.closeAdmin() })
}

@Composable
fun V3AdminKeySetupDialog(vm: AppViewModel) {
    var key by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    V3SimpleDialog("Tạo khóa Admin CP", vm::dismissAdminSetup, null, key.length >= 6 && key == confirm, { vm.setupAdminKey(key, confirm) }) {
        OutlinedTextField(key, { key = it.take(64) }, label = { Text("Khóa quản trị") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(confirm, { confirm = it.take(64) }, label = { Text("Nhập lại khóa") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
private fun V3AdminPanel(vm: AppViewModel, settings: AppSettings, onDismiss: () -> Unit) {
    val payments by vm.premiumPayments.collectAsStateWithLifecycle()
    var endpoint by remember(settings) { mutableStateOf(settings.aiEndpoint) }
    var model by remember(settings) { mutableStateOf(settings.aiModel) }
    var apiKey by remember { mutableStateOf("") }
    var systemPrompt by remember(settings) { mutableStateOf(settings.aiSystemPrompt) }
    var includeFinance by remember(settings) { mutableStateOf(settings.aiFinancialContext) }
    var bank by remember(settings) { mutableStateOf(settings.bankName) }
    var account by remember(settings) { mutableStateOf(settings.bankAccount) }
    var owner by remember(settings) { mutableStateOf(settings.bankOwner) }
    var plusPrice by remember(settings) { mutableStateOf(settings.plusPrice.toString()) }
    var proPrice by remember(settings) { mutableStateOf(settings.proPrice.toString()) }
    var months by remember { mutableStateOf("1") }
    var tier by remember { mutableStateOf(PremiumTier.PRO.name) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 760.dp), PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Admin CP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("V3 control center", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { V3SectionTitle("AI") }
                item { OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API endpoint") }, placeholder = { Text("https://…/v1/chat/completions") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key mới") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(systemPrompt, { systemPrompt = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Cho AI dùng ngữ cảnh tài chính", Modifier.weight(1f)); Switch(includeFinance, { includeFinance = it }) } }
                item { Button(onClick = { vm.saveAiConfig(endpoint, model, apiKey, systemPrompt, includeFinance) }, modifier = Modifier.fillMaxWidth()) { Text("Lưu AI") } }

                item { Spacer(Modifier.height(4.dp)); V3SectionTitle("Thanh toán bank") }
                item { OutlinedTextField(bank, { bank = it }, label = { Text("Ngân hàng") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(account, { account = it.filter(Char::isDigit) }, label = { Text("Số tài khoản") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(owner, { owner = it }, label = { Text("Chủ tài khoản") }, modifier = Modifier.fillMaxWidth()) }
                item { V3MoneyField(plusPrice, { plusPrice = it }, "Giá Plus / tháng") }
                item { V3MoneyField(proPrice, { proPrice = it }, "Giá Pro / tháng") }
                item { Button(onClick = { vm.saveBankConfig(bank, account, owner, v3ParseMoney(plusPrice), v3ParseMoney(proPrice)) }, modifier = Modifier.fillMaxWidth()) { Text("Lưu thanh toán") } }

                item { Spacer(Modifier.height(4.dp)); V3SectionTitle("Premium override") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PremiumTier.PLUS, PremiumTier.PRO).forEach { p -> FilterChip(tier == p.name, { tier = p.name }, label = { Text(p.name) }) }
                    }
                }
                item { OutlinedTextField(months, { months = it.filter(Char::isDigit).take(3) }, label = { Text("Số tháng") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.activatePremium(PremiumTier.valueOf(tier), months.toIntOrNull() ?: 1) }, Modifier.weight(1f)) { Text("Kích hoạt") }
                        OutlinedButton(onClick = vm::deactivatePremium, Modifier.weight(1f)) { Text("Tắt Premium") }
                    }
                }

                item { V3SectionTitle("Yêu cầu chuyển khoản") }
                if (payments.isEmpty()) item { Text("Chưa có yêu cầu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(payments.take(12), key = { it.id }) { payment ->
                    GlassCard(Modifier.fillMaxWidth(), PaddingValues(12.dp)) {
                        Text("${payment.plan} • ${v3Money(payment.amount)}", fontWeight = FontWeight.Bold)
                        Text("${payment.transferCode} • ${payment.status}", style = MaterialTheme.typography.bodySmall)
                        if (payment.status == PaymentStatus.PENDING.name) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { vm.rejectPremiumPayment(payment) }) { Text("Từ chối") }
                                TextButton(onClick = { vm.approvePremiumPayment(payment, months.toIntOrNull() ?: 1) }) { Text("Duyệt") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun V3MoreScreen(vm: AppViewModel, settings: AppSettings, onBiometricRequest: () -> Unit) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val payees by vm.payees.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val payments by vm.premiumPayments.collectAsStateWithLifecycle()
    var showPremium by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var editPayee by remember { mutableStateOf<PayeeEntity?>(null) }
    var newPayee by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<TagEntity?>(null) }
    var newTag by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setNotifications(granted) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportTransactionsCsv) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V3SectionTitle("Trung tâm", "Cài đặt • dữ liệu • Premium") }
        item {
            GlassCard(Modifier.fillMaxWidth().clickable { showPremium = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Premium", fontWeight = FontWeight.Bold)
                        Text(if (settings.premiumActive) "${settings.premiumTier} • đến ${v3Date(settings.premiumExpiry)}" else "FREE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }

        item { V3SectionTitle("Giao diện & riêng tư") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("SYSTEM" to "Hệ thống", "LIGHT" to "Sáng", "DARK" to "Tối").forEachIndexed { i, pair ->
                    SegmentedButton(settings.theme == pair.first, { vm.setTheme(pair.first) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second) }
                }
            }
        }
        item { GlassCard(Modifier.fillMaxWidth(), PaddingValues(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.VisibilityOff, null); Spacer(Modifier.width(10.dp)); Text("Ẩn số tiền", Modifier.weight(1f)); Switch(settings.privacyMode, vm::setPrivacyMode) } } }
        item {
            GlassCard(Modifier.fillMaxWidth(), PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(10.dp)); Text("Thông báo tài chính", Modifier.weight(1f))
                    Switch(settings.notificationsEnabled, { enabled ->
                        if (!enabled) vm.setNotifications(false)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.setNotifications(true)
                    })
                }
            }
        }

        item { V3SectionTitle("Bảo mật") }
        item { GlassCard(Modifier.fillMaxWidth().clickable { showPin = true }, PaddingValues(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(10.dp)); Text(if (settings.hasPin) "Đổi PIN" else "Bật PIN", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } } }
        if (settings.hasPin) item { GlassCard(Modifier.fillMaxWidth(), PaddingValues(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(10.dp)); Text("Sinh trắc học", Modifier.weight(1f)); Switch(settings.biometricEnabled, { vm.setBiometric(it); if (it) onBiometricRequest() }) } } }

        item { V3SectionTitle("Dữ liệu") }
        item { V3ActionRow(Icons.Default.Backup, "Xuất backup V3", "JSON đầy đủ") { exportBackup.launch("TietKiemPro-V3-${AppViewModel.monthKey(System.currentTimeMillis())}.json") } }
        item { V3ActionRow(Icons.Default.Restore, "Khôi phục", "Hỗ trợ backup schema 1–2") { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) } }
        item { V3ActionRow(Icons.Default.TableView, "Xuất CSV", "Giao dịch + payee + tags") { exportCsv.launch("TietKiemPro-V3-transactions.csv") } }

        item { V3SectionTitle("Payee & tags") }
        item { Row(Modifier.fillMaxWidth()) { Text("Người nhận / cửa hàng", Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = { newPayee = true }) { Icon(Icons.Default.Add, null) } } }
        items(payees.take(12), key = { "p-${it.id}" }) { p -> ListItem(headlineContent = { Text(p.name) }, leadingContent = { Icon(Icons.Default.Store, null) }, modifier = Modifier.clickable { editPayee = p }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) }
        item { Row(Modifier.fillMaxWidth()) { Text("Tags", Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = { newTag = true }) { Icon(Icons.Default.Add, null) } } }
        items(tags.take(12), key = { "t-${it.id}" }) { t -> ListItem(headlineContent = { Text("#${t.name}") }, leadingContent = { Icon(Icons.Default.Tag, null) }, modifier = Modifier.clickable { editTag = t }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) }

        item { V3SectionTitle("Ứng dụng") }
        item { GlassCard(Modifier.fillMaxWidth(), PaddingValues(12.dp)) { Text("Tiết Kiệm Pro V3", fontWeight = FontWeight.Bold); Text("3.0.0 • Financial OS", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }

    if (showPremium) V3PremiumDialog(vm, settings, payments, { showPremium = false })
    if (showPin) V3PinDialog(settings.hasPin, { showPin = false }, { pin, confirm -> vm.setPin(pin, confirm); showPin = false }, if (settings.hasPin) vm::clearPin else null)
    if (newPayee || editPayee != null) V3NameEntityDialog("Payee", editPayee?.name.orEmpty(), { newPayee = false; editPayee = null }, { name -> vm.savePayee(editPayee?.copy(name = name) ?: PayeeEntity(name = name)); newPayee = false; editPayee = null }, editPayee?.let { p -> { vm.deletePayee(p); editPayee = null } })
    if (newTag || editTag != null) V3NameEntityDialog("Tag", editTag?.name.orEmpty(), { newTag = false; editTag = null }, { name -> vm.saveTag(editTag?.copy(name = name) ?: TagEntity(name = name)); newTag = false; editTag = null }, editTag?.let { t -> { vm.deleteTag(t); editTag = null } })
}

@Composable
private fun V3PremiumDialog(vm: AppViewModel, settings: AppSettings, payments: List<PremiumPaymentEntity>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 720.dp), PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Tiết Kiệm Pro Premium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Nâng cấp qua chuyển khoản", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    V3PlanCard("PLUS", settings.plusPrice, listOf("Budget rollover & envelope", "Forecast & What-if", "OCR hóa đơn", "Planner nâng cao")) { vm.createPremiumPayment(PremiumTier.PLUS) }
                }
                item {
                    V3PlanCard("PRO", settings.proPrice, listOf("Toàn bộ Plus", "AI Financial Copilot", "AI dùng ngữ cảnh tài chính", "Ưu tiên tính năng mới")) { vm.createPremiumPayment(PremiumTier.PRO) }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                        Text("Chuyển khoản", fontWeight = FontWeight.Bold)
                        if (settings.bankName.isBlank() || settings.bankAccount.isBlank()) Text("Chưa cấu hình tài khoản nhận", color = MaterialTheme.colorScheme.error)
                        else {
                            Text(settings.bankName)
                            Text(settings.bankAccount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(settings.bankOwner)
                        }
                    }
                }
                val pending = payments.firstOrNull { it.status == PaymentStatus.PENDING.name }
                if (pending != null) item {
                    GlassCard(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                        Text("Yêu cầu đang chờ", fontWeight = FontWeight.Bold)
                        Text("${pending.plan} • ${v3Money(pending.amount)}")
                        Text("Nội dung: ${pending.transferCode}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun V3PlanCard(name: String, price: Long, features: List<String>, onBuy: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("${v3Money(price)}/tháng", fontWeight = FontWeight.Bold) }
        features.forEach { Text("✓ $it") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Tạo yêu cầu thanh toán") }
    }
}

@Composable
private fun V3ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick), PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null) }
    }
}

@Composable
private fun V3PinDialog(hasPin: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDisable: (() -> Unit)?) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    V3SimpleDialog(if (hasPin) "Đổi PIN" else "Bật PIN", onDismiss, onDisable, pin.length >= 4 && pin == confirm, { onSave(pin, confirm) }) {
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN 4–12 số") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(12) }, label = { Text("Nhập lại PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun V3NameEntityDialog(title: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(current) { mutableStateOf(current) }
    V3SimpleDialog(title, onDismiss, onDelete, name.isNotBlank(), { onSave(name.trim()) }) {
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}
