package app.undo.capture

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

object AppInfo {
    private val labels = ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String?): String? {
        if (pkg == null) return null
        labels[pkg]?.let { return it }
        val label = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: app.undo.engine.AppCatalog.appName(pkg)
        if (label != null) labels[pkg] = label
        return label
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun icon(ctx: Context, pkg: String?): Drawable? = pkg?.let { runCatching { ctx.packageManager.getApplicationIcon(it) }.getOrNull() }

    @Suppress("DEPRECATION")
    fun installer(ctx: Context, pkg: String): String? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(pkg).installingPackageName
        else ctx.packageManager.getInstallerPackageName(pkg)
    }.getOrNull()
}
