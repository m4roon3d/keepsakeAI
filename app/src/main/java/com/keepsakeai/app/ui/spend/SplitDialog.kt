package com.keepsakeai.app.ui.spend

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import com.keepsakeai.app.ui.theme.Display
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ceil

/** Standard UPI deep link; every UPI app understands it when scanned as a QR code. */
fun upiLink(vpa: String, name: String, amount: Double, note: String): String {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    return "upi://pay?pa=${enc(vpa)}&pn=${enc(name.ifBlank { "trove" })}&am=${String.format(Locale.US, "%.2f", amount)}&cu=INR&tn=${enc(note.take(40))}"
}

/** Each person's share, rounded up to the rupee so the payer is never short. */
fun shareOf(total: Double, people: Int): Double = ceil(total / people.coerceAtLeast(1))

private fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (y in 0 until size) for (x in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt())
    }
    return bmp
}

/** QR plus a caption, ready to drop into WhatsApp. */
private fun shareCard(qr: Bitmap, title: String, amountLine: String, vpa: String): Bitmap {
    val w = qr.width + 120
    val h = qr.height + 300
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFFFFFFF.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt(); textAlign = Paint.Align.CENTER }
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textSize = 54f
    c.drawText(amountLine, w / 2f, 95f, p)
    p.typeface = Typeface.DEFAULT; p.textSize = 34f; p.color = 0xFF475569.toInt()
    c.drawText(title, w / 2f, 150f, p)
    c.drawBitmap(qr, 60f, 190f, null)
    p.textSize = 30f
    c.drawText("Scan with any UPI app · $vpa", w / 2f, h - 50f, p)
    return bmp
}

private fun shareImage(context: Context, bmp: Bitmap, text: String) {
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "split_${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Send split request").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SplitDialog(expense: ExpenseEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val prefs = remember { MoneyPrefs(context) }
    var people by remember { mutableIntStateOf(2) }
    var vpa by remember { mutableStateOf(prefs.upiId) }
    var name by remember { mutableStateOf(prefs.payeeName) }
    val share = shareOf(expense.amount, people)
    val note = "${expense.merchant} split"
    val validVpa = Regex("^[\\w.\\-]{2,}@[a-zA-Z]{2,}$").matches(vpa.trim())
    val qr = remember(vpa, name, share, validVpa) { if (validVpa) qrBitmap(upiLink(vpa.trim(), name, share, note), 480) else null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("split ${expense.merchant}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap.m)) {
                Text("₹${String.format(Locale.US, "%,.0f", expense.amount)} between", fontSize = Ts.body, color = c.soft)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("–") { if (people > 2) people-- }
                    Text("$people people", fontSize = Ts.lead, fontFamily = Display, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(horizontal = Gap.l))
                    StepButton("+") { if (people < 20) people++ }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹${String.format(Locale.US, "%,.0f", share)}", fontSize = Ts.heading, fontFamily = Display, fontWeight = FontWeight.ExtraBold, color = Flow.Emerald)
                        Text("each", fontSize = Ts.meta, color = c.muted)
                    }
                }
                GlassField(vpa, { vpa = it }, "Your UPI ID (e.g. name@okhdfcbank)", Modifier.fillMaxWidth())
                GlassField(name, { name = it }, "Your name (shown to payers)", Modifier.fillMaxWidth())
                if (qr != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(qr.asImageBitmap(), "UPI QR for ₹$share", Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)))
                    }
                } else if (vpa.isNotBlank()) {
                    Text("That doesn't look like a UPI ID yet.", fontSize = Ts.meta, color = Flow.Rose)
                }
            }
        },
        confirmButton = {
            GradientButton(
                "send QR",
                enabled = validVpa,
                brush = Flow.spendGradient,
                onClick = {
                    prefs.upiId = vpa; prefs.payeeName = name
                    val link = upiLink(vpa.trim(), name, share, note)
                    val amountLine = "₹${String.format(Locale.US, "%,.0f", share)} each"
                    val card = shareCard(qrBitmap(link), "${expense.merchant} · split $people ways", amountLine, vpa.trim())
                    shareImage(
                        context, card,
                        "Hey! Your share for ${expense.merchant} is ₹${String.format(Locale.US, "%,.0f", share)}. Scan the QR with any UPI app, or pay to ${vpa.trim()}."
                    )
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = {
                if (validVpa) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("UPI link", upiLink(vpa.trim(), name, share, note)))
                    prefs.upiId = vpa; prefs.payeeName = name
                }
                onDismiss()
            }) { Text(if (validVpa) "copy link" else "cancel") }
        }
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val c = LocalFlow.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(c.glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = Ts.lead, fontFamily = Display, fontWeight = FontWeight.Bold, color = c.text) }
}
