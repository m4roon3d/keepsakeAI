package com.keepsakeai.app.ui.journal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_SECONDS = 180

private fun newRecorder(context: Context, file: File): MediaRecorder {
    @Suppress("DEPRECATION")
    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
    r.setAudioSource(MediaRecorder.AudioSource.MIC)
    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    r.setAudioChannels(1)
    r.setAudioSamplingRate(32000)
    r.setAudioEncodingBitRate(48000)
    r.setOutputFile(file.absolutePath)
    r.prepare()
    return r
}

/** Talk for a minute; Gemini transcribes (any language mix) and trove keeps what matters. */
@Composable
fun VoiceJournalScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val state by viewModel.journalState.collectAsState()
    val transcript by viewModel.journalTranscript.collectAsState()

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val recording = recorder != null

    fun start() {
        val f = File(File(context.filesDir, "journal").apply { mkdirs() }, "j_${System.currentTimeMillis()}.m4a")
        runCatching { newRecorder(context, f).also { it.start() } }.onSuccess { recorder = it; file = f; seconds = 0; startedAt = System.currentTimeMillis() }
    }

    fun stop(save: Boolean) {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        val f = file
        if (save && f != null) viewModel.saveJournal(f) else f?.delete()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (ok) start()
    }

    // Timer + live level while recording
    LaunchedEffect(recording) {
        while (recorder != null) {
            delay(100)
            level = ((recorder?.maxAmplitude ?: 0) / 12000f).coerceIn(0f, 1f)
            seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            if (seconds >= MAX_SECONDS) stop(save = true)
        }
    }
    DisposableEffect(Unit) {
        onDispose { recorder?.let { runCatching { it.stop() }; it.release() }; recorder = null }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breathe")
    val voice by animateFloatAsState(1f + level * 0.35f, tween(120), label = "voice")

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowHeader(eyebrow = "talk for a minute", title = "voice ", gradientSuffix = "journal", joinSuffix = true,
            leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }) {
            GlassButton("close", onClick = { stop(save = false); viewModel.resetJournal(); onClose() }, icon = Icons.Rounded.Close)
        }
        Spacer(Modifier.height(36.dp))

        when {
            state == "transcribing" -> {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(120.dp), color = Flow.Indigo, strokeWidth = 4.dp)
                }
                Spacer(Modifier.height(20.dp))
                Text("listening back and keeping what matters…", color = c.muted, fontSize = 14.sp)
            }
            state == "saved" -> {
                LogoMark(size = 96.dp)
                Spacer(Modifier.height(12.dp))
                Text("kept ✓", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                Text("facts, people, to-dos and dates were picked up automatically", fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                transcript?.let {
                    GlassCard(Modifier.fillMaxWidth()) { Text(it, fontSize = 14.sp, color = c.text, lineHeight = 20.sp) }
                }
                Spacer(Modifier.height(16.dp))
                GradientButton("done", onClick = { viewModel.resetJournal(); onClose() }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                // Record orb
                Box(
                    Modifier.size(200.dp).scale(if (recording) voice else breathe)
                        .clip(CircleShape).background(Flow.auroraSweep)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            when {
                                recording -> stop(save = true)
                                hasMic -> start()
                                else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(86.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) "Stop" else "Record", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    if (recording) String.format("%d:%02d", seconds / 60, seconds % 60) else "tap to start",
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.text
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (recording) "tap the orb when you're done · up to 3 minutes"
                    else "How was your day? Who did you meet, what's on your mind, what do you need to do? Any language is fine.",
                    fontSize = 13.sp, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)
                )
                if (recording) {
                    Spacer(Modifier.height(18.dp))
                    Text("discard", color = Flow.Rose, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { stop(save = false) }.padding(10.dp))
                }
                if (state.startsWith("failed:")) {
                    Spacer(Modifier.height(16.dp))
                    Text(state.removePrefix("failed:"), color = Flow.Rose, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
