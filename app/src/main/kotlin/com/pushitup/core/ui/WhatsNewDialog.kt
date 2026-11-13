package com.pushitup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pushitup.core.theme.*
import com.pushitup.core.update.AppChangelog

@Composable
fun WhatsNewDialog(
    changelog : AppChangelog.VersionChangelog,
    onDismiss : () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(SurfaceGlass, RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint     = AccentGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "What's New",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = TextPrimary,
                    )
                    Text(
                        "Version ${changelog.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(16.dp))

            // Changelog sections — scrollable if many entries
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                changelog.sections.forEach { section ->
                    ChangelogSection(section)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Text("Got it!", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun ChangelogSection(section: AppChangelog.Section) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (icon, tint) = sectionIcon(section.title)
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                section.title,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                fontSize   = 13.sp,
                letterSpacing = 0.5.sp,
            )
        }
        section.items.forEach { item ->
            Row(
                modifier              = Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("•", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(item, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun sectionIcon(title: String): Pair<ImageVector, Color> = when {
    title.contains("feature", ignoreCase = true) ||
    title.contains("new",     ignoreCase = true)  -> Icons.Rounded.AutoAwesome to Color(0xFF3B82F6)
    title.contains("fix",     ignoreCase = true) ||
    title.contains("bug",     ignoreCase = true)  -> Icons.Rounded.BugReport    to Color(0xFFEF4444)
    title.contains("improve", ignoreCase = true) ||
    title.contains("enhance", ignoreCase = true)  -> Icons.Rounded.Bolt         to Color(0xFFF59E0B)
    else                                           -> Icons.Rounded.CheckCircle  to Color(0xFF1DB954)
}
