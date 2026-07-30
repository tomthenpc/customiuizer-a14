package tv.withaibuild.customiuizer.broadcastprobe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Third-party broadcast probe.
 *
 * This app is signed with the debug keystore, not the CustoMIUIzer module certificate,
 * and does not enable BroadcastOptions identity sharing. It attempts to send every
 * protected module broadcast to its correct target package as documented in
 * docs/BROADCAST_TRUST_MATRIX.md, plus a few negative probes (wrong package,
 * unregistered action, missing identity). If the protections work, the module
 * receivers either ignore the broadcast because the probe does not hold the
 * required signature permission, or they explicitly set a failure result code
 * because the sender package is not whitelisted.
 *
 * Result-code legend (ordered broadcasts only):
 *   100   SENTINEL    - no matching receiver / delivery blocked / not handled
 *   -1    HANDLED     - receiver executed the action (must NOT happen from an untrusted app)
 *    1    FAILED      - receiver received the broadcast but rejected it
 *
 * All probes from this untrusted app should report SENTINEL or FAILED.
 * HANDLED on any high-privilege action indicates a security regression.
 */
public class MainActivity extends Activity {

    private static final int SENTINEL = 100;
    private static final int HANDLED = -1;   // Activity.RESULT_OK
    private static final int FAILED = 1;     // Activity.RESULT_FIRST_USER

    private static final String ACTION_PREFIX = "tv.withaibuild.customiuizer.mods.action.";
    private static final String EVENT_PREFIX = "tv.withaibuild.customiuizer.mods.event.";

    private static class Probe {
        final String label;
        final String action;
        final String targetPackage;
        final Bundle extras;
        final int expectedResult;
        final String expectedText;
        final String reason;

        Probe(String label, String action, String targetPackage, Bundle extras,
              int expectedResult, String expectedText, String reason) {
            this.label = label;
            this.action = action;
            this.targetPackage = targetPackage;
            this.extras = extras;
            this.expectedResult = expectedResult;
            this.expectedText = expectedText;
            this.reason = reason;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView logView = new TextView(this);
        logView.setText("Tap a probe. All results from this untrusted app should be SENTINEL (no receiver / blocked) or FAILED (rejected). HANDLED is a regression.");
        layout.addView(logView);

        List<Probe> probes = new ArrayList<>();

        // Module -> SystemUI, signature permission.
        probes.add(new Probe(
                "FastReboot (correct target)",
                ACTION_PREFIX + "FastReboot",
                "com.android.systemui",
                null,
                SENTINEL,
                "SENTINEL",
                "Receiver exists but the probe does not hold the module BROADCAST signature permission, so the system drops the broadcast before onReceive."
        ));

        // Host -> SystemUI, sender identity whitelist.
        probes.add(new Probe(
                "RestartSystemUI",
                ACTION_PREFIX + "RestartSystemUI",
                "com.android.systemui",
                null,
                FAILED,
                "FAILED",
                "mSBReceiver in SystemUI gets the broadcast but the sender package is not in the whitelist (android, com.android.systemui, com.miui.home, module)."
        ));

        probes.add(new Probe(
                "RestartLauncher",
                ACTION_PREFIX + "RestartLauncher",
                "com.android.systemui",
                null,
                FAILED,
                "FAILED",
                "Same mSBReceiver path; sender is the probe, not a trusted host."
        ));

        probes.add(new Probe(
                "LockDevice",
                ACTION_PREFIX + "LockDevice",
                "com.android.systemui",
                null,
                FAILED,
                "FAILED",
                "mSBReceiver rejects because getSentFromPackage() is the probe, not a whitelisted package."
        ));

        probes.add(new Probe(
                "TakeScreenshot",
                ACTION_PREFIX + "TakeScreenshot",
                "com.android.systemui",
                null,
                FAILED,
                "FAILED",
                "mSBReceiver rejects the untrusted sender."
        ));

        // Host -> system_server (android), sender identity whitelist.
        probes.add(new Probe(
                "ForceClose",
                ACTION_PREFIX + "ForceClose",
                "android",
                null,
                FAILED,
                "FAILED",
                "phoneWindowManagerActionReceiver in system_server gets the broadcast but the probe is not in the sender whitelist."
        ));

        probes.add(new Probe(
                "SimulateMenu",
                ACTION_PREFIX + "SimulateMenu",
                "android",
                null,
                FAILED,
                "FAILED",
                "phoneWindowManagerActionReceiver rejects the untrusted sender."
        ));

        // Module -> SystemUI, signature permission.
        probes.add(new Probe(
                "FetchCachedDevices",
                ACTION_PREFIX + "FetchCachedDevices",
                "com.android.systemui",
                null,
                SENTINEL,
                "SENTINEL",
                "Receiver exists but the probe does not hold the BROADCAST signature permission."
        ));

        // Host -> module, sender identity whitelist.
        Bundle privacyBundle = new Bundle();
        privacyBundle.putString("DATATYPE", "privacy");
        probes.add(new Probe(
                "PUSHAPPCONFIG",
                EVENT_PREFIX + "PUSHAPPCONFIG",
                "tv.withaibuild.customiuizer.r14",
                privacyBundle,
                FAILED,
                "FAILED",
                "AppSelector.configReceiver in the module gets the broadcast but the sender is not com.miui.home."
        ));

        // Negative probes.
        probes.add(new Probe(
                "FastReboot sent to wrong package",
                ACTION_PREFIX + "FastReboot",
                "com.miui.home",
                null,
                SENTINEL,
                "SENTINEL",
                "There is no FastReboot receiver in com.miui.home, so the broadcast is never claimed."
        ));

        probes.add(new Probe(
                "Unregistered action",
                ACTION_PREFIX + "ThisActionDoesNotExist",
                "com.android.systemui",
                null,
                SENTINEL,
                "SENTINEL",
                "No receiver registers for this action anywhere."
        ));

        for (final Probe probe : probes) {
            Button button = new Button(this);
            button.setText(probe.label);
            button.setOnClickListener(v -> {
                Intent intent = new Intent(probe.action);
                intent.setPackage(probe.targetPackage);
                if (probe.extras != null) {
                    intent.putExtras(probe.extras);
                }
                BroadcastReceiver result = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int code = getResultCode();
                        String verdict;
                        if (code == SENTINEL) {
                            verdict = "SENTINEL";
                        } else if (code == HANDLED) {
                            verdict = "HANDLED";
                        } else if (code == FAILED) {
                            verdict = "FAILED";
                        } else {
                            verdict = "UNKNOWN(" + code + ")";
                        }
                        boolean expected = (code == probe.expectedResult);
                        String status = expected ? "OK" : "REGRESSION?";
                        StringBuilder sb = new StringBuilder();
                        sb.append(probe.label).append("\n");
                        sb.append("target: ").append(probe.targetPackage).append("\n");
                        sb.append("result: ").append(verdict).append(" (").append(status).append(")\n");
                        sb.append("expected: ").append(probe.expectedText).append("\n");
                        sb.append("why: ").append(probe.reason);
                        if (!expected && code == HANDLED) {
                            sb.append("\n\nCRITICAL: an untrusted app triggered a high-privilege action.");
                        }
                        logView.setText(sb.toString());
                    }
                };
                sendOrderedBroadcast(intent, null, result, null, SENTINEL, null, null);
                logView.setText("Sent " + probe.label + " to " + probe.targetPackage + ", waiting for result...");
            });
            layout.addView(button);
        }

        setContentView(layout);
    }
}
