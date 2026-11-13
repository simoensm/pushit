package com.pushitup.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pushitup.MainActivity
import com.pushitup.R
import org.json.JSONArray

// ── Color tokens ──────────────────────────────────────────────────────────────

private val WidgetBg      = ColorProvider(Color(0xFF0D0D0F))
private val WidgetSurface = ColorProvider(Color(0xFF1C1C22))
private val WidgetGreen   = ColorProvider(Color(0xFF22C55E))
private val WidgetGold    = ColorProvider(Color(0xFFFFD700))
private val WidgetSilver  = ColorProvider(Color(0xFFC0C0C0))
private val WidgetBronze  = ColorProvider(Color(0xFFCD7F32))
private val WidgetText    = ColorProvider(Color(0xFFFFFFFF))
private val WidgetMuted   = ColorProvider(Color(0xFF6B7280))
private val WidgetMe      = ColorProvider(Color(0xFF22C55E))
private val WidgetDivider = ColorProvider(Color(0x22FFFFFF))

// ── Breakpoints (width × height) ─────────────────────────────────────────────

private val SMALL  = DpSize(110.dp, 40.dp)   // 2×1 — one row, name + reps only
private val MEDIUM = DpSize(180.dp, 146.dp)  // 3×2 — top 3, compact
private val LARGE  = DpSize(250.dp, 200.dp)  // 4×3 — full layout

/**
 * Glance AppWidget showing today's friends push-up ranking.
 * Data is read from SharedPreferences updated by [FriendsRankingUpdateWorker].
 *
 * Supports three size breakpoints:
 *   Small  (≈ 2×1): leader name + today's reps
 *   Medium (≈ 3×2): top 3 compact rows
 *   Large  (≈ 4×3): full list with header + open-app button
 */
class FriendsRankingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(
            FriendsRankingUpdateWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(FriendsRankingUpdateWorker.KEY_RANKING_JSON, null)

        val entries = parseEntries(json)

        provideContent {
            val size = LocalSize.current
            when {
                size.width  >= LARGE.width  && size.height >= LARGE.height  -> LargeContent(entries)
                size.width  >= MEDIUM.width && size.height >= MEDIUM.height -> MediumContent(entries)
                else                                                         -> SmallContent(entries)
            }
        }
    }

    data class RankEntry(
        val name      : String,
        val todayReps : Int,
        val streak    : Int,
        val isMe      : Boolean,
    )

    private fun parseEntries(json: String?): List<RankEntry> {
        json ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                RankEntry(
                    name      = obj.getString("name"),
                    todayReps = obj.getInt("todayReps"),
                    streak    = obj.getInt("streak"),
                    isMe      = obj.optBoolean("isMe", false),
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

// ── Small layout (≈ 2×1): just the leader ────────────────────────────────────

@Composable
private fun SmallContent(entries: List<FriendsRankingWidget.RankEntry>) {
    val leader = entries.firstOrNull()
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Image(
            provider           = ImageProvider(R.drawable.ic_widget_trophy),
            contentDescription = null,
            modifier           = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        if (leader == null) {
            Text("No data", style = TextStyle(color = WidgetMuted, fontSize = 11.sp))
        } else {
            Text(
                leader.name,
                style = TextStyle(
                    color      = if (leader.isMe) WidgetMe else WidgetText,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "${leader.todayReps} reps",
                style = TextStyle(color = WidgetGold, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

// ── Medium layout (≈ 3×2): top 3, no button ──────────────────────────────────

@Composable
private fun MediumContent(entries: List<FriendsRankingWidget.RankEntry>) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(18.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // Mini header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_trophy),
                    contentDescription = null,
                    modifier           = GlanceModifier.size(12.dp),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    "Today",
                    style = TextStyle(color = WidgetMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            if (entries.isEmpty()) {
                Text("Open app to sync", style = TextStyle(color = WidgetMuted, fontSize = 11.sp))
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    entries.take(3).forEachIndexed { i, entry ->
                        CompactRankRow(position = i + 1, entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRankRow(position: Int, entry: FriendsRankingWidget.RankEntry) {
    val badgeColor = when (position) {
        1 -> WidgetGold; 2 -> WidgetSilver; 3 -> WidgetBronze; else -> WidgetSurface
    }
    Row(
        modifier          = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = GlanceModifier.size(18.dp).background(badgeColor).cornerRadius(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$position", style = TextStyle(color = ColorProvider(Color.Black), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            entry.name,
            modifier = GlanceModifier.defaultWeight(),
            style    = TextStyle(
                color      = if (entry.isMe) WidgetMe else WidgetText,
                fontSize   = 11.sp,
                fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        Text(
            "${entry.todayReps}",
            style = TextStyle(color = WidgetGold, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// ── Large layout (≈ 4×3): full ───────────────────────────────────────────────

@Composable
private fun LargeContent(entries: List<FriendsRankingWidget.RankEntry>) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(20.dp),
    ) {
        Column(
            modifier          = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Header
            Row(
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_trophy),
                    contentDescription = null,
                    modifier           = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "Today's Ranking",
                    style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier         = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No data yet — open the app to sync.",
                        style = TextStyle(color = WidgetMuted, fontSize = 12.sp),
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    entries.forEachIndexed { index, entry ->
                        WidgetRankRow(position = index + 1, entry = entry)
                        if (index < entries.lastIndex) {
                            Spacer(GlanceModifier.height(1.dp).fillMaxWidth().background(WidgetDivider))
                        }
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Open app button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(WidgetGreen)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Open PushIt",
                    style = TextStyle(
                        color      = ColorProvider(Color.Black),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

// ── Full rank row (large layout) ──────────────────────────────────────────────

@Composable
private fun WidgetRankRow(position: Int, entry: FriendsRankingWidget.RankEntry) {
    val badgeColor = when (position) {
        1 -> WidgetGold; 2 -> WidgetSilver; 3 -> WidgetBronze; else -> WidgetSurface
    }
    val nameColor = if (entry.isMe) WidgetMe else WidgetText

    Row(
        modifier          = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = GlanceModifier.size(24.dp).background(badgeColor).cornerRadius(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$position", style = TextStyle(color = ColorProvider(Color.Black), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }

        Spacer(GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                if (entry.isMe) "${entry.name}  ·  you" else entry.name,
                style = TextStyle(
                    color      = nameColor,
                    fontSize   = 13.sp,
                    fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (entry.streak > 0) {
                Text(
                    "🔥 ${entry.streak} day streak",
                    style = TextStyle(color = WidgetMuted, fontSize = 10.sp),
                )
            }
        }

        Text(
            "${entry.todayReps} reps",
            style = TextStyle(color = WidgetGold, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}
