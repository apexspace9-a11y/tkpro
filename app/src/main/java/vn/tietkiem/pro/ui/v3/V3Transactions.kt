package vn.tietkiem.pro.ui.v3

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.tietkiem.pro.ai.ReceiptOcrService
import vn.tietkiem.pro.data.*
import vn.tietkiem.pro.ui.AppViewModel
import java.util.Calendar

enum class V3TxPeriod(val label: String) { WEEK("7N"), MONTH("Tháng"), QUARTER("3T"), ALL("Tất cả") }

data class V3SplitDraft(val categoryId: Long?, val amount: String, val note: String = "")

@Composable
fun V3TransactionsScreen(vm: AppViewModel, settings: AppSettings) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val payees by vm.payees.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val metas by vm.transactionMeta.collectAsStateWithLifecycle()
    val txTags by vm.transactionTags.collectAsStateWithLifecycle()
    val splits by vm.transactionSplits.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var periodName by rememberSaveable { mutableStateOf(V3TxPeriod.MONTH.name) }
    var accountFilter by rememberSaveable { mutableLongStateOf(-1L) }
    var categoryFilter by rememberSaveable { mutableLongStateOf(-1L) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val payeeMap = remember(payees) { payees.associateBy { it.id } }
    val metaMap = remember(metas) { metas.associateBy { it.transactionId } }
    val tagsByTx = remember(txTags) { txTags.groupBy { it.transactionId } }
    val tagMap = remember(tags) { tags.associateBy { it.id } }
    val start = remember(periodName) { v3PeriodStart(V3TxPeriod.valueOf(periodName)) }

    val shown = remember(transactions, query, typeFilter, periodName, accountFilter, categoryFilter, accounts, categories, metas, txTags, payees, tags) {
        transactions.filter { tx ->
            val meta = metaMap[tx.id]
            val tagNames = tagsByTx[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name }
            val haystack = listOf(
                tx.note,
                accountMap[tx.accountId]?.name.orEmpty(),
                tx.toAccountId?.let { accountMap[it]?.name }.orEmpty(),
                tx.categoryId?.let { categoryMap[it]?.name }.orEmpty(),
                meta?.payeeId?.let { payeeMap[it]?.name }.orEmpty(),
                meta?.merchantText.orEmpty(),
                tagNames.joinToString(" "),
                tx.amount.toString()
            ).joinToString(" ").lowercase()
            (typeFilter == "ALL" || tx.type == typeFilter) &&
                (start == null || tx.occurredAt >= start) &&
                (accountFilter < 0 || tx.accountId == accountFilter || tx.toAccountId == accountFilter) &&
                (categoryFilter < 0 || tx.categoryId == categoryFilter) &&
                (query.isBlank() || haystack.contains(query.trim().lowercase()))
        }
    }

    val income = shown.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
    val expense = shown.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                V3SectionTitle("Giao dịch Pro", "Payee • tag • split • OCR") {
                    GlassPill("${shown.size}") { Icon(Icons.Default.FilterAlt, null, Modifier.size(17.dp)) }
                }
                OutlinedTextField(
                    query,
                    { query = it },
                    placeholder = { Text("Tìm giao dịch, người nhận, tag…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "Tất cả", TransactionType.INCOME.name to "Thu", TransactionType.EXPENSE.name to "Chi", TransactionType.TRANSFER.name to "Chuyển").forEach { (value, label) ->
                        FilterChip(selected = typeFilter == value, onClick = { typeFilter = value }, label = { Text(label) })
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    V3TxPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = periodName == period.name,
                            onClick = { periodName = period.name },
                            shape = SegmentedButtonDefaults.itemShape(index, V3TxPeriod.entries.size)
                        ) { Text(period.label) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V3Dropdown("Ví", accountFilter, listOf(-1L to "Tất cả") + accounts.filterNot { it.archived }.map { it.id to it.name }, { accountFilter = it }, Modifier.weight(1f))
                    V3Dropdown("Danh mục", categoryFilter, listOf(-1L to "Tất cả") + categories.map { it.id to it.name }, { categoryFilter = it }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassMetric("Thu", hiddenMoney(income, settings.privacyMode), Modifier.weight(1f))
                    GlassMetric("Chi", hiddenMoney(expense, settings.privacyMode), Modifier.weight(1f))
                    GlassMetric("Dòng tiền", hiddenMoney(income - expense, settings.privacyMode), Modifier.weight(1f))
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Không có giao dịch phù hợp", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(shown, key = { it.id }) { tx ->
                        val meta = metaMap[tx.id]
                        val payee = meta?.payeeId?.let { payeeMap[it]?.name }
                        val tagNames = tagsByTx[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name }
                        GlassCard(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { editing = tx },
                            PaddingValues(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (tx.type) {
                                        TransactionType.INCOME.name -> Icons.Default.SouthWest
                                        TransactionType.EXPENSE.name -> Icons.Default.NorthEast
                                        else -> Icons.Default.SwapHoriz
                                    }, null
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(payee ?: tx.categoryId?.let { categoryMap[it]?.name } ?: if (tx.type == TransactionType.TRANSFER.name) "Chuyển tiền" else "Giao dịch", fontWeight = FontWeight.Bold)
                                    Text(
                                        buildString {
                                            append(v3DateTime(tx.occurredAt)); append(" • "); append(accountMap[tx.accountId]?.name ?: "Ví")
                                            if (tagNames.isNotEmpty()) { append(" • "); append(tagNames.joinToString(" #", prefix = "#")) }
                                            if (tx.note.isNotBlank()) { append(" • "); append(tx.note) }
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    hiddenMoney(tx.amount, settings.privacyMode),
                                    fontWeight = FontWeight.Black,
                                    color = if (tx.type == TransactionType.EXPENSE.name) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        FloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "Thêm giao dịch")
        }
    }

    if (creating || editing != null) {
        val tx = editing
        V3TransactionEditor(
            item = tx,
            existingMeta = tx?.let { metaMap[it.id] },
            existingTagIds = tx?.let { tagsByTx[it.id].orEmpty().map(TransactionTagEntity::tagId) }.orEmpty(),
            existingSplits = tx?.let { id -> splits.filter { it.transactionId == id.id } }.orEmpty(),
            accounts = accounts.filterNot { it.archived },
            categories = categories,
            payees = payees,
            tags = tags,
            onDismiss = { creating = false; editing = null },
            onDelete = tx?.let { current -> { vm.deleteTransaction(current); editing = null } },
            onSave = { row, meta, tagIds, splitRows ->
                vm.saveRichTransaction(row, meta, tagIds, splitRows)
                creating = false; editing = null
            }
        )
    }
}

@Composable
private fun V3TransactionEditor(
    item: TransactionEntity?,
    existingMeta: TransactionMetaEntity?,
    existingTagIds: List<Long>,
    existingSplits: List<TransactionSplitEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    payees: List<PayeeEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (TransactionEntity, TransactionMetaEntity?, List<Long>, List<TransactionSplitEntity>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocr = remember { ReceiptOcrService() }
    var scanning by remember { mutableStateOf(false) }

    var type by remember(item) { mutableStateOf(item?.type ?: TransactionType.EXPENSE.name) }
    var amount by remember(item) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var accountId by remember(item, accounts) { mutableLongStateOf(item?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var toAccountId by remember(item, accounts) { mutableStateOf(item?.toAccountId ?: accounts.firstOrNull { it.id != accountId }?.id) }
    var categoryId by remember(item, categories) { mutableStateOf(item?.categoryId ?: categories.firstOrNull { it.type == TransactionType.EXPENSE.name }?.id) }
    var payeeId by remember(existingMeta) { mutableStateOf(existingMeta?.payeeId) }
    var merchant by remember(existingMeta) { mutableStateOf(existingMeta?.merchantText.orEmpty()) }
    var attachmentUri by remember(existingMeta) { mutableStateOf(existingMeta?.attachmentUri.orEmpty()) }
    var subscription by remember(existingMeta) { mutableStateOf(existingMeta?.isSubscription ?: false) }
    var note by remember(item) { mutableStateOf(item?.note.orEmpty()) }
    var date by remember(item) { mutableStateOf(v3Date(item?.occurredAt ?: System.currentTimeMillis())) }
    val selectedTags = remember(item) { mutableStateListOf<Long>().apply { addAll(existingTagIds) } }
    val splitDrafts = remember(item) { mutableStateListOf<V3SplitDraft>().apply {
        existingSplits.forEach { add(V3SplitDraft(it.categoryId, it.amount.toString(), it.note)) }
    } }

    fun applyScan(scan: vn.tietkiem.pro.ai.ReceiptScanResult) {
        if (scan.amount > 0) amount = scan.amount.toString()
        if (scan.merchant.isNotBlank()) merchant = scan.merchant
        if (scan.dateText.isNotBlank() && v3ParseDate(scan.dateText) != null) date = scan.dateText
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch {
            scanning = true
            runCatching { ocr.recognize(bitmap) }.onSuccess(::applyScan)
            scanning = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            attachmentUri = uri.toString()
            scanning = true
            runCatching { ocr.recognize(context, uri) }.onSuccess(::applyScan)
            scanning = false
        }
    }

    LaunchedEffect(type) {
        if (type == TransactionType.TRANSFER.name) {
            categoryId = null; splitDrafts.clear()
        } else if (categories.none { it.id == categoryId && it.type == type }) {
            categoryId = categories.firstOrNull { it.type == type }?.id
        }
    }
    LaunchedEffect(accountId) { if (toAccountId == accountId) toAccountId = accounts.firstOrNull { it.id != accountId }?.id }

    val splitTotal = splitDrafts.sumOf { v3ParseMoney(it.amount) }
    val splitValid = splitDrafts.isEmpty() || (splitDrafts.all { v3ParseMoney(it.amount) > 0 } && splitTotal == v3ParseMoney(amount))
    val saveEnabled = v3ParseMoney(amount) > 0 && accountId > 0 && splitValid &&
        (type != TransactionType.TRANSFER.name || (toAccountId != null && toAccountId != accountId))

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 720.dp), PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (item == null) "Giao dịch mới" else "Sửa giao dịch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Pro editor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TransactionType.EXPENSE.name to "Chi", TransactionType.INCOME.name to "Thu", TransactionType.TRANSFER.name to "Chuyển").forEach { (v, label) ->
                        FilterChip(selected = type == v, onClick = { type = v }, label = { Text(label) })
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(15) }, label = { Text("Số tiền") }, suffix = { Text("₫") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                V3Dropdown("Ví", accountId, accounts.map { it.id to it.name }, { accountId = it })
                if (type == TransactionType.TRANSFER.name) {
                    V3Dropdown("Ví nhận", toAccountId, accounts.filter { it.id != accountId }.map { it.id to it.name }, { toAccountId = it })
                } else {
                    V3Dropdown("Danh mục", categoryId, categories.filter { it.type == type }.map { it.id to it.name }, { categoryId = it })
                    V3Dropdown("Người nhận / cửa hàng", payeeId, listOf(null to "Không chọn") + payees.map { it.id as Long? to it.name }, { payeeId = it })
                    OutlinedTextField(merchant, { merchant = it }, label = { Text("Tên trên hóa đơn") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Ngày dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                if (type != TransactionType.TRANSFER.name) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { camera.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text("Quét hóa đơn") }
                        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Chọn ảnh") }
                    }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (attachmentUri.isNotBlank()) Text("✓ Đã gắn ảnh hóa đơn", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Khoản chi định kỳ / subscription", Modifier.weight(1f))
                        Switch(checked = subscription, onCheckedChange = { subscription = it })
                    }

                    if (tags.isNotEmpty()) {
                        Text("Tags", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in selectedTags,
                                    onClick = { if (tag.id in selectedTags) selectedTags.remove(tag.id) else selectedTags.add(tag.id) },
                                    label = { Text("#${tag.name}") }
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Chia giao dịch", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { splitDrafts.add(V3SplitDraft(categoryId, "")) }) { Icon(Icons.Default.CallSplit, null); Spacer(Modifier.width(4.dp)); Text("Thêm") }
                    }
                    splitDrafts.forEachIndexed { index, draft ->
                        GlassCard(Modifier.fillMaxWidth(), PaddingValues(10.dp)) {
                            V3Dropdown("Danh mục ${index + 1}", draft.categoryId, categories.filter { it.type == type }.map { it.id as Long? to it.name }, { splitDrafts[index] = draft.copy(categoryId = it) })
                            OutlinedTextField(draft.amount, { splitDrafts[index] = draft.copy(amount = it.filter(Char::isDigit)) }, label = { Text("Số tiền") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { splitDrafts.removeAt(index) }) { Icon(Icons.Default.Delete, null) }
                            }
                        }
                    }
                    if (splitDrafts.isNotEmpty()) Text(
                        "Đã chia ${v3Money(splitTotal)} / ${v3Money(v3ParseMoney(amount))}",
                        color = if (splitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Xóa") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Button(onClick = {
                    val row = item?.copy(
                        type = type,
                        amount = v3ParseMoney(amount),
                        accountId = accountId,
                        toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                        categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                        note = note.trim(),
                        occurredAt = v3ParseDate(date) ?: System.currentTimeMillis()
                    ) ?: TransactionEntity(
                        type = type,
                        amount = v3ParseMoney(amount),
                        accountId = accountId,
                        toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                        categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                        note = note.trim(),
                        occurredAt = v3ParseDate(date) ?: System.currentTimeMillis()
                    )
                    val meta = if (type == TransactionType.TRANSFER.name || (payeeId == null && merchant.isBlank() && attachmentUri.isBlank() && !subscription)) null
                    else TransactionMetaEntity(item?.id ?: 0L, payeeId, attachmentUri, merchant.trim(), subscription)
                    val splitRows = splitDrafts.map { TransactionSplitEntity(transactionId = item?.id ?: 0L, categoryId = it.categoryId, amount = v3ParseMoney(it.amount), note = it.note) }
                    onSave(row, meta, selectedTags.toList(), splitRows)
                }, enabled = saveEnabled) { Text("Lưu") }
            }
        }
    }
}

@Composable
fun <T> V3Dropdown(
    label: String,
    selected: T?,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: "Chọn"
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelected(value); expanded = false }) }
        }
    }
}

private fun v3PeriodStart(period: V3TxPeriod): Long? {
    val now = Calendar.getInstance()
    return when (period) {
        V3TxPeriod.ALL -> null
        V3TxPeriod.WEEK -> now.apply { add(Calendar.DAY_OF_YEAR, -6); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        V3TxPeriod.MONTH -> now.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        V3TxPeriod.QUARTER -> now.apply { add(Calendar.MONTH, -2); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
}
