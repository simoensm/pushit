package com.pushitup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Reusable circular avatar — shows [photoUrl] via Coil when available,
 * falls back to the first letter of [displayName].
 *
 * @param photoUrl       Raw or cache-busted photo URL (pass [UserProfile.photoDisplayUrl]).
 * @param displayName    Used for the initial fallback and content description.
 * @param size           Diameter of the circle.
 * @param tierColor      Border + initial text colour (defaults to accent-green-ish grey).
 * @param borderWidth    Thickness of the ring around the circle.
 * @param cacheKey       Optional Coil memory/disk cache key; set to "uid_timestamp" to
 *                       bust the cache after a new upload.
 */
@Composable
fun UserAvatar(
    photoUrl    : String?,
    displayName : String,
    size        : Dp,
    tierColor   : Color,
    modifier    : Modifier    = Modifier,
    borderWidth : Dp          = 1.5.dp,
    cacheKey    : String?     = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (photoUrl.isNullOrBlank()) {
                    // Gradient background for letter avatar
                    Modifier.background(
                        Brush.radialGradient(
                            listOf(
                                tierColor.copy(alpha = 0.45f),
                                tierColor.copy(alpha = 0.18f),
                            )
                        )
                    )
                } else {
                    Modifier.background(tierColor.copy(alpha = 0.15f))
                }
            )
            .border(borderWidth, tierColor.copy(alpha = 0.50f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            val req = ImageRequest.Builder(LocalContext.current)
                .data(photoUrl)
                .crossfade(true)
                .apply {
                    if (cacheKey != null) {
                        memoryCacheKey(cacheKey)
                        diskCacheKey(cacheKey)
                    }
                }
                .build()
            AsyncImage(
                model              = req,
                contentDescription = displayName,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            // Letter avatar — size the text proportional to the circle
            val textSize = (size.value * 0.42f).sp
            Text(
                displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize   = textSize,
                fontWeight = FontWeight.Black,
                color      = tierColor,
                letterSpacing = 0.sp,
            )
        }
    }
}
