package com.pushitup.core.ui

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yalantis.ucrop.UCropActivity

/**
 * Thin wrapper around UCropActivity that forces the window to fit system windows
 * (i.e. content does NOT draw behind the status bar).
 *
 * UCrop itself may call WindowCompat.setDecorFitsSystemWindows(window, false) internally
 * or inherit edge-to-edge from the system, which pushes the toolbar title and action icons
 * behind the status bar. Overriding in our own subclass (called before super.onCreate)
 * wins that race and keeps the toolbar fully visible.
 */
class PushItCropActivity : UCropActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Clear translucent flag before AppCompat touches the window.
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        super.onCreate(savedInstanceState)

        // Called AFTER super — this overwrites any edge-to-edge mode that AppCompat
        // or UCrop enabled during their own onCreate (the common failure point).
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Solid status bar so the toolbar is visible below it, not behind it.
        window.statusBarColor     = android.graphics.Color.parseColor("#141416")
        window.navigationBarColor = android.graphics.Color.parseColor("#0D0D0F")

        // Ensure light icons aren't used on our dark bars.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars     = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply after any system might have reset the flag.
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }
}
