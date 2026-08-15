package vn.tietkiem.pro.ui.v4

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.online.CloudApi

@Composable
fun V4AiScreen(vm: V4ViewModel, settings: AppSettings, navigate: (V4Destination) -> Unit) {
    val messages by vm.aiMessages.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    Column(Modifier.fillMaxSize()) {
        if (!(settings.premiumActive && settings.premiumTier == PremiumTier.PRO.name)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI yêu cầu gói PRO", fontWeight = FontWeight.Bold)
                        Text("Premium được xác thực từ tài khoản online", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { navigate(V4Destination.PREMIUM) }) { Text("Xem gói") }
                }
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Phân tích tháng này", "Tối ưu ngân sách", "Lập kế hoạch trả nợ", "Đánh giá dòng tiền").forEach { prompt ->
                AssistChip(onClick = { input = prompt }, label = { Text(prompt) })
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    V4Card(Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Trợ lý tài chính", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Hỏi về dòng tiền, ngân sách, mục tiêu hoặc nợ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.widthIn(max = 560.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Text(message.content, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(14.dp).imePadding(), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    input,
                    { input = it },
                    placeholder = { Text("Nhập câu hỏi…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            vm.sendAiMessage(text)
                            input = ""
                        }
                    },
                    enabled = !busy && input.isNotBlank()
                ) { Icon(Icons.Default.Send, "Gửi") }
            }
        }
    }
}

