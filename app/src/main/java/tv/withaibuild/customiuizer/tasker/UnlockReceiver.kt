package tv.withaibuild.customiuizer.tasker

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)

            // Determine authentication mode.
            val sender = getSentFromPackage()
            val explicitToThisComponent = isExplicitToThisComponent(context, intent)

            when {
                sender != null -> {
                    // Best path: the host shared its identity and must match the Bundle host.
                    if (!UnlockTokenProvider().verifyBundle(context, bundle, sender)) {
                        logLimited("token-mismatch", "UnlockReceiver: rejected, token/host mismatch for sender=$sender")
                        return
                    }
                    forward(context, bundle)
                }
                explicitToThisComponent -> {
                    // Fallback: the broadcast was explicitly targeted to this component.
                    // The per-host token remains the primary secret; the explicit component
                    // only confirms the sender intended to reach this receiver.
                    if (!UnlockTokenProvider().verifyBundle(context, bundle, null)) {
                        logLimited("token-invalid-explicit", "UnlockReceiver: rejected, invalid token for explicit broadcast")
                        return
                    }
                    forward(context, bundle)
                }
                else -> {
                    logLimited("identity-missing", "UnlockReceiver: rejected, sender identity not shared and broadcast is not explicit")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "UnlockReceiver: unexpected error", t)
        }
    }

    private fun forward(context: Context, bundle: Bundle?) {
        val sendIntent = Intent().apply {
            action = UNLOCK_SET_FORCED
            setPackage("com.android.systemui")
            putExtras(bundle ?: Bundle())
        }
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        context.sendBroadcast(sendIntent, null, options)
    }

    private fun isExplicitToThisComponent(context: Context, intent: Intent): Boolean {
        val component = intent.component ?: return false
        return component.packageName == context.packageName &&
                component.className == UnlockReceiver::class.java.name
    }

    private fun logLimited(reason: String, message: String) {
        val now = SystemClock.elapsedRealtime()
        val last = lastLogTimes[reason]
        if (last == null || now - last > LOG_THROTTLE_MS) {
            lastLogTimes[reason] = now
            Log.w(TAG, message)
        }
    }

    companion object {
        private const val TAG = "CustoMIUIzer-UnlockReceiver"
        private const val UNLOCK_SET_FORCED = "tv.withaibuild.customiuizer.mods.action.UnlockSetForced"
        private const val LOG_THROTTLE_MS = 60_000L
        private val lastLogTimes = mutableMapOf<String, Long>()
    }
}
