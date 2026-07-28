package tv.withaibuild.customiuizer.mods.utils

import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.os.Build
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.ref.WeakReference
import kotlin.math.sqrt

/**
 * Lock-screen album art processor.
 *
 * - Heavy blur / scale / grayscale work is moved off the main thread into a
 *   single cancellable coroutine pipeline. A new request cancels the previous
 *   one, and only the latest request's result is published.
 * - The input is downsampled before the CPU blur is applied, so fastBlur works
 *   on at most 0.25 MP instead of the original cover resolution.
 * - Processing is skipped while the screen is off/AOD; the raw source is
 *   cached and processed when the screen turns on.
 * - Generated bitmaps are sized to the target View, use CENTER_CROP by default,
 *   and cached/deduplicated by source identity, processing parameters, and
 *   target dimensions.
 */
object LockScreenAlbumArtController {

    private const val BLUR_MAX_PIXELS = 512 * 512
    private const val CACHE_MAX_ENTRIES = 3

    private data class CacheKey(
        val sourceHash: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val blur: Int,
        val rescale: Int,
        val grayscale: Boolean,
        val targetWidth: Int,
        val targetHeight: Int
    )

    private val albumArtCache = LruCache<CacheKey, Bitmap>(CACHE_MAX_ENTRIES)

    private var miuiThemeUtilsClass: Class<*>? = null
    private var lastContextRef: WeakReference<Context>? = null
    private var lastViewRef: WeakReference<View>? = null
    private var isAod = false

    private var pendingSource: Bitmap? = null
    private var pendingBlur: Int = 0
    private var pendingRescale: Int = 1
    private var pendingGrayscale: Boolean = false

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var generationJob: Job? = null

    @JvmStatic
    fun setMiuiThemeUtilsClass(cls: Class<*>) {
        miuiThemeUtilsClass = cls
    }

    @JvmStatic
    fun setAod(aod: Boolean) {
        if (isAod == aod) return
        isAod = aod
        if (!isAod) processPending()
    }

    @JvmStatic
    fun updateMediaMetaData(context: Context, art: Bitmap?, blur: Int, rescale: Int, grayscale: Boolean) {
        lastContextRef = WeakReference(context)
        val cls = miuiThemeUtilsClass ?: return

        pendingSource = art
        pendingBlur = blur
        pendingRescale = rescale
        pendingGrayscale = grayscale

        val previousSource = XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap?
        if (art === previousSource && art != null) return
        if (art == null && previousSource == null) return

        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArtSource", art)
        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", null)

        if (art == null) {
            sendUpdateBroadcast(context)
            return
        }

        if (isAod || !ScreenStateController.isScreenOn()) {
            // keep the raw source; process once the lock screen is visible
            return
        }

        val view = lastViewRef?.get()
        val targetW = view?.width ?: 0
        val targetH = view?.height ?: 0
        generate(context, art, blur, rescale, grayscale, targetW, targetH)
    }

    @JvmStatic
    fun applyTo(view: View): Boolean {
        lastViewRef = WeakReference(view)
        val processed = getStaticAlbumArt()
        return if (processed != null && processed.width == view.width && processed.height == view.height && view.width > 0 && view.height > 0) {
            setViewBackground(view, processed)
            true
        } else if (pendingSource != null && view.width > 0 && view.height > 0) {
            val ctx = lastContextRef?.get() ?: view.context
            generate(ctx, pendingSource!!, pendingBlur, pendingRescale, pendingGrayscale, view.width, view.height)
            false
        } else {
            false
        }
    }

    private fun setViewBackground(view: View, bitmap: Bitmap) {
        view.background = android.graphics.drawable.BitmapDrawable(view.resources, bitmap)
        view.visibility = View.VISIBLE
    }

    private fun getStaticAlbumArt(): Bitmap? {
        val cls = miuiThemeUtilsClass ?: return null
        return try {
            XposedHelpers.getAdditionalStaticField(cls, "mAlbumArt") as Bitmap?
        } catch (_: Throwable) {
            null
        }
    }

    private fun getStaticSource(): Bitmap? {
        val cls = miuiThemeUtilsClass ?: return null
        return try {
            XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap?
        } catch (_: Throwable) {
            null
        }
    }

