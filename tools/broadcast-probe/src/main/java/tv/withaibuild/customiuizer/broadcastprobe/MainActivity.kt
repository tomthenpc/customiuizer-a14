package tv.withaibuild.customiuizer.broadcastprobe

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Third-party broadcast probe.
 *
 * This app is signed with the debug keystore, not the CustoMIUIzer module certificate,
 * and does not enable BroadcastOptions identity sharing. Each button attempts to send a
 * high-privilege module broadcast that the real module protects either with a signature
 * permission or with a sender package whitelist. If the protections work, the receiver
 * ignores the broadcast and the device does not perform the requested action.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val logView = TextView(this).apply {
            text = "Tap a button to send a protected broadcast from an untrusted app."
        }
        layout.addView(logView)

        val probes = listOf(
            "FastReboot" to "tv.withaibuild.customiuizer.mods.action.FastReboot",
            "RestartSystemUI" to "tv.withaibuild.customiuizer.mods.action.RestartSystemUI",
            "RestartLauncher" to "tv.withaibuild.customiuizer.mods.action.RestartLauncher",
            "LockDevice" to "tv.withaibuild.customiuizer.mods.action.LockDevice",
            "TakeScreenshot" to "tv.withaibuild.customiuizer.mods.action.TakeScreenshot",
            "ForceClose" to "tv.withaibuild.customiuizer.mods.action.ForceClose",
            "SimulateMenu" to "tv.withaibuild.customiuizer.mods.action.SimulateMenu",
            "FetchCachedDevices" to "tv.withaibuild.customiuizer.mods.action.FetchCachedDevices",
            "PUSHAPPCONFIG" to "tv.withaibuild.customiuizer.mods.event.PUSHAPPCONFIG",
        )

        for ((label, action) in probes) {
            val button = Button(this).apply {
                text = "Send $label"
                setOnClickListener {
                    val intent = Intent(action).apply {
                        setPackage("com.android.systemui")
                        putExtra("DATATYPE", "privacy") // for PUSHAPPCONFIG shape
                    }
                    val result = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            logView.text = "$label result code: $resultCode (ignored or unhandled expected)"
                        }
                    }
                    sendOrderedBroadcast(intent, null, result, null, -1, null, null)
                    logView.text = "Sent $label; no signature/identity, should be rejected."
                }
            }
            layout.addView(button)
        }

        setContentView(layout)
    }
}
