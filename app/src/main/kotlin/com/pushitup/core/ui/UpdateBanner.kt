package com.pushitup.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pushitup.core.theme.*

private const val PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.pushitup"

@Composable
fun UpdateBanner(
    versionName : String,
    onDismiss   : () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentGreen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.SystemUpdate,
            contentDescription = null,
            tint     = Color.Black,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Update available — v$versionName",
                fontWeight = FontWeight.Bold,
                color      = Color.Black,
                style      = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Tap to open Play Store",
                color = Color.Black.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.Black),
        ) {
            Text("Update", fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "Dismiss", tint = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp))
        }
    }
}