    private fun processPending() {
        val source = getStaticSource() ?: return
        pendingSource = source
        val context = lastContextRef?.get() ?: return
        val view = lastViewRef?.get()
        val targetW = view?.width ?: 0
        val targetH = view?.height ?: 0
        generate(context, source, pendingBlur, pendingRescale, pendingGrayscale, targetW, targetH)
    }

    private fun generate(context: Context, art: Bitmap, blur: Int, rescale: Int, grayscale: Boolean, targetWidth: Int, targetHeight: Int) {
        generationJob?.cancel()
        generationJob = controllerScope.launch {
            val processed = withContext(Dispatchers.Default) {
                process(context, art, blur, rescale, grayscale, targetWidth, targetHeight)
            }
            if (!isActive) return@launch
            val wallpaperColors = if (processed != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                withContext(Dispatchers.Default) {
                    try { WallpaperColors.fromBitmap(processed) } catch (_: Throwable) { null }
                }
            } else null
            withContext(Dispatchers.Main) {
                applyResult(context, processed, wallpaperColors)
            }
        }
    }

    private fun process(context: Context, art: Bitmap, blur: Int, rescale: Int, grayscale: Boolean, targetWidth: Int, targetHeight: Int): Bitmap? {
        val blurred = if (blur > 0) blurArt(art, blur) else art
        val width = if (targetWidth > 0) targetWidth else getDisplayWidth(context)
        val height = if (targetHeight > 0) targetHeight else getDisplayHeight(context)
        return processAlbumArt(blurred, rescale, grayscale, width, height)
    }

    private fun blurArt(art: Bitmap, blur: Int): Bitmap? {
        val small = downsampleForBlur(art, BLUR_MAX_PIXELS)
        return Helpers.fastBlur(small, blur + 1) ?: small
    }

    private fun downsampleForBlur(art: Bitmap, maxPixels: Int): Bitmap {
        val pixels = art.width * art.height
        if (pixels <= maxPixels) return art
        val ratio = sqrt(maxPixels.toFloat() / pixels)
        val w = (art.width * ratio).toInt().coerceAtLeast(1)
        val h = (art.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(art, w, h, true)
    }

    private fun getDisplayWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager? ?: return 1080
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.x
    }

    private fun getDisplayHeight(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager? ?: return 1920
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.y
    }

    private fun processAlbumArt(bitmap: Bitmap?, rescale: Int, grayscale: Boolean, width: Int, height: Int): Bitmap? {
        if (bitmap == null) return null
        if (width <= 0 || height <= 0) return bitmap

        val key = CacheKey(
            System.identityHashCode(bitmap),
            bitmap.width,
            bitmap.height,
            0,
            rescale,
            grayscale,
            width,
            height
        )
        albumArtCache.get(key)?.let { return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val transformation = Matrix()

        if (grayscale) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }

        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()
        // Default (rescale == 1) and cover (rescale == 3) use CENTER_CROP.
        val scale = if (rescale == 2) {
            Math.min(width / originalWidth, height / originalHeight)
        } else {
            Math.max(width / originalWidth, height / originalHeight)
        }
        val xTranslation = (width - originalWidth * scale) / 2.0f
        val yTranslation = (height - originalHeight * scale) / 2.0f
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(scale, scale)

        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processed)
        canvas.drawBitmap(bitmap, transformation, paint)
        albumArtCache.put(key, processed)
        return processed
    }

    private fun applyResult(context: Context, processed: Bitmap?, wallpaperColors: WallpaperColors?) {
        val cls = miuiThemeUtilsClass ?: return
        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", processed)
        sendUpdateBroadcast(context)

        if (processed != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wallpaperColors != null) {
            val updateFakeWallpaper = Intent("miui.intent.action.LOCK_WALLPAPER_CHANGED")
            updateFakeWallpaper.setPackage("com.android.systemui")
            val isWallpaperColorLight = (wallpaperColors.colorHints and 1) == 1
            updateFakeWallpaper.putExtra("is_wallpaper_color_light", isWallpaperColorLight)
            context.sendBroadcast(updateFakeWallpaper)
        }
    }

    private fun sendUpdateBroadcast(context: Context) {
        val updateAlbumWallpaper = Intent(GlobalActions.EVENT_PREFIX + "UPDATE_LS_ALBUM_ART")
        updateAlbumWallpaper.setPackage("com.android.systemui")
        context.sendBroadcast(updateAlbumWallpaper)
    }
}
