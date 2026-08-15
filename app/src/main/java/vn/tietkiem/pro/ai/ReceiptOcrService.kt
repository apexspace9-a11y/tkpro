package vn.tietkiem.pro.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReceiptScanResult(
    val rawText: String,
    val merchant: String,
    val amount: Long,
    val dateText: String
)

class ReceiptOcrService {
    suspend fun recognize(bitmap: Bitmap): ReceiptScanResult = process(InputImage.fromBitmap(bitmap, 0))

    suspend fun recognize(context: Context, uri: Uri): ReceiptScanResult =
        process(InputImage.fromFilePath(context, uri))

    private suspend fun process(image: InputImage): ReceiptScanResult = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                recognizer.close()
                if (cont.isActive) cont.resume(parse(text))
            }
            .addOnFailureListener { error ->
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(error)
            }
        cont.invokeOnCancellation { recognizer.close() }
    }

    private fun parse(text: String): ReceiptScanResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchant = lines.firstOrNull { line -> line.any(Char::isLetter) && line.length in 2..80 }.orEmpty()
        val dateRegex = Regex("\\b(\\d{1,2}[/-]\\d{1,2}[/-](?:\\d{2}|\\d{4}))\\b")
        val date = dateRegex.find(text)?.groupValues?.getOrNull(1).orEmpty()

        val moneyRegex = Regex("(?<!\\d)(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{4,12})(?:\\s?(?:đ|₫|vnd))?", RegexOption.IGNORE_CASE)
        val candidates = moneyRegex.findAll(text).mapNotNull { match ->
            match.groupValues[1].replace(Regex("[^0-9]"), "").toLongOrNull()
        }.filter { it in 1_000L..5_000_000_000L }.toList()
        val amount = candidates.maxOrNull() ?: 0L

        return ReceiptScanResult(
            rawText = text,
            merchant = merchant,
            amount = amount,
            dateText = date
        )
    }
}
