package tv.withaibuild.customiuizer.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
        if (bundle == null) {
            XposedHelpers.log("UnlockReceiver: rejected, missing bundle")
            return
        }
        if (!UnlockTokenProvider().verify(context, bundle)) {
            XposedHelpers.log("UnlockReceiver: rejected, missing or invalid token")
            return
        }
        val sendIntent = Intent().apply {
            action = GlobalActions.ACTION_PREFIX + "UnlockSetForced"
            setPackage("com.android.systemui")
            putExtras(bundle)
        }
        ModuleHelper.sendBroadcastWithIdentity(context, sendIntent)
    }
}
