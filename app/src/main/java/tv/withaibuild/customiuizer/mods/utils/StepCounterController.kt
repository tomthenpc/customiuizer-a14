package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Control-center step counter.
 *
 * - Views are held as weak references and expire naturally when the header is
 *   destroyed, so a detached view never prevents garbage collection.
 * - TIME_TICK receiver is registered only when at least one view is alive and
 *   the screen is on, and unregistered when the last view dies or the screen
 *   turns off.
 * - Screen on/off is observed through [ScreenStateController] instead of
 *   polling [PowerManager.isInteractive] on every tick.
 * - Step queries are single-flight: a new tick is skipped while a query is
 *   already in flight, preventing parallel ContentProvider requests.
 */
object StepCounterController : ScreenStateController.ScreenStateListener {

    private val stepViewList = ArrayList<WeakReference<TextView>>(2)
    private var context: Context? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var pendingUpdateJob: Job? = null
    private var timeTickRegistered = false

    private var scope: CoroutineScope = newScope()
    private val queryMutex = Mutex()

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onScreenStateChanged(isOn: Boolean) {
        val ctx = context ?: return
        if (isOn) {
            if (hasActiveViews()) {
                ensureTickRegistered(ctx)
                scope.launch { refreshSteps(ctx) }
            }
        } else {
            pendingUpdateJob?.cancel()
            pendingUpdateJob = null
            unregisterTick(ctx)
        }
    }

    @JvmStatic
    fun removeStepViewByTag(tag: String) {
        cleanupDeadViews()
        stepViewList.removeAll { it.get()?.tag == tag }
        if (stepViewList.isEmpty()) {
            pendingUpdateJob?.cancel()
            pendingUpdateJob = null
            context?.let { unregisterTick(it) }
        }
    }

    @JvmStatic
    fun addStepView(sv: TextView) {
        cleanupDeadViews()
        stepViewList.add(WeakReference(sv))

        val ctx = context ?: return
        if (ScreenStateController.isScreenOn()) {
            ensureTickRegistered(ctx)
        }

        pendingUpdateJob?.cancel()
        pendingUpdateJob = scope.launch {
            delay(3000L)
            if (ScreenStateController.isScreenOn()) refreshSteps(ctx)
        }
    }

    @JvmStatic
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun initContext(context: Context) {
        // Cancel any pending work from a previous context and recreate the scope.
        scope.cancel()
        scope = newScope()
        pendingUpdateJob?.cancel()
        pendingUpdateJob = null

        val oldContext = this.context
        oldContext?.let { unregisterTick(it) }

        this.context = context
        ScreenStateController.addListener(context, this)

        // Receiver will be registered when the first view appears while the
        // screen is on; do not pay for TIME_TICK while no one is observing.
        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (!ScreenStateController.isScreenOn()) return@guarded
                scope.launch { refreshSteps(context) }
            }
        }
    }

    private fun hasActiveViews(): Boolean {
        cleanupDeadViews()
        return stepViewList.isNotEmpty()
    }

    private fun cleanupDeadViews() {
        stepViewList.removeAll { it.get() == null }
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

    private suspend fun refreshSteps(context: Context) {
        if (!hasActiveViews()) return

        val newText = withContext(Dispatchers.IO) {
            queryMutex.withLock {
                queryStepProvider(context)
            }
        } ?: return

        if (newText == stepsWithGoal) return
        stepsWithGoal = newText

        cleanupDeadViews()
        for (ref in stepViewList) {
            ref.get()?.text = newText
        }
    }

    private fun queryStepProvider(context: Context): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                Uri.parse("content://com.miui.health.provider.main/activity/steps/brief"),
                arrayOf("steps", "goal"),
                null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val stepCount = cursor.getString(0)
                val stepGoal = cursor.getString(1)
                "$stepCount/$stepGoal"
            } else {
                null
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            null
        } finally {
            cursor?.close()
        }
    }

    private var stepsWithGoal: String? = null
}
