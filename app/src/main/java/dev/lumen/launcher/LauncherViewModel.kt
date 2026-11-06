package dev.lumen.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DOCK_SIZE = 4
        const val ICON_DP = 56
    }

    private val context: Context = application
    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)
    private val prefs = application.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var homeKeys by mutableStateOf(readList("home"))
        private set

    var dockKeys by mutableStateOf(readList("dock"))
        private set

    var homeSignal by mutableIntStateOf(0)
        private set

    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    val homeApps: List<AppEntry> by derivedStateOf { resolve(homeKeys) }
    val dockApps: List<AppEntry> by derivedStateOf { resolve(dockKeys) }

    private var refreshJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh(delayMs = 400)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(packageReceiver) }
    }

    // ---- events -----------------------------------------------------------

    fun onHomePressed() {
        homeSignal++
    }

    fun updateTime() {
        now = System.currentTimeMillis()
    }

    // ---- layout editing ---------------------------------------------------

    fun addToHome(app: AppEntry) {
        if (app.key in homeKeys) return
        dockKeys = dockKeys - app.key
        homeKeys = homeKeys + app.key
        save("dock", dockKeys)
        save("home", homeKeys)
    }

    fun removeFromHome(app: AppEntry) {
        homeKeys = homeKeys - app.key
        save("home", homeKeys)
    }

    fun addToDock(app: AppEntry) {
        if (app.key in dockKeys || dockKeys.size >= DOCK_SIZE) return
        homeKeys = homeKeys - app.key
        dockKeys = dockKeys + app.key
        save("home", homeKeys)
        save("dock", dockKeys)
    }

    fun removeFromDock(app: AppEntry) {
        dockKeys = dockKeys - app.key
        save("dock", dockKeys)
    }

    // ---- loading ----------------------------------------------------------

    private fun refresh(delayMs: Long = 0L) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val list = withContext(Dispatchers.Default) { loadApps() }
            apps = list
            ensureDefaults(list)
        }
    }

    private fun loadApps(): List<AppEntry> {
        val px = (ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { info ->
                AppEntry(
                    key = info.componentName.flattenToString(),
                    label = info.label?.toString().orEmpty(),
                    component = info.componentName,
                    icon = info.getIcon(0).toIconBitmap(px).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun resolve(keys: List<String>): List<AppEntry> {
        val map = apps.associateBy { it.key }
        return keys.mapNotNull { map[it] }
    }

    /** First run: dock = default phone/messages/browser/camera, home = first few apps. */
    private fun ensureDefaults(list: List<AppEntry>) {
        if (list.isEmpty()) return
        if (!prefs.contains("dock")) {
            val picks = defaultDockPackages()
                .mapNotNull { pkg -> list.firstOrNull { it.component.packageName == pkg } }
                .distinctBy { it.key }
                .take(DOCK_SIZE)
            dockKeys = picks.map { it.key }
            save("dock", dockKeys)
        }
        if (!prefs.contains("home")) {
            val docked = dockKeys.toSet()
            homeKeys = list.filter { it.key !in docked }.take(8).map { it.key }
            save("home", homeKeys)
        }
    }

    private fun defaultDockPackages(): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" }
    }

    // ---- persistence ------------------------------------------------------

    private fun readList(name: String): List<String> =
        prefs.getString(name, null)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun save(name: String, list: List<String>) {
        prefs.edit().putString(name, list.joinToString(",")).apply()
    }
}

/**
 * Renders any icon as a uniform rounded-square ("squircle-ish") bitmap, so every
 * app looks consistent regardless of the app's own adaptive-icon mask. Pre-masking here
 * means Compose just blits a bitmap, with no per-frame clipping while scrolling.
 */
internal fun Drawable.toIconBitmap(size: Int): Bitmap {
    val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val srcCanvas = Canvas(src)

    val adaptive = this as? AdaptiveIconDrawable
    if (adaptive != null) {
        // Layers are 108dp with a 72dp visible window; extend bounds so the visible window fills the bitmap.
        val pad = (size * 0.25f).toInt()
        adaptive.background?.let {
            it.setBounds(-pad, -pad, size + pad, size + pad)
            it.draw(srcCanvas)
        }
        adaptive.foreground?.let {
            it.setBounds(-pad, -pad, size + pad, size + pad)
            it.draw(srcCanvas)
        }
    } else {
        srcCanvas.drawColor(0xFFF2F3F5.toInt())
        val inset = (size * 0.17f).toInt()
        setBounds(inset, inset, size - inset, size - inset)
        draw(srcCanvas)
    }

    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(out)
    val radius = size * 0.29f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    outCanvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    outCanvas.drawBitmap(src, 0f, 0f, paint)
    src.recycle()
    return out
}
