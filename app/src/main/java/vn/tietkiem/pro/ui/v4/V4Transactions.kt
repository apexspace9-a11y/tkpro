package vn.tietkiem.pro.ui.v4

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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.tietkiem.pro.ai.ReceiptOcrService
import vn.tietkiem.pro.data.*
import java.util.Calendar

enum class V4TxPeriod(val label: String) { WEEK("7 ngày"), MONTH("Tháng này"), QUARTER("3 tháng"), ALL("Tất cả") }
data class V4SplitDraft(val categoryId: Long?, val amount: String, val note: String = "")

@Composable
fun V4TransactionsScreen(vm: V4ViewModel, settings: AppSettings) {
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
    var periodName by rememberSaveable { mutableStateOf(V4TxPeriod.MONTH.name) }
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
    val start = remember(periodName) { v4PeriodStart(V4TxPeriod.valueOf(periodName)) }

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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    placeholder = { Text("Tìm số tiền, ghi chú, cửa hàng, tag…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Xóa") } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "ALL" to "Tất cả",
                        TransactionType.EXPENSE.name to "Chi",
                        TransactionType.INCOME.name to "Thu",
                        TransactionType.TRANSFER.name to "Chuyển"
                    ).forEach { (value, label) ->
                        FilterChip(selected = typeFilter == value, onClick = { typeFilter = value }, label = { Text(label) })
                    }
                }
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 600.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            V4StringChoice("Thời gian", periodName, V4TxPeriod.entries.map { it.name to it.label }) { periodName = it }
                            Spacer(Modifier.width(4.dp))
                        }
                    } else {
                        V4StringChoice("Thời gian", periodName, V4TxPeriod.entries.map { it.name to it.label }) { periodName = it }
                    }
                }
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 560.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { V4ChoiceField("Tài khoản", accountFilter, listOf(-1L to "Tất cả") + accounts.filterNot { it.archived }.map { it.id to it.name }) { accountFilter = it } }
                            Box(Modifier.weight(1f)) { V4ChoiceField("Danh mục", categoryFilter, listOf(-1L to "Tất cả") + categories.map { it.id to it.name }) { categoryFilter = it } }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            V4ChoiceField("Tài khoản", accountFilter, listOf(-1L to "Tất cả") + accounts.filterNot { it.archived }.map { it.id to it.name }) { accountFilter = it }
                            V4ChoiceField("Danh mục", categoryFilter, listOf(-1L to "Tất cả") + categories.map { it.id to it.name }) { categoryFilter = it }
                        }
                    }
                }
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 560.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            V4Metric("Thu", v4HiddenMoney(income, settings.privacyMode), Modifier.weight(1f))
                            V4Metric("Chi", v4HiddenMoney(expense, settings.privacyMode), Modifier.weight(1f))
                            V4Metric("Dòng tiền", v4HiddenMoney(income - expense, settings.privacyMode), Modifier.weight(1f))
                        }
                    } else {
                        V4Metric("Dòng tiền theo bộ lọc", v4HiddenMoney(income - expense, settings.privacyMode), Modifier.fillMaxWidth(), emphasis = true)
                    }
                }
            }
            item { V4SectionHeader("${shown.size} giao dịch") }
            if (shown.isEmpty()) item { Text("Không có giao dịch phù hợp", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { it.id }) { tx ->
                val meta = metaMap[tx.id]
                val payee = meta?.payeeId?.let { payeeMap[it]?.name }
                val tagNames = tagsByTx[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name }
                Surface(
                    Modifier.fillMaxWidth().clickable { editing = tx },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(
                                when (tx.type) {
                                    TransactionType.INCOME.name -> Icons.Default.SouthWest
                                    TransactionType.EXPENSE.name -> Icons.Default.NorthEast
                                    else -> Icons.Default.SwapHoriz
                                },
                                null,
                                Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(payee ?: tx.categoryId?.let { categoryMap[it]?.name } ?: if (tx.type == TransactionType.TRANSFER.name) "Chuyển tiền" else "Giao dịch", fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(v4DateTime(tx.occurredAt)); append(" • "); append(accountMap[tx.accountId]?.name ?: "Tài khoản")
                                    if (tagNames.isNotEmpty()) { append(" • "); append(tagNames.joinToString(" #", prefix = "#")) }
                                    if (tx.note.isNotBlank()) { append(" • "); append(tx.note) }
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        val sign = when (tx.type) { TransactionType.INCOME.name -> "+"; TransactionType.EXPENSE.name -> "−"; else -> "" }
                        Text("$sign${v4HiddenMoney(tx.amount, settings.privacyMode)}", fontWeight = FontWeight.Bold, color = if (tx.type == TransactionType.EXPENSE.name) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Giao dịch mới") }
        )
    }

    if (creating || editing != null) {
        val tx = editing
        V4TransactionEditor(
            item = tx,
            existingMeta = tx?.let { metaMap[it.id] },
            existingTagIds = tx?.let { tagsByTx[it.id].orEmpty().map(TransactionTagEntity::tagId) }.orEmpty(),
            existingSplits = tx?.let { row -> splits.filter { it.transactionId == row.id } }.orEmpty(),
            accounts = accounts.filterNot { it.archived },
            categories = categories,
            payees = payees,
            tags = tags,
            onDismiss = { creating = false; editing = null },
            onDelete = tx?.let { current -> { vm.deleteTransaction(current); editing = null } },
            onSave = { row, meta, tagIds, splitRows ->
                vm.saveRichTransaction(row, meta, tagIds, splitRows)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun V4TransactionEditor(
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
    var date by remember(item) { mutableStateOf(v4Date(item?.occurredAt ?: System.currentTimeMillis())) }
    val selectedTags = remember(item) { mutableStateListOf<Long>().apply { addAll(existingTagIds) } }
    val splitDrafts = remember(item) { mutableStateListOf<V4SplitDraft>().apply { existingSplits.forEach { add(V4SplitDraft(it.categoryId, it.amount.toString(), it.note)) } } }

    fun applyScan(scan: vn.tietkiem.pro.ai.ReceiptScanResult) {
        if (scan.amount > 0) amount = scan.amount.toString()
        if (scan.merchant.isNotBlank()) merchant = scan.merchant
        if (scan.dateText.isNotBlank() && v4ParseDate(scan.dateText) != null) date = scan.dateText
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
            categoryId = null
            splitDrafts.clear()
        } else if (categories.none { it.id == categoryId && it.type == type }) {
            categoryId = categories.firstOrNull { it.type == type }?.id
        }
    }
    LaunchedEffect(accountId) { if (toAccountId == accountId) toAccountId = accounts.firstOrNull { it.id != accountId }?.id }

    val splitTotal = splitDrafts.sumOf { v4ParseMoney(it.amount) }
    val splitValid = splitDrafts.isEmpty() || (splitDrafts.all { v4ParseMoney(it.amount) > 0 } && splitTotal == v4ParseMoney(amount))
    val saveEnabled = v4ParseMoney(amount) > 0 && accountId > 0 && splitValid &&
        (type != TransactionType.TRANSFER.name || (toAccountId != null && toAccountId != accountId))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 760.dp).heightIn(max = 820.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (item == null) "Giao dịch mới" else "Sửa giao dịch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Chi tiết giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Đóng") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TransactionType.EXPENSE.name to "Chi", TransactionType.INCOME.name to "Thu", TransactionType.TRANSFER.name to "Chuyển").forEach { (value, label) ->
                            FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(15) }, label = { Text("Số tiền") }, suffix = { Text("₫") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    V4ChoiceField("Tài khoản", accountId, accounts.map { it.id to it.name }) { accountId = it }
                    if (type == TransactionType.TRANSFER.name) {
                        V4ChoiceField("Tài khoản nhận", toAccountId, accounts.filter { it.id != accountId }.map { it.id as Long? to it.name }) { toAccountId = it }
                    } else {
                        V4ChoiceField("Danh mục", categoryId, categories.filter { it.type == type }.map { it.id as Long? to it.name }) { categoryId = it }
                        V4ChoiceField("Cửa hàng / đối tác", payeeId, listOf(null to "Không chọn") + payees.map { it.id as Long? to it.name }) { payeeId = it }
                        OutlinedTextField(merchant, { merchant = it }, label = { Text("Tên trên hóa đơn") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    OutlinedTextField(date, { date = it.take(10) }, label = { Text("Ngày dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                    if (type != TransactionType.TRANSFER.name) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 500.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton({ camera.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Quét hóa đơn") }
                                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Chọn ảnh") }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton({ camera.launch(null) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Quét hóa đơn") }
                                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Chọn ảnh") }
                                }
                            }
                        }
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (attachmentUri.isNotBlank()) Text("Đã gắn ảnh hóa đơn", color = MaterialTheme.colorScheme.primary)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Khoản định kỳ / subscription", Modifier.weight(1f))
                            Switch(subscription, { subscription = it })
                        }

                        if (tags.isNotEmpty()) {
                            Text("Tags", fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Column(Modifier.weight(1f)) {
                                Text("Chia giao dịch", fontWeight = FontWeight.Bold)
                                if (splitDrafts.isNotEmpty()) Text("${v4Money(splitTotal)} / ${v4Money(v4ParseMoney(amount))}", color = if (splitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { splitDrafts.add(V4SplitDraft(categoryId, "")) }) { Icon(Icons.Default.CallSplit, null); Spacer(Modifier.width(5.dp)); Text("Thêm") }
                        }
                        splitDrafts.forEachIndexed { index, draft ->
                            V4Card(Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                                V4ChoiceField("Danh mục ${index + 1}", draft.categoryId, categories.filter { it.type == type }.map { it.id as Long? to it.name }) { splitDrafts[index] = draft.copy(categoryId = it) }
                                OutlinedTextField(draft.amount, { splitDrafts[index] = draft.copy(amount = it.filter(Char::isDigit)) }, label = { Text("Số tiền") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(draft.note, { splitDrafts[index] = draft.copy(note = it) }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                TextButton(onClick = { splitDrafts.removeAt(index) }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, null); Text("Xóa phần") }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Xóa") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val occurredAt = v4ParseDate(date) ?: System.currentTimeMillis()
                            val row = item?.copy(
                                type = type,
                                amount = v4ParseMoney(amount),
                                accountId = accountId,
                                toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                                categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                                note = note.trim(),
                                occurredAt = occurredAt
                            ) ?: TransactionEntity(
                                type = type,
                                amount = v4ParseMoney(amount),
                                accountId = accountId,
                                toAccountId = if (type == TransactionType.TRANSFER.name) toAccountId else null,
                                categoryId = if (type == TransactionType.TRANSFER.name) null else categoryId,
                                note = note.trim(),
                                occurredAt = occurredAt
                            )
                            val meta = if (type == TransactionType.TRANSFER.name || (payeeId == null && merchant.isBlank() && attachmentUri.isBlank() && !subscription)) null
                            else TransactionMetaEntity(item?.id ?: 0L, payeeId, attachmentUri, merchant.trim(), subscription)
                            val splitRows = splitDrafts.map { TransactionSplitEntity(transactionId = item?.id ?: 0L, categoryId = it.categoryId, amount = v4ParseMoney(it.amount), note = it.note) }
                            onSave(row, meta, selectedTags.toList(), splitRows)
                        },
                        enabled = saveEnabled
                    ) { Text("Lưu giao dịch") }
                }
            }
        }
    }
}

private fun v4PeriodStart(period: V4TxPeriod): Long? {
    val now = Calendar.getInstance()
    return when (period) {
        V4TxPeriod.ALL -> null
        V4TxPeriod.WEEK -> now.apply { add(Calendar.DAY_OF_YEAR, -6); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        V4TxPeriod.MONTH -> now.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        V4TxPeriod.QUARTER -> now.apply { add(Calendar.MONTH, -2); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
}
