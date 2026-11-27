package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import com.keepsakeai.app.ui.theme.Display
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ui.components.AuroraBackground
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.theme.LocalFlow

/** Covers the app until fingerprint, face or screen lock succeeds. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = LocalFlow.current
    AuroraBackground(
        Modifier.fillMaxSize()
            // Swallow touches so nothing underneath can be tapped.
            .clickable(remember { MutableInteractionSource() }, null) { }
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LogoMark(size = 110.dp)
            Spacer(Modifier.height(Gap.l))
            Text("trove is locked", fontSize = Ts.section, fontFamily = Display, fontWeight = FontWeight.ExtraBold, color = c.text)
            Spacer(Modifier.height(Gap.s))
            Text("Your memories and payments stay private.", fontSize = Ts.body, color = c.soft)
            Spacer(Modifier.height(28.dp))
            GradientButton("unlock", onClick = onUnlock, icon = Icons.Rounded.Fingerprint)
        }
    }
}
