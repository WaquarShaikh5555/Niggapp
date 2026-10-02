package app.undo.capture

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.undo.engine.SettingWords

/**
 * Every way UNDO hands off to another screen. All intents are explicit about what they do; nothing
 * here sends a message, places a call or changes anything without you pressing the final button.
 */
object Launch {
    private fun start(ctx: Context, intent: Intent): Boolean = try {
        if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun app(ctx: Context, pkg: String?): Boolean {
        if (pkg == null) return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(ctx, intent)
    }

    fun url(ctx: Context, url: String, preferPackage: String? = null): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https") return false // only ever open https links
        if (preferPackage != null) {
            if (start(ctx, Intent(Intent.ACTION_VIEW, uri).setPackage(preferPackage))) return true
        }
        return start(ctx, Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    fun playStore(ctx: Context, pkg: String): Boolean =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage("com.android.vending")) ||
            url(ctx, "https://play.google.com/store/apps/details?id=$pkg")

    fun playSubscriptions(ctx: Context): Boolean =
        url(ctx, "https://play.google.com/store/account/subscriptions", "com.android.vending")

    /** Opens the dialer with the number filled in. You press call. */
    fun dial(ctx: Context, number: String): Boolean = start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun settingsScreen(ctx: Context, id: String?): Boolean {
        val primary: Intent? = when (id) {
            SettingWords.ROTATION, SettingWords.BRIGHTNESS_MODE, SettingWords.TIMEOUT, SettingWords.FONT_SCALE, "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingWords.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            SettingWords.RINGER, "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SettingWords.DND, "dnd" -> Intent("android.settings.ZEN_MODE_SETTINGS")
            "internet" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> null
        }
        return (primary != null && start(ctx, primary)) ||
            (id == SettingWords.DND && start(ctx, Intent(Settings.ACTION_SOUND_SETTINGS))) ||
            start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun notificationHistory(ctx: Context): Boolean =
        start(ctx, Intent("android.settings.NOTIFICATION_HISTORY")) ||
            start(ctx, Intent("android.settings.NOTIFICATION_SETTINGS")) ||
            start(ctx, Intent(Settings.ACTION_SETTINGS))

    fun notificationAccess(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, UndoNotificationListener.component(ctx).flattenToString())
            if (start(ctx, i)) return true
        }
        return start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun usageAccess(ctx: Context): Boolean =
        start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) ||
            start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun writeSettings(ctx: Context): Boolean =
        start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun appDetails(ctx: Context): Boolean =
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun appNotificationSettings(ctx: Context): Boolean =
        start(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))

    /** Opens the system share sheet. You choose the app and press send yourself. */
    fun share(ctx: Context, text: String): Boolean =
        start(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send with…"))

    /** Opens your SMS app with the text pre-filled. Nothing is sent until you tap send. */
    fun sms(ctx: Context, text: String): Boolean =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text))

    fun email(ctx: Context, subject: String, text: String): Boolean =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text))

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Fires another app's original notification action (only while UNDO's process still holds it).
     * The user just tapped a button in UNDO, so UNDO is in the foreground and may lend that.
     */
    fun pendingIntent(ctx: Context, pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
                pi.send(ctx, 0, null, null, null, null, opts)
            } else {
                pi.send()
            }
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
