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
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.MainModule
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
 */
object LockScreenAlbumArtController {

    private const val BLUR_MAX_PIXELS = 512 * 512

    private var miuiThemeUtilsClass: Class<*>? = null
    private var lastContextRef: WeakReference<Context>? = null
    private var isAod = false

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

        val previousSource = XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap?
        if (art === previousSource) return
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

        generate(context, art, blur, rescale, grayscale)
    }

    private fun processPending() {
        val cls = miuiThemeUtilsClass ?: return
        val art = XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap? ?: return
        val context = lastContextRef?.get() ?: return

        val blur = MainModule.mPrefs.getInt("system_albumartonlock_blur", 0)
        val rescale = MainModule.mPrefs.getStringAsInt("system_albumartonlock_scale", 1)
        val grayscale = MainModule.mPrefs.getBoolean("system_albumartonlock_gray")

        generate(context, art, blur, rescale, grayscale)
    }

    private fun generate(context: Context, art: Bitmap, blur: Int, rescale: Int, grayscale: Boolean) {
        generationJob?.cancel()
        generationJob = controllerScope.launch {
            val processed = withContext(Dispatchers.Default) {
                process(art, blur, rescale, grayscale, context)
            }
            if (!isActive) return@launch
            withContext(Dispatchers.Main) {
                applyResult(context, processed)
            }
        }
    }

    private fun process(art: Bitmap, blur: Int, rescale: Int, grayscale: Boolean, context: Context): Bitmap? {
        val blurred = if (blur > 0) blurArt(art, blur) else art
        return processAlbumArt(context, blurred, rescale, grayscale)
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

    private fun processAlbumArt(context: Context?, bitmap: Bitmap?, rescale: Int, grayscale: Boolean): Bitmap? {
        if (context == null || bitmap == null) return bitmap
        if (rescale == 1 && !grayscale) return bitmap

        val paint = Paint().apply { isFilterBitmap = true }
        val transformation = Matrix()

        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val point = Point()
        display.getRealSize(point)
        val width = point.x
        val height = point.y

        if (grayscale) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }

        if (rescale != 1) {
            val originalWidth = bitmap.width.toFloat()
            val originalHeight = bitmap.height.toFloat()
            val scale = if (rescale == 2) {
                Math.min(width / originalWidth, height / originalHeight)
            } else {
                Math.max(width / originalWidth, height / originalHeight)
            }
            val xTranslation = (width - originalWidth * scale) / 2.0f
            val yTranslation = (height - originalHeight * scale) / 2.0f
            transformation.postTranslate(xTranslation, yTranslation)
            transformation.preScale(scale, scale)
        }

        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processed)
        canvas.drawBitmap(bitmap, transformation, paint)
        return processed
    }

    private fun applyResult(context: Context, processed: Bitmap?) {
        val cls = miuiThemeUtilsClass ?: return
        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", processed)
        sendUpdateBroadcast(context)

        if (processed != null) {
            val updateFakeWallpaper = Intent("miui.intent.action.LOCK_WALLPAPER_CHANGED")
            updateFakeWallpaper.setPackage("com.android.systemui")
            val fromBitmap = WallpaperColors.fromBitmap(processed)
            val isWallpaperColorLight = (fromBitmap.colorHints and 1) == 1
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