@Composable
fun V4PremiumScreen(vm: V4ViewModel, settings: AppSettings) {
    val payments by vm.remotePayments.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            V4Card(Modifier.fillMaxWidth(), PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (settings.premiumActive) settings.premiumTier else "FREE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (settings.premiumActive && settings.premiumExpiry > 0) "Có hiệu lực đến ${v4Date(settings.premiumExpiry)}" else "Tài khoản hiện tại",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { V4SectionHeader("Chọn gói") }
        item {
            V4PlanCard(
                name = "PLUS",
                price = settings.plusPrice,
                features = listOf("Đồng bộ cloud", "Ngân sách nâng cao", "Mục tiêu và Debt Planner", "OCR hóa đơn"),
                onBuy = { vm.createPremiumPayment(PremiumTier.PLUS) }
            )
        }
        item {
            V4PlanCard(
                name = "PRO",
                price = settings.proPrice,
                features = listOf("Toàn bộ PLUS", "AI Financial Copilot", "AI dùng dữ liệu tài chính", "Ưu tiên tính năng mới"),
                onBuy = { vm.createPremiumPayment(PremiumTier.PRO) }
            )
        }
        item { V4SectionHeader("Chuyển khoản") }
        item {
            V4Card(Modifier.fillMaxWidth()) {
                if (settings.bankName.isBlank() || settings.bankAccount.isBlank()) {
                    Text("Chưa có tài khoản nhận thanh toán", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(settings.bankName, fontWeight = FontWeight.SemiBold)
                    Text(settings.bankAccount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(settings.bankOwner, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (payments.isNotEmpty()) {
            item { V4SectionHeader("Yêu cầu thanh toán") }
            items(payments, key = { it.id }) { payment ->
                V4Card(Modifier.fillMaxWidth(), PaddingValues(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(payment.plan, fontWeight = FontWeight.Bold)
                            Text(payment.transferCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(v4Money(payment.amount), fontWeight = FontWeight.Bold)
                    }
                    Text(v4PaymentStatus(payment.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun V4PlanCard(name: String, price: Long, features: List<String>, onBuy: () -> Unit) {
    V4Card(Modifier.fillMaxWidth(), PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${v4Money(price)}/tháng", fontWeight = FontWeight.Bold)
        }
        features.forEach { Text("✓ $it") }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Tạo yêu cầu thanh toán") }
    }
}

@Composable
fun V4SettingsScreen(
    vm: V4ViewModel,
    settings: AppSettings,
    onBiometricRequest: () -> Unit,
    navigate: (V4Destination) -> Unit
) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val payees by vm.payees.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var showPin by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var editPayee by remember { mutableStateOf<PayeeEntity?>(null) }
    var newPayee by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<TagEntity?>(null) }
    var newTag by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportTransactionsCsv) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { V4SectionHeader("Tài khoản online", settings.cloudEmail) }
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Text(settings.serverUrl, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::syncNow, Modifier.weight(1f)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(6.dp)); Text("Đồng bộ") }
                    OutlinedButton(onClick = vm::logout, Modifier.weight(1f)) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(6.dp)); Text("Đăng xuất") }
                }
            }
        }

        item { V4SectionHeader("Giao diện") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("SYSTEM" to "Hệ thống", "LIGHT" to "Sáng", "DARK" to "Tối").forEachIndexed { index, (value, label) ->
                    SegmentedButton(settings.theme == value, { vm.setTheme(value) }, SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                }
            }
        }
        item { V4ToggleRow(Icons.Default.VisibilityOff, "Ẩn số tiền", "Che số tiền trên các màn hình", settings.privacyMode, vm::setPrivacyMode) }
        item {
            V4ToggleRow(Icons.Default.Notifications, "Thông báo tài chính", "Ngân sách, nợ, mục tiêu và định kỳ", settings.notificationsEnabled) { enabled ->
                if (!enabled) vm.setNotifications(false)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else vm.setNotifications(true)
            }
        }

        item { V4SectionHeader("Bảo mật") }
        item { V4ActionRow(Icons.Default.Lock, if (settings.hasPin) "Đổi PIN" else "Bật PIN", "Khóa khi rời ứng dụng") { showPin = true } }
        if (settings.hasPin) {
            item {
                V4ToggleRow(Icons.Default.Fingerprint, "Sinh trắc học", "Vân tay hoặc khóa màn hình", settings.biometricEnabled) { enabled ->
                    vm.setBiometric(enabled)
                    if (enabled) onBiometricRequest()
                }
            }
        }

        item { V4SectionHeader("Dữ liệu") }
        item { V4ActionRow(Icons.Default.CloudSync, "Đồng bộ ngay", "Đẩy hoặc nhận dữ liệu từ server", vm::syncNow) }
        item { V4ActionRow(Icons.Default.Backup, "Xuất bản sao lưu", "JSON đầy đủ", { exportBackup.launch("TietKiemPro-V4-${System.currentTimeMillis()}.json") }) }
        item { V4ActionRow(Icons.Default.TableView, "Xuất CSV", "Giao dịch để mở bằng bảng tính", { exportCsv.launch("TietKiemPro-V4.csv") }) }
        item { V4ActionRow(Icons.Default.Restore, "Khôi phục bản sao", "Dữ liệu khôi phục sẽ được đồng bộ lên cloud", { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) }

        item { V4SectionHeader("Danh mục", "${categories.size} mục") { TextButton(onClick = { newCategory = true }) { Icon(Icons.Default.Add, null); Text("Thêm") } } }
        items(categories, key = { "category-${it.id}" }) { category ->
            V4EntityRow(category.name, if (category.type == CategoryType.EXPENSE.name) "Chi tiêu" else "Thu nhập") { editCategory = category }
        }

        item { V4SectionHeader("Đối tác / cửa hàng", "${payees.size} mục") { TextButton(onClick = { newPayee = true }) { Icon(Icons.Default.Add, null); Text("Thêm") } } }
        items(payees, key = { "payee-${it.id}" }) { payee -> V4EntityRow(payee.name, "Payee") { editPayee = payee } }

        item { V4SectionHeader("Tags", "${tags.size} thẻ") { TextButton(onClick = { newTag = true }) { Icon(Icons.Default.Add, null); Text("Thêm") } } }
        items(tags, key = { "tag-${it.id}" }) { tag -> V4EntityRow("#${tag.name}", "Tag") { editTag = tag } }

        item { V4ActionRow(Icons.Default.AdminPanelSettings, "Admin CP", "Quản trị server và kiểm tra hệ thống") { navigate(V4Destination.ADMIN) } }
        item { Spacer(Modifier.height(18.dp)) }
    }

    if (showPin) V4PinDialog(settings.hasPin, { showPin = false }, { pin, confirm -> vm.setPin(pin, confirm); showPin = false }, if (settings.hasPin) ({ vm.clearPin(); showPin = false }) else null)

    if (newCategory || editCategory != null) {
        V4CategoryDialog(editCategory, { newCategory = false; editCategory = null }, { vm.saveCategory(it); newCategory = false; editCategory = null }, editCategory?.let { row -> { vm.deleteCategory(row); editCategory = null } })
    }
    if (newPayee || editPayee != null) {
        V4NameDialog(if (editPayee == null) "Thêm đối tác" else "Sửa đối tác", editPayee?.name.orEmpty(), { newPayee = false; editPayee = null }, { name -> vm.savePayee(editPayee?.copy(name = name) ?: PayeeEntity(name = name)); newPayee = false; editPayee = null }, editPayee?.let { row -> { vm.deletePayee(row); editPayee = null } })
    }
    if (newTag || editTag != null) {
        V4NameDialog(if (editTag == null) "Thêm tag" else "Sửa tag", editTag?.name.orEmpty(), { newTag = false; editTag = null }, { name -> vm.saveTag(editTag?.copy(name = name) ?: TagEntity(name = name)); newTag = false; editTag = null }, editTag?.let { row -> { vm.deleteTag(row); editTag = null } })
    }
}

@Composable
fun V4AdminScreen(vm: V4ViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val unlocked by vm.adminUnlocked.collectAsStateWithLifecycle()
    val config by vm.adminConfig.collectAsStateWithLifecycle()
    val payments by vm.adminPayments.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var pendingTestAfterPermission by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingTestAfterPermission) vm.testNotification()
        pendingTestAfterPermission = false
    }

    if (!unlocked) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
            V4Card(Modifier.widthIn(max = 560.dp).fillMaxWidth(), PaddingValues(22.dp)) {
                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Admin CP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Xác thực với máy chủ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    key,
                    { key = it.take(160) },
                    label = { Text("Admin key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.verifyAdminKey(key) }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Default.VpnKey, null); Spacer(Modifier.width(8.dp)); Text("Xác thực")
                }
            }
        }
        return
    }

    val currentConfig = config
    if (currentConfig == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LaunchedEffect(Unit) { vm.loadAdminData() }
        return
    }

    var aiEndpoint by remember(currentConfig) { mutableStateOf(currentConfig.aiEndpoint) }
    var aiModel by remember(currentConfig) { mutableStateOf(currentConfig.aiModel) }
    var aiKey by remember { mutableStateOf("") }
    var aiPrompt by remember(currentConfig) { mutableStateOf(currentConfig.aiSystemPrompt) }
    var bankName by remember(currentConfig) { mutableStateOf(currentConfig.bankName) }
    var bankAccount by remember(currentConfig) { mutableStateOf(currentConfig.bankAccount) }
    var bankOwner by remember(currentConfig) { mutableStateOf(currentConfig.bankOwner) }
    var plusPrice by remember(currentConfig) { mutableStateOf(currentConfig.plusPrice.toString()) }
    var proPrice by remember(currentConfig) { mutableStateOf(currentConfig.proPrice.toString()) }
    var overrideEmail by remember { mutableStateOf(settings.cloudEmail) }
    var overrideTier by remember { mutableStateOf(PremiumTier.PRO.name) }
    var overrideMonths by remember { mutableStateOf("1") }
    var reviewMonths by remember { mutableStateOf("1") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                V4SectionHeader("Admin CP", "Quản trị online")
                IconButton(onClick = vm::closeAdmin) { Icon(Icons.Default.Lock, "Khóa Admin") }
            }
        }

        item { V4SectionHeader("Kiểm tra hệ thống") }
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Thông báo thiết bị", fontWeight = FontWeight.Bold)
                        Text("Gửi một thông báo thử ngay trên máy này", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pendingTestAfterPermission = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.testNotification()
                    }) { Text("Test") }
                }
            }
        }

        item { V4SectionHeader("AI server") }
        item { OutlinedTextField(aiEndpoint, { aiEndpoint = it }, label = { Text("API endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(aiModel, { aiModel = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            OutlinedTextField(
                aiKey,
                { aiKey = it },
                label = { Text(if (currentConfig.aiApiKeySet) "API key mới (đang có key)" else "API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item { OutlinedTextField(aiPrompt, { aiPrompt = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 4) }

        item { V4SectionHeader("Thanh toán") }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 560.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(bankName, { bankName = it }, label = { Text("Ngân hàng") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(bankAccount, { bankAccount = it.filter(Char::isDigit) }, label = { Text("Số tài khoản") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(bankName, { bankName = it }, label = { Text("Ngân hàng") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(bankAccount, { bankAccount = it.filter(Char::isDigit) }, label = { Text("Số tài khoản") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        }
        item { OutlinedTextField(bankOwner, { bankOwner = it }, label = { Text("Chủ tài khoản") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { V4MoneyField(plusPrice, { plusPrice = it }, "PLUS / tháng") }
                Box(Modifier.weight(1f)) { V4MoneyField(proPrice, { proPrice = it }, "PRO / tháng") }
            }
        }
        item {
            Button(
                onClick = {
                    vm.saveAdminConfig(
                        CloudApi.AdminConfig(
                            aiEndpoint = aiEndpoint.trim(),
                            aiModel = aiModel.trim(),
                            aiApiKeySet = currentConfig.aiApiKeySet || aiKey.isNotBlank(),
                            aiSystemPrompt = aiPrompt.trim(),
                            bankName = bankName.trim(),
                            bankAccount = bankAccount.trim(),
                            bankOwner = bankOwner.trim(),
                            plusPrice = v4ParseMoney(plusPrice),
                            proPrice = v4ParseMoney(proPrice)
                        ),
                        aiKey
                    )
                    aiKey = ""
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Lưu cấu hình server") }
        }

        item { V4SectionHeader("Premium override") }
        item { OutlinedTextField(overrideEmail, { overrideEmail = it }, label = { Text("Email tài khoản") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumTier.entries.forEach { tier ->
                    FilterChip(selected = overrideTier == tier.name, onClick = { overrideTier = tier.name }, label = { Text(tier.name) })
                }
            }
        }
        item { OutlinedTextField(overrideMonths, { overrideMonths = it.filter(Char::isDigit).take(3) }, label = { Text("Số tháng") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedButton(onClick = { vm.setPremiumForUser(overrideEmail, overrideTier, overrideMonths.toIntOrNull() ?: 1) }, enabled = overrideEmail.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Cập nhật Premium") } }

        item { V4SectionHeader("Yêu cầu thanh toán", "${payments.size} yêu cầu gần nhất") }
        item { OutlinedTextField(reviewMonths, { reviewMonths = it.filter(Char::isDigit).take(3) }, label = { Text("Số tháng khi duyệt") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        if (payments.isEmpty()) item { Text("Chưa có yêu cầu", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(payments, key = { it.id }) { payment ->
            V4Card(Modifier.fillMaxWidth(), PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(payment.email.ifBlank { "Tài khoản" }, fontWeight = FontWeight.Bold)
                        Text("${payment.plan} • ${payment.transferCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(v4Money(payment.amount), fontWeight = FontWeight.Bold)
                }
                Text(v4PaymentStatus(payment.status))
                if (payment.status == PaymentStatus.PENDING.name) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.reviewPayment(payment, false, 1) }) { Text("Từ chối") }
                        Button(onClick = { vm.reviewPayment(payment, true, reviewMonths.toIntOrNull() ?: 1) }) { Text("Duyệt") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun V4ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    V4Card(Modifier.fillMaxWidth(), PaddingValues(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChecked)
        }
    }
}

@Composable
private fun V4ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun V4EntityRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.Edit, null)
        }
    }
}

@Composable
private fun V4PinDialog(hasPin: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDisable: (() -> Unit)?) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    V4EditorDialog(if (hasPin) "Đổi PIN" else "Bật PIN", onDismiss, onDisable, pin.length >= 4 && pin == confirm, { onSave(pin, confirm) }) {
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN 4–12 số") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(12) }, label = { Text("Nhập lại PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
private fun V4CategoryDialog(item: CategoryEntity?, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var type by remember(item) { mutableStateOf(item?.type ?: CategoryType.EXPENSE.name) }
    V4EditorDialog(if (item == null) "Thêm danh mục" else "Sửa danh mục", onDismiss, onDelete, name.isNotBlank(), {
        onSave(item?.copy(name = name.trim(), type = type) ?: CategoryEntity(name = name.trim(), type = type, icon = "category", sortOrder = 100))
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == CategoryType.EXPENSE.name, { type = CategoryType.EXPENSE.name }, label = { Text("Chi tiêu") })
            FilterChip(type == CategoryType.INCOME.name, { type = CategoryType.INCOME.name }, label = { Text("Thu nhập") })
        }
    }
}

@Composable
private fun V4NameDialog(title: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember(current) { mutableStateOf(current) }
    V4EditorDialog(title, onDismiss, onDelete, name.isNotBlank(), { onSave(name.trim()) }) {
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

private fun v4PaymentStatus(status: String): String = when (status) {
    PaymentStatus.PENDING.name -> "Đang chờ duyệt"
    PaymentStatus.APPROVED.name -> "Đã duyệt"
    PaymentStatus.REJECTED.name -> "Đã từ chối"
    else -> status
}
