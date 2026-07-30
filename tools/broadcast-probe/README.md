# CustoMIUIzer Broadcast Probe

A standalone, untrusted Android application used to verify that the CustoMIUIzer
module correctly rejects third-party broadcasts. It is intentionally signed with
the debug keystore and a different `applicationId`, and it does **not** enable
`BroadcastOptions` sender identity sharing.

## What it tests

Each button sends an ordered broadcast to the exact target package defined in
`docs/BROADCAST_TRUST_MATRIX.md`:

| Label | Action | Target package | Authentication path | Expected result |
|---|---|---|---|---|
| FastReboot (correct target) | `tv.withaibuild.customiuizer.mods.action.FastReboot` | `com.android.systemui` | Module → SystemUI, `signature` permission | `SENTINEL` (no delivery) |
| RestartSystemUI | `...action.RestartSystemUI` | `com.android.systemui` | Host → SystemUI, sender identity whitelist | `FAILED` (rejected) |
| RestartLauncher | `...action.RestartLauncher` | `com.android.systemui` | Host → SystemUI, sender identity whitelist | `FAILED` (rejected) |
| LockDevice | `...action.LockDevice` | `com.android.systemui` | Host → SystemUI, sender identity whitelist | `FAILED` (rejected) |
| TakeScreenshot | `...action.TakeScreenshot` | `com.android.systemui` | Host → SystemUI, sender identity whitelist | `FAILED` (rejected) |
| ForceClose | `...action.ForceClose` | `android` | Host → system_server, sender identity whitelist | `FAILED` (rejected) |
| SimulateMenu | `...action.SimulateMenu` | `android` | Host → system_server, sender identity whitelist | `FAILED` (rejected) |
| FetchCachedDevices | `...action.FetchCachedDevices` | `com.android.systemui` | Module → SystemUI, `signature` permission | `SENTINEL` (no delivery) |
| PUSHAPPCONFIG | `...event.PUSHAPPCONFIG` | `tv.withaibuild.customiuizer.r14` | Launcher → module, sender identity whitelist | `FAILED` (rejected) |
| FastReBoot sent to wrong package | `...action.FastReboot` | `com.miui.home` | No matching receiver | `SENTINEL` |
| Unregistered action | `...action.ThisActionDoesNotExist` | `com.android.systemui` | No matching receiver | `SENTINEL` |

## Result-code legend

The probe uses `sendOrderedBroadcast(..., initialCode = 100)`. Receivers update the
result code when they handle or explicitly reject a broadcast:

- `100` **SENTINEL** — no matching receiver, permission not held, or the broadcast
  was otherwise not delivered to a handler.
- `-1` **HANDLED** (`Activity.RESULT_OK`) — a receiver accepted and executed the
  action. This must **never** happen from this untrusted app.
- `1` **FAILED** (`Activity.RESULT_FIRST_USER`) — a receiver received the broadcast
  but explicitly rejected it because the sender was not whitelisted.

A successful security test is one where every button reports `SENTINEL` or `FAILED`.
Any `HANDLED` result on a high-privilege action is a regression.

## Building

From the repository root:

```bash
./gradlew :broadcast-probe:assembleDebug
```

Output: `tools/broadcast-probe/build/outputs/apk/debug/broadcast-probe-debug.apk`

## Usage on a real device

1. Install the module's `CustoMIUIzer-A14-r14.13.8-unsigned-ci.apk` through LSPosed
   and reboot.
2. Install `broadcast-probe-debug.apk` with a different signature.
3. Open the probe and tap each button.
4. Verify the on-screen result matches the Expected column above.
5. For `FastReboot` and `FetchCachedDevices`, the device must **not** soft-reboot
   or return a device list; a `SENTINEL` result confirms the `signature` permission
   protected the receiver.
6. For `RestartSystemUI` / `LockDevice` / `TakeScreenshot` / `ForceClose` /
   `SimulateMenu`, the device must **not** perform the action; a `FAILED` result
   confirms the sender identity whitelist worked.
