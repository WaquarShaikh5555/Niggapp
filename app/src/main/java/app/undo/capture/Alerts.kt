package app.undo.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.undo.Graph
import app.undo.R
import app.undo.ui.MainActivity

/**
 * Optional, off by default. A quiet nudge when something costly or recoverable just happened.
 * Lock-screen version hides the details.
 */
object Alerts {
    const val CHANNEL = "undo_windows"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Undo opportunities", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A quiet heads-up right after a payment, deletion or uninstall, while it's still easy to fix."
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun permissionGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun maybePost(ctx: Context, tag: String, title: String, text: String) {
        if (!Graph.prefs.current.alerts || Graph.prefs.current.paused) return
        if (!permissionGranted(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_undo)
            .setContentTitle("UNDO")
            .setContentText("Something may be worth a second look")
            .build()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_undo)
            .setColor(0xFFE8553A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setTimeoutAfter(30 * 60_000L)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(tag, 1, n) }
    }
}
