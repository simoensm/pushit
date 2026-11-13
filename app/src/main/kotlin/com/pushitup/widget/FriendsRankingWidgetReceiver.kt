package com.pushitup.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver entry-point for the Friends Ranking widget.
 *
 * When the launcher adds a new widget instance, [onEnabled] fires (once for the
 * first instance) and we kick off the WorkManager refresh cycle.
 * When all instances are removed, [onDisabled] cancels the periodic work.
 */
class FriendsRankingWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FriendsRankingWidget()

    override fun onEnabled(context: android.content.Context) {
        super.onEnabled(context)
        // Start immediate fetch + recurring 30-min refresh
        FriendsRankingUpdateWorker.enqueue(context)
    }

    override fun onDisabled(context: android.content.Context) {
        super.onDisabled(context)
        // No widget instances left — stop background refreshes
        FriendsRankingUpdateWorker.cancel(context)
    }
}
