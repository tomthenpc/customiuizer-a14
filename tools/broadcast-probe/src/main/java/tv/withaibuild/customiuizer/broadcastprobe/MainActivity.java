package tv.withaibuild.customiuizer.broadcastprobe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Third-party broadcast probe.
 *
 * This app is signed with the debug keystore, not the CustoMIUIzer module certificate,
 * and does not enable BroadcastOptions identity sharing. Each button attempts to send a
 * high-privilege module broadcast that the real module protects either with a signature
 * permission or with a sender package whitelist. If the protections work, the receiver
 * ignores the broadcast and the device does not perform the requested action.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView logView = new TextView(this);
        logView.setText("Tap a button to send a protected broadcast from an untrusted app.");
        layout.addView(logView);

        List<Map.Entry<String, String>> probes = new ArrayList<>();
        probes.add(new AbstractMap.SimpleEntry<>("FastReboot", "tv.withaibuild.customiuizer.mods.action.FastReboot"));
        probes.add(new AbstractMap.SimpleEntry<>("RestartSystemUI", "tv.withaibuild.customiuizer.mods.action.RestartSystemUI"));
        probes.add(new AbstractMap.SimpleEntry<>("RestartLauncher", "tv.withaibuild.customiuizer.mods.action.RestartLauncher"));
        probes.add(new AbstractMap.SimpleEntry<>("LockDevice", "tv.withaibuild.customiuizer.mods.action.LockDevice"));
        probes.add(new AbstractMap.SimpleEntry<>("TakeScreenshot", "tv.withaibuild.customiuizer.mods.action.TakeScreenshot"));
        probes.add(new AbstractMap.SimpleEntry<>("ForceClose", "tv.withaibuild.customiuizer.mods.action.ForceClose"));
        probes.add(new AbstractMap.SimpleEntry<>("SimulateMenu", "tv.withaibuild.customiuizer.mods.action.SimulateMenu"));
        probes.add(new AbstractMap.SimpleEntry<>("FetchCachedDevices", "tv.withaibuild.customiuizer.mods.action.FetchCachedDevices"));
        probes.add(new AbstractMap.SimpleEntry<>("PUSHAPPCONFIG", "tv.withaibuild.customiuizer.mods.event.PUSHAPPCONFIG"));

        for (final Map.Entry<String, String> probe : probes) {
            Button button = new Button(this);
            button.setText("Send " + probe.getKey());
            button.setOnClickListener(v -> {
                Intent intent = new Intent(probe.getValue());
                intent.setPackage("com.android.systemui");
                intent.putExtra("DATATYPE", "privacy");
                BroadcastReceiver result = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        logView.setText(probe.getKey() + " result code: " + getResultCode() + " (ignored or unhandled expected)");
                    }
                };
                sendOrderedBroadcast(intent, null, result, null, -1, null, null);
                logView.setText("Sent " + probe.getKey() + "; no signature/identity, should be rejected.");
            });
            layout.addView(button);
        }

        setContentView(layout);
    }
}
