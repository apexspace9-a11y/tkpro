package vn.tietkiem.pro.ui.v4

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.tietkiem.pro.data.AppSettings
import vn.tietkiem.pro.ui.theme.TietKiemProTheme

enum class V4Destination(val title: String) {
    HOME("Tổng quan"),
    TRANSACTIONS("Giao dịch"),
    PLAN("Kế hoạch"),
    MORE("Thêm"),
    ACCOUNTS("Tài khoản"),
    ANALYTICS("Phân tích"),
    AI("Trợ lý AI"),
    PREMIUM("Premium"),
    SETTINGS("Cài đặt"),
    ADMIN("Admin CP")
}

private val mainDestinations = listOf(
    V4Destination.HOME,
    V4Destination.TRANSACTIONS,
    V4Destination.PLAN,
    V4Destination.MORE
)

@Composable
fun V4AppRoot(vm: V4ViewModel, onBiometricRequest: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val current = settings ?: return
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }

    TietKiemProTheme(current.theme) {
        Box(Modifier.fillMaxSize()) {
            when (online.stage) {
                V4OnlineStage.CHECKING -> V4LoadingScreen()
                V4OnlineStage.AUTH_REQUIRED -> V4AuthScreen(vm, current, online)
                V4OnlineStage.ERROR -> V4ConnectionErrorScreen(online, vm::retryOnline, vm::logout)
                V4OnlineStage.READY -> {
                    if (current.hasPin && !unlocked) {
                        V4UnlockScreen(current.biometricEnabled, vm::verifyPin, onBiometricRequest)
                    } else {
                        if (!current.hasPin && !unlocked) LaunchedEffect(Unit) { vm.unlockWithoutPin() }
                        V4MainShell(vm, current, online, onBiometricRequest)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }
}

@Composable
private fun V4MainShell(
    vm: V4ViewModel,
    settings: AppSettings,
    online: V4OnlineState,
    onBiometricRequest: () -> Unit
) {
    var destinationName by rememberSaveable { mutableStateOf(V4Destination.HOME.name) }
    val destination = V4Destination.valueOf(destinationName)
    val isMain = destination in mainDestinations

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 760.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Icon(Icons.Default.AccountBalanceWallet, null, Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                ) {
                    Spacer(Modifier.height(8.dp))
                    mainDestinations.forEach { item ->
                        NavigationRailItem(
                            selected = destination == item,
                            onClick = { destinationName = item.name },
                            icon = { Icon(v4DestinationIcon(item), item.title) },
                            label = { Text(item.title) },
                            alwaysShowLabel = true
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = destination == V4Destination.AI,
                        onClick = { destinationName = V4Destination.AI.name },
                        icon = { Icon(Icons.Default.AutoAwesome, "AI") },
                        label = { Text("AI") },
                        alwaysShowLabel = true
                    )
                    Spacer(Modifier.height(12.dp))
                }

                V4ContentScaffold(
                    modifier = Modifier.weight(1f),
                    destination = destination,
                    isMain = isMain,
                    online = online,
                    onBack = { destinationName = V4Destination.MORE.name },
                    onSync = vm::syncNow
                ) { padding ->
                    V4DestinationContent(vm, settings, destination, padding, { destinationName = it.name }, onBiometricRequest)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    V4TopBar(
                        destination = destination,
                        isMain = isMain,
                        online = online,
                        onBack = { destinationName = V4Destination.MORE.name },
                        onSync = vm::syncNow,
                        onAi = { destinationName = V4Destination.AI.name }
                    )
                },
                bottomBar = {
                    if (isMain) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            mainDestinations.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item,
                                    onClick = { destinationName = item.name },
                                    icon = { Icon(v4DestinationIcon(item), item.title) },
                                    label = { Text(item.title, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                V4DestinationContent(vm, settings, destination, padding, { destinationName = it.name }, onBiometricRequest)
            }
        }
    }
}

@Composable
private fun V4ContentScaffold(
    modifier: Modifier,
    destination: V4Destination,
    isMain: Boolean,
    online: V4OnlineState,
    onBack: () -> Unit,
    onSync: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { V4TopBar(destination, isMain, online, onBack, onSync, null) },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V4TopBar(
    destination: V4Destination,
    isMain: Boolean,
    online: V4OnlineState,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onAi: (() -> Unit)?
) {
    TopAppBar(
        title = {
            Column {
                Text(destination.title, fontWeight = FontWeight.Bold)
                if (destination == V4Destination.HOME) {
                    Text(
                        online.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            if (!isMain) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Quay lại") }
        },
        actions = {
            if (onAi != null && destination != V4Destination.AI) {
                IconButton(onClick = onAi) { Icon(Icons.Default.AutoAwesome, "AI") }
            }
            IconButton(onClick = onSync, enabled = !online.syncing) {
                if (online.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Sync, "Đồng bộ")
            }
        }
    )
}

@Composable
private fun V4DestinationContent(
    vm: V4ViewModel,
    settings: AppSettings,
    destination: V4Destination,
    padding: PaddingValues,
    navigate: (V4Destination) -> Unit,
    onBiometricRequest: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (destination) {
            V4Destination.HOME -> V4DashboardScreen(vm, settings, navigate)
            V4Destination.TRANSACTIONS -> V4TransactionsScreen(vm, settings)
            V4Destination.PLAN -> V4PlanScreen(vm, settings)
            V4Destination.MORE -> V4MoreScreen(vm, settings, navigate)
            V4Destination.ACCOUNTS -> V4AccountsScreen(vm, settings)
            V4Destination.ANALYTICS -> V4AnalyticsScreen(vm, settings)
            V4Destination.AI -> V4AiScreen(vm, settings, navigate)
            V4Destination.PREMIUM -> V4PremiumScreen(vm, settings)
            V4Destination.SETTINGS -> V4SettingsScreen(vm, settings, onBiometricRequest, navigate)
            V4Destination.ADMIN -> V4AdminScreen(vm, settings)
        }
    }
}

@Composable
private fun V4AuthScreen(vm: V4ViewModel, settings: AppSettings, online: V4OnlineState) {
    var server by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var email by rememberSaveable(settings.cloudEmail) { mutableStateOf(settings.cloudEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (register) "Tạo tài khoản" else "Đăng nhập", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Tiết Kiệm Pro V4", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    server,
                    { server = it },
                    label = { Text("Máy chủ") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    email,
                    { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Mật khẩu") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (online.error.isNotBlank()) Text(online.error, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { vm.login(server, email, password, register) },
                    enabled = !online.syncing && server.isNotBlank() && email.isNotBlank() && password.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (online.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (register) "Tạo tài khoản" else "Đăng nhập")
                }
                TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Tạo mới")
                }
            }
        }
    }
}

@Composable
private fun V4ConnectionErrorScreen(state: V4OnlineState, retry: () -> Unit, logout: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.error)
            Text("Không thể kết nối", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(state.error.ifBlank { "Máy chủ hiện không phản hồi" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Thử lại") }
            TextButton(onClick = logout) { Text("Đăng nhập tài khoản khác") }
        }
    }
}

@Composable
private fun V4LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Đang kết nối…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun V4UnlockScreen(biometric: Boolean, onUnlock: (String) -> Unit, onBiometric: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 460.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Mở khóa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onUnlock(pin) }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Mở khóa") }
                if (biometric) OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Sinh trắc học")
                }
            }
        }
    }
}

private fun v4DestinationIcon(destination: V4Destination) = when (destination) {
    V4Destination.HOME -> Icons.Default.Home
    V4Destination.TRANSACTIONS -> Icons.Default.ReceiptLong
    V4Destination.PLAN -> Icons.Default.Flag
    V4Destination.MORE -> Icons.Default.GridView
    V4Destination.ACCOUNTS -> Icons.Default.AccountBalanceWallet
    V4Destination.ANALYTICS -> Icons.Default.Insights
    V4Destination.AI -> Icons.Default.AutoAwesome
    V4Destination.PREMIUM -> Icons.Default.WorkspacePremium
    V4Destination.SETTINGS -> Icons.Default.Settings
    V4Destination.ADMIN -> Icons.Default.AdminPanelSettings
}
