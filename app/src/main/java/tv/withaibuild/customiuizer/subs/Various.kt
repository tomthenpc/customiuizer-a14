package tv.withaibuild.customiuizer.subs

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.XposedServiceManager

class Various : SubFragment() {

    private companion object {
        const val UPDATER_PACKAGE = "com.android.updater"
        const val UPDATE_SERVICES_PREF = "pref_key_various_disable_update_services"
        const val UPDATE_SERVICE_NAMES_SNAPSHOT = "internal_updater_service_names"
        const val UPDATE_SERVICE_STATES_SNAPSHOT = "internal_updater_service_states"
        const val CLEAR_UPDATE_PREF = "pref_key_various_clear_update_state"
        const val MIUI_DAEMON_PACKAGE = "com.miui.daemon"
        const val MIUI_DAEMON_PREF = "pref_key_various_disable_miui_daemon"
        const val MIUI_DAEMON_STATE_SNAPSHOT = "internal_miui_daemon_application_state"
        const val BLOCK_NOTIFICATION_PROMPTS_PREF =
            "pref_key_various_block_notification_permission_prompts"
        const val BLOCK_LOCATION_PROMPTS_PREF =
            "pref_key_various_block_location_permission_prompts"
        val PERMISSION_CONTROLLER_PACKAGES = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        selectSub()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        findPreference<Preference>("pref_key_various_alarmcompat_apps")?.setOnPreferenceClickListener(openAppsEdit)

        findPreference<Preference>("pref_key_various_calluibright_cat")?.setOnPreferenceClickListener {
            openSubFragment(Various_CallUIBright(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_calluibright_title, R.xml.prefs_various_calluibright)
            true
        }

        findPreference<Preference>("pref_key_various_hiddenfeatures_cat")?.setOnPreferenceClickListener {
            openSubFragment(Various_HiddenFeatures(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_hiddenfeatures_title, R.xml.prefs_various_hiddenfeatures)
            true
        }

        setupUpdaterServiceControl()
        setupUpdaterStateCleaner()
        setupMiuiDaemonControl()
        setupPermissionControllerScopeRequests()

        try {
            val act = activity ?: throw Throwable()
            val pkgInfo = act.packageManager.getApplicationInfo("com.miui.packageinstaller", PackageManager.MATCH_DISABLED_COMPONENTS)
            if (!pkgInfo.enabled) throw Throwable()
        } catch (e: Throwable) {
            val pref = findPreference<CheckBoxPreferenceEx>("pref_key_various_miuiinstaller")
            pref?.isChecked = false
            pref?.setUnsupported(true)
            pref?.setSummary(R.string.various_miuiinstaller_error)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupPermissionControllerScopeRequests() {
        val packageManager = context?.packageManager ?: return
        val installedTargets = PERMISSION_CONTROLLER_PACKAGES.filter { packageName ->
            try {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (installedTargets.isEmpty()) return
        for (key in listOf(BLOCK_NOTIFICATION_PROMPTS_PREF, BLOCK_LOCATION_PROMPTS_PREF)) {
            val preference = findPreference<CheckBoxPreferenceEx>(key) ?: continue
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue != true) return@setOnPreferenceChangeListener true
                val requested = XposedServiceManager.requestApi102Scope(installedTargets) {
                        success, message ->
                    if (!success) {
                        AppHelper.log("PermissionScope", message ?: "scope request rejected")
                        Toast.makeText(
                            context,
                            R.string.various_permission_scope_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                if (!requested) {
                    Toast.makeText(
                        context,
                        R.string.various_permission_scope_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }
        }
    }

    private fun setupUpdaterStateCleaner() {
        val preference = findPreference<PreferenceEx>(CLEAR_UPDATE_PREF) ?: return
        preference.setOnPreferenceClickListener {
            val activity = activity ?: return@setOnPreferenceClickListener false
            AlertDialog.Builder(activity)
                .setTitle(R.string.various_clear_update_state_title)
                .setMessage(R.string.various_clear_update_state_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestMaintenanceAction(
                        preference,
                        Intent(GlobalActions.CLEAR_UPDATER_STATE_ACTION).setPackage("android"),
                        R.string.various_clear_update_state_success,
                        R.string.various_clear_update_state_failed
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun setupMiuiDaemonControl() {
        val preference = findPreference<CheckBoxPreferenceEx>(MIUI_DAEMON_PREF) ?: return
        val context = context ?: return
        val originalState = try {
            val info = context.packageManager.getApplicationInfo(
                MIUI_DAEMON_PACKAGE,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0) {
                throw PackageManager.NameNotFoundException()
            }
            context.packageManager.getApplicationEnabledSetting(MIUI_DAEMON_PACKAGE)
        } catch (_: PackageManager.NameNotFoundException) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_disable_miui_daemon_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            val disable = value == true
            val targetState = if (disable) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            } else {
                AppHelper.appPrefs?.getInt(MIUI_DAEMON_STATE_SNAPSHOT, Int.MIN_VALUE)
                    ?.takeIf { it != Int.MIN_VALUE }
                    ?: return@setOnPreferenceChangeListener false
            }
            val runAction = {
                val intent = Intent(GlobalActions.SET_MIUI_DAEMON_STATE_ACTION).apply {
                    setPackage("android")
                    putExtra(GlobalActions.EXTRA_APPLICATION_STATE, targetState)
                }
                requestMaintenanceAction(
                    preference,
                    intent,
                    R.string.various_disable_miui_daemon_success,
                    R.string.various_disable_miui_daemon_failed
                ) {
                    AppHelper.appPrefs?.edit()?.apply {
                        putBoolean(MIUI_DAEMON_PREF, disable)
                        if (disable) {
                            putInt(MIUI_DAEMON_STATE_SNAPSHOT, originalState)
                        } else {
                            remove(MIUI_DAEMON_STATE_SNAPSHOT)
                        }
                    }?.apply()
                    preference.isChecked = disable
                }
            }
            if (disable) {
                val activity = activity ?: return@setOnPreferenceChangeListener false
                AlertDialog.Builder(activity)
                    .setTitle(R.string.various_disable_miui_daemon_title)
                    .setMessage(R.string.various_disable_miui_daemon_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ -> runAction() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                runAction()
            }
            false
        }
    }

    private fun requestMaintenanceAction(
        preference: Preference,
        intent: Intent,
        successMessage: Int,
        failedMessage: Int,
        onSuccess: (() -> Unit)? = null
    ) {
        val context = context ?: return
        preference.isEnabled = false
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                preference.isEnabled = true
                if (resultCode == GlobalActions.ACTION_HANDLED) {
                    onSuccess?.invoke()
                    Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                    return
                }
                val message = if (resultCode == GlobalActions.ACTION_UNHANDLED) {
                    R.string.various_disable_update_services_bridge_unavailable
                } else {
                    failedMessage
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        ModuleHelper.sendOrderedBroadcastWithIdentity(
            context,
            intent,
            null,
            resultReceiver,
            Handler(Looper.getMainLooper()),
            GlobalActions.ACTION_UNHANDLED
        )
    }

    @Suppress("DEPRECATION")
    private fun declaredUpdaterServices(): Pair<Array<String>, IntArray>? {
        val context = context ?: return null
        return try {
            val services = context.packageManager.getPackageInfo(
                UPDATER_PACKAGE,
                PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
            ).services
                ?.map { it.name }
                ?.filter { it.startsWith("$UPDATER_PACKAGE.") }
                ?.distinct()
                ?.sorted()
                .orEmpty()
            if (services.isEmpty() || services.size > 32) return null
            val states = IntArray(services.size) { index ->
                context.packageManager.getComponentEnabledSetting(
                    ComponentName(UPDATER_PACKAGE, services[index])
                )
            }
            services.toTypedArray() to states
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun setupUpdaterServiceControl() {
        val preference = findPreference<CheckBoxPreferenceEx>(UPDATE_SERVICES_PREF) ?: return
        val current = declaredUpdaterServices()
        if (current == null) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_disable_update_services_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            if (value == true) {
                val activity = activity ?: return@setOnPreferenceChangeListener false
                AlertDialog.Builder(activity)
                    .setTitle(R.string.various_disable_update_services_title)
                    .setMessage(getString(R.string.various_disable_update_services_confirm, current.first.size))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestUpdaterServiceState(
                            preference,
                            true,
                            current.first,
                            IntArray(current.first.size) {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            current.second
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                val snapshot = readUpdaterServiceSnapshot()
                if (snapshot == null) {
                    Toast.makeText(
                        context,
                        R.string.various_disable_update_services_failed,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    requestUpdaterServiceState(
                        preference,
                        false,
                        snapshot.first,
                        snapshot.second,
                        null
                    )
                }
            }
            false
        }
    }

    private fun readUpdaterServiceSnapshot(): Pair<Array<String>, IntArray>? {
        val prefs = AppHelper.appPrefs ?: return null
        val names = prefs.getString(UPDATE_SERVICE_NAMES_SNAPSHOT, null)
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            ?.toTypedArray()
            ?: return null
        val states = prefs.getString(UPDATE_SERVICE_STATES_SNAPSHOT, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()
            ?: return null
        return if (names.isNotEmpty() && names.size == states.size) names to states else null
    }

    private fun requestUpdaterServiceState(
        preference: CheckBoxPreferenceEx,
        enabled: Boolean,
        names: Array<String>,
        requestedStates: IntArray,
        snapshotStates: IntArray?
    ) {
        val context = context ?: return
        preference.isEnabled = false
        val intent = Intent(GlobalActions.SET_UPDATER_SERVICES_ACTION).apply {
            setPackage("android")
            putExtra(GlobalActions.EXTRA_UPDATER_SERVICE_NAMES, names)
            putExtra(GlobalActions.EXTRA_UPDATER_SERVICE_STATES, requestedStates)
        }
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                preference.isEnabled = true
                if (resultCode == GlobalActions.ACTION_HANDLED) {
                    AppHelper.appPrefs?.edit()?.apply {
                        putBoolean(UPDATE_SERVICES_PREF, enabled)
                        if (enabled && snapshotStates != null) {
                            putString(UPDATE_SERVICE_NAMES_SNAPSHOT, names.joinToString("\n"))
                            putString(UPDATE_SERVICE_STATES_SNAPSHOT, snapshotStates.joinToString(","))
                        } else if (!enabled) {
                            remove(UPDATE_SERVICE_NAMES_SNAPSHOT)
                            remove(UPDATE_SERVICE_STATES_SNAPSHOT)
                        }
                    }?.apply()
                    preference.isChecked = enabled
                    return
                }
                val message = if (resultCode == GlobalActions.ACTION_UNHANDLED) {
                    R.string.various_disable_update_services_bridge_unavailable
                } else {
                    R.string.various_disable_update_services_failed
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        ModuleHelper.sendOrderedBroadcastWithIdentity(
            context,
            intent,
            null,
            resultReceiver,
            Handler(Looper.getMainLooper()),
            GlobalActions.ACTION_UNHANDLED
        )
    }
}
