package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Process
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BitmapCachedLoader(
    target: ImageView,
    info: AppData,
    context: Context
) {
    private val targetRef = WeakReference(target)
    private val appInfo = WeakReference(info)
    private val ctx = context.applicationContext
    private val theTag = (target.tag as? Int) ?: -1
    private var iconKey: String = ""

    fun execute() {
        val ad = appInfo.get() ?: return
        iconKey = ad.iconKey
        if (iconKey.isEmpty()) return

        val isLeader = synchronized(inFlightLock) {
            val list = inFlight.getOrPut(iconKey) { mutableListOf() }
            list.add(this)
            list.size == 1
        }
        if (!isLeader) return

        loaderScope.launch(loaderDispatcher) {
            val bitmap = loadBitmap()
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    applyToTarget(bitmap)
                    dispatchToWaiters(bitmap)
                }
            } else {
                releaseWaiters()
            }
        }
    }

    private fun loadBitmap(): Bitmap? {
        var icon: android.graphics.drawable.Drawable? = null

        val ad = appInfo.get() ?: return null
        if (iconKey.isEmpty()) return null

        // If another loader finished while we were queued, return the cached bitmap
        val existing = Helpers.memoryCache.get(iconKey)
        if (existing != null) return existing

        try {
            if (ad.pkgName.isEmpty() && ad.actName.isEmpty()) return null

            val pkgMgr = ctx.packageManager
            if (ad.actName.isNotEmpty() && ad.actName != "-") {
                val component = ComponentName(ad.pkgName, ad.actName)
                try {
                    if (pkgMgr.getActivityInfo(component, PackageManager.MATCH_ALL).icon != 0) {
                        icon = pkgMgr.getActivityIcon(component)
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
            }
            if (icon == null && pkgMgr.getApplicationInfo(ad.pkgName, PackageManager.MATCH_DISABLED_COMPONENTS).icon != 0) {
                icon = pkgMgr.getApplicationIcon(ad.pkgName)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load app icon", t)
        }
        if (icon == null) return null

        val newIconSize = ctx.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        val bmp = Bitmap.createBitmap(newIconSize, newIconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        icon.setBounds(0, 0, newIconSize, newIconSize)
        icon.draw(canvas)

        Helpers.memoryCache.put(iconKey, bmp)

        return bmp
    }

    private fun applyToTarget(bmp: Bitmap) {
        val itemIcon = targetRef.get() ?: return
        val tag = itemIcon.tag
        if (tag is Int && theTag == tag && itemIcon.drawable is TransitionDrawable) {
            val crossfader = itemIcon.drawable as TransitionDrawable
            crossfader.addLayer(BitmapDrawable(ctx.resources, bmp))
            crossfader.startTransition(200)
        }
    }

    private fun dispatchToWaiters(bmp: Bitmap) {
        val waiters: List<BitmapCachedLoader>
        synchronized(inFlightLock) {
            waiters = inFlight.remove(iconKey)?.toList() ?: return
        }
        // The leader (this) is in the list; skip it to avoid reapplying to its own view.
        for (loader in waiters) {
            if (loader === this) continue
            loader.applyToTarget(bmp)
        }
    }

    private fun releaseWaiters() {
        synchronized(inFlightLock) {
            inFlight.remove(iconKey)
        }
    }

    companion object {
        private const val TAG = "Pengeek.IconLoader"
        private const val MAX_PENDING_TASKS = 128

        internal fun clampLoaderThreadCount(availableProcessors: Int): Int =
            (availableProcessors / 2).coerceIn(2, 4)

        private val threadCount =
            clampLoaderThreadCount(Runtime.getRuntime().availableProcessors())
        private val threadNumber = AtomicInteger()

        private val executor = ThreadPoolExecutor(
            threadCount, threadCount, 15L, TimeUnit.SECONDS,
            LinkedBlockingQueue(MAX_PENDING_TASKS),
            { runnable ->
                Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    runnable.run()
                }, "Pengeek-IconLoader-${threadNumber.incrementAndGet()}")
            },
            ThreadPoolExecutor.DiscardOldestPolicy()
        ).apply { allowCoreThreadTimeOut(true) }

        private val loaderDispatcher = executor.asCoroutineDispatcher()
        private val loaderScope = CoroutineScope(SupervisorJob())

        private val inFlightLock = Any()
        private val inFlight = mutableMapOf<String, MutableList<BitmapCachedLoader>>()
    }
}
