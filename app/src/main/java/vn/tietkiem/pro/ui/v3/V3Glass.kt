package vn.tietkiem.pro.ui.v3

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val vi = Locale("vi", "VN")

@Composable
fun V3GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "glass-bg")
    val driftA by transition.animateFloat(
        initialValue = -35f,
        targetValue = 55f,
        animationSpec = infiniteRepeatable(tween(8500), RepeatMode.Reverse),
        label = "drift-a"
    )
    val driftB by transition.animateFloat(
        initialValue = 40f,
        targetValue = -45f,
        animationSpec = infiniteRepeatable(tween(10500), RepeatMode.Reverse),
        label = "drift-b"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.primary.copy(alpha = 0.08f),
                        colors.tertiary.copy(alpha = 0.06f),
                        colors.background
                    )
                )
            )
    ) {
        Box(
            Modifier
                .size(250.dp)
                .graphicsLayer { translationX = driftA; translationY = -30f }
                .blur(72.dp)
                .background(colors.primary.copy(alpha = 0.20f), CircleShape)
                .align(Alignment.TopEnd)
        )
        Box(
            Modifier
                .size(230.dp)
                .graphicsLayer { translationX = driftB; translationY = 60f }
                .blur(78.dp)
                .background(colors.tertiary.copy(alpha = 0.18f), CircleShape)
                .align(Alignment.CenterStart)
        )
        Box(
            Modifier
                .size(180.dp)
                .blur(70.dp)
                .background(colors.secondary.copy(alpha = 0.16f), CircleShape)
                .align(Alignment.BottomEnd)
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)), shape)
            .animateContentSize(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leading != null) leading()
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GlassMetric(label: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    GlassCard(modifier, PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun V3SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) action()
    }
}

fun v3Money(value: Long): String = NumberFormat.getNumberInstance(vi).format(value) + " ₫"
fun v3Date(value: Long): String = SimpleDateFormat("dd/MM/yyyy", vi).format(value)
fun v3DateTime(value: Long): String = SimpleDateFormat("dd/MM HH:mm", vi).format(value)
fun v3Month(value: Long): String = SimpleDateFormat("'Tháng' MM/yyyy", vi).format(value)
fun v3ParseMoney(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L
fun v3ParseDate(value: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", vi).apply { isLenient = false }.parse(value)?.time
}.getOrNull()

fun hiddenMoney(value: Long, hidden: Boolean): String = if (hidden) "•••••• ₫" else v3Money(value)
