package com.keepsakeai.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.Badge
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SnapMode(val label: String, val prefixPrompt: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DOC("Document", "", Icons.Rounded.Description),
    RECEIPT("Receipt", "Receipt scan: ", Icons.Rounded.ReceiptLong),
    TRANSLATE("Translate", "Translate to English and summarise this text: ", Icons.Rounded.Translate)
}

/** Private folder that photos are kept in, so memory cards can show them later. */
private fun snapsDir(context: Context) = File(context.filesDir, "snaps").apply { mkdirs() }

private fun newSnapFile(context: Context) = File(snapsDir(context), "snap_${System.currentTimeMillis()}.jpg")

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Copies a picked gallery image into trove's own folder (picker access is temporary). */
private fun importToSnaps(context: Context, source: Uri): Uri? = runCatching {
    val dest = newSnapFile(context)
    context.contentResolver.openInputStream(source)?.use { input ->
        dest.outputStream().use { input.copyTo(it) }
    } ?: return null
    Uri.fromFile(dest)
}.getOrNull()

/**
 * Snapsake: point, snap, keep.
 * Opens the phone's own camera app (or the photo picker), reads the text on-device
 * with ML Kit, and saves it to trove together with the photo.
 */
@Composable
fun SnapsakeScreen(
    viewModel: MindSpaceViewModel,
    onSavedNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val scope = rememberCoroutineScope()
    val ocr = (context.applicationContext as MindSpaceApp).ocrProcessor

    var mode by rememberSaveable { mutableStateOf(SnapMode.DOC) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runOcr(uri: Uri) {
        photo = uri.toString()
        reading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ocr.extractTextFromUri(uri) } }
            reading = false
            result.onSuccess {
                text = it.trim()
                if (text.isBlank()) error = "No text found. You can still type a note and save the photo."
            }.onFailure { error = "Couldn't read the photo: ${it.localizedMessage}" }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCameraFile
        pendingCameraFile = null
        if (ok && path != null) runOcr(Uri.fromFile(File(path)))
        else path?.let { File(it).delete() }
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val local = withContext(Dispatchers.IO) { importToSnaps(context, uri) }
                runOcr(local ?: uri)
            }
        }
    }

    fun openCamera() {
        val file = newSnapFile(context)
        pendingCameraFile = file.absolutePath
        try {
            cameraLauncher.launch(uriFor(context, file))
        } catch (e: ActivityNotFoundException) {
            pendingCameraFile = null
            error = "No camera app found on this phone."
        }
    }

    val auto by viewModel.autoCamera.collectAsState()
    androidx.compose.runtime.LaunchedEffect(auto) {
        if (auto) {
            viewModel.autoCamera.value = false
            openCamera()
        }
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlowHeader(
            eyebrow = "point. snap. keep.",
            title = "snap",
            gradientSuffix = "sake",
            joinSuffix = true,
            leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.LENS) }
        ) {
            GlassButton(
                "gallery",
                onClick = { pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = Icons.Rounded.PhotoLibrary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnapMode.entries.forEach { m ->
                Pill(m.label, mode == m, { mode = m }, Modifier.weight(1f), icon = m.icon)
            }
        }

        // Viewfinder panel: the photo once taken, otherwise an animated scan frame.
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Flow.lensGradient)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = "Snapped photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (reading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Flow.Cyan, strokeWidth = 3.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Reading text…", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                ScanFrame(mode)
            }
        }

        GradientButton(
            text = if (photo == null) "Open camera" else "Retake",
            onClick = { openCamera() },
            icon = Icons.Rounded.CameraAlt,
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(it, color = Flow.Rose, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
        }

        if (photo != null && !reading) {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                Text(
                    "TEXT FOUND · edit before saving",
                    style = MaterialTheme.typography.labelSmall,
                    color = Flow.Indigo,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
                )
                GlassField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Add a note about this photo…",
                    singleLine = false,
                    minLines = 4,
                    bare = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            GradientButton(
                text = "Save to trove",
                enabled = text.isNotBlank() || photo != null,
                onClick = {
                    val body = text.ifBlank { "Photo snapped with lens" }
                    viewModel.captureText(mode.prefixPrompt + body, source = CaptureSource.CAMERA_SCAN, photoUri = photo)
                    photo = null
                    text = ""
                    onSavedNavigateHome()
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (photo == null) {
            Text(
                "lens opens your phone's own camera, so you get its full quality, zoom and HDR. Text is read on your phone; only the extracted text goes to your AI for sorting.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ScanFrame(mode: SnapMode) {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "scanY")
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                // Dashed target frame
                drawRoundRect(
                    color = Flow.Cyan.copy(alpha = 0.7f),
                    topLeft = Offset(w * 0.15f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f, 48f),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f)))
                )
                // Glowing scan line
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Flow.Cyan, Color.Transparent)),
                    topLeft = Offset(w * 0.06f, h * y),
                    size = androidx.compose.ui.geometry.Size(w * 0.88f, 5f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CenterFocusWeak, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(mode.label, color = Color(0xFFA5F3FC), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(1.dp))
            Text("Tap Open camera to snap", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}
