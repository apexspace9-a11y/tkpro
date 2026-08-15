package vn.tietkiem.pro.ui.v4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val vi = Locale("vi", "VN")

@Composable
fun V4Page(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun V4Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun V4SectionHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
fun V4Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    emphasis: Boolean = false
) {
    V4Card(modifier, PaddingValues(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!supporting.isNullOrBlank()) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun V4StatusDot(online: Boolean, text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, RoundedCornerShape(50))
            )
        }
    )
}

fun v4Money(value: Long): String = NumberFormat.getNumberInstance(vi).format(value) + " ₫"
fun v4HiddenMoney(value: Long, hidden: Boolean): String = if (hidden) "•••••• ₫" else v4Money(value)
fun v4Date(value: Long): String = SimpleDateFormat("dd/MM/yyyy", vi).format(value)
fun v4DateTime(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", vi).format(value)
fun v4Month(value: Long): String = SimpleDateFormat("'Tháng' MM/yyyy", vi).format(value)
fun v4ParseMoney(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L
fun v4ParseDate(value: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", vi).apply { isLenient = false }.parse(value)?.time
}.getOrNull()

val V4Positive: Color @Composable get() = MaterialTheme.colorScheme.primary
val V4Negative: Color @Composable get() = MaterialTheme.colorScheme.error
