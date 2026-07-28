package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Weather data cache for the status bar clock.
 *
 * - The context and update runnable are held as weak references so a destroyed
 *   clock controller does not keep SystemUI objects alive.
 * - The ContentProvider query is protected by a Mutex so ticks do not pile up
 *   while a query is already in flight.
 * - TIME_TICK is only registered while the screen is on; screen off stops both
 *   the receiver and any delayed refresh job.
 * - A missed force refresh is remembered and executed when the screen turns
 *   back on.
 */
object WeatherDataController : ScreenStateController.ScreenStateListener {

    @JvmField
    var weatherInfo: String = ""

    private var weakReferenceContext: WeakReference<Context>? = null
    private var weakReferenceRunnable: Runnable? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var context: Context? = null

    private var controllerScope = newScope()
    private val queryMutex = Mutex()
    private var timeTickRegistered = false
    private var pendingForceRefresh = false

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onScreenStateChanged(isOn: Boolean) {
        val ctx = context ?: return
        if (isOn) {
            ensureTickRegistered(ctx)
            if (pendingForceRefresh) {
                pendingForceRefresh = false
                refreshWeatherData(true)
            }
        } else {
            controllerScope.cancel()
            controllerScope = newScope()
            unregisterTick(ctx)
        }
    }

    private fun queryWeather() {
        val ctx = weakReferenceContext?.get() ?: return

        var cursor: Cursor? = null
        try {
            cursor = ctx.contentResolver.query(
                Uri.parse("content://weather/actualWeatherData/1"),
                null, null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                var newWeather = ""
                var columnIndex = cursor.getColumnIndex("description")
                if (columnIndex >= 0) {
                    newWeather = cursor.getString(columnIndex)
                }
                columnIndex = cursor.getColumnIndex("temperature")
                if (columnIndex >= 0) {
                    newWeather += (" " + cursor.getString(columnIndex))
                }
                weatherInfo = newWeather
            }
        } catch (ignored: Throwable) {
        } finally {
            cursor?.close()
        }
    }

    @JvmStatic
    fun refreshWeatherData(forceRefresh: Boolean) {
        if (!ScreenStateController.isScreenOn()) {
            if (forceRefresh) pendingForceRefresh = true
            return
        }

        if (forceRefresh) pendingForceRefresh = false

        controllerScope.launch {
            withContext(Dispatchers.IO) {
                queryMutex.withLock { queryWeather() }
            }
            if (forceRefresh) {
                weakReferenceRunnable?.run()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @JvmStatic
    fun initContext(context: Context, updateTimeRunnable: Runnable) {
        // Cancel any pending work from a previous context and start fresh.
        controllerScope.cancel()
        controllerScope = newScope()
        pendingForceRefresh = false

        val oldContext = this.context
        oldContext?.let { unregisterTick(it) }

        weakReferenceContext = WeakReference(context)
        weakReferenceRunnable = updateTimeRunnable
        this.context = context

        ScreenStateController.addListener(context, this)

        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (!ScreenStateController.isScreenOn()) return@guarded
                refreshWeatherData(false)
            }
        }

        if (ScreenStateController.isScreenOn()) {
            ensureTickRegistered(context)
            controllerScope.launch {
                delay(1800)
                if (ScreenStateController.isScreenOn()) refreshWeatherData(true)
            }
        } else {
            // No need to query while the screen is off.
            pendingForceRefresh = true
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun ensureTickRegistered(ctx: Context) {
        if (timeTickRegistered || timeTickReceiver == null) return
        try {
            ctx.registerReceiver(
                timeTickReceiver,
                IntentFilter("android.intent.action.TIME_TICK"),
                Context.RECEIVER_NOT_EXPORTED
            )
            timeTickRegistered = true
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    private fun unregisterTick(ctx: Context) {
        if (!timeTickRegistered) return
        timeTickRegistered = false
        val receiver = timeTickReceiver ?: return
        try {
            ctx.unregisterReceiver(receiver)
        } catch (ignored: Throwable) {
        }
    }
}
