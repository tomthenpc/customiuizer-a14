package tv.withaibuild.customiuizer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.HashMap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers

open class PreferenceFragmentBase : PreferenceFragmentCompat() {

    private var actContext: Context? = null

    @JvmField
    protected var toolbarMenu = false

    @JvmField
    protected var animDur = 350

    @JvmField
    protected var activeMenus = ""

    @JvmField
    protected var isCustomActionBar = false

    @JvmField
    protected var headLayoutId = 0

    @JvmField
    protected var tailLayoutId = 0

    protected open fun getActionBar(): ActionBar? {
        val act = activity as AppCompatActivity?
        return act?.supportActionBar
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (toolbarMenu) {
            inflater.inflate(R.menu.menu_mods, menu)
        }
        if (isCustomActionBar) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item.isVisible = item.itemId == R.id.edit_confirm
            }
        } else {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val menuId = item.itemId
                val menuKey = MAP_KEYS[menuId]
                item.isVisible = when {
                    activeMenus == "all" && menuId == R.id.edit_confirm -> false
                    activeMenus == "all" -> true
                    menuKey != null && activeMenus.contains(menuKey) -> true
                    else -> false
                }
            }
        }
    }

    open fun confirmEdit() {}

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = AppHelper.prefsName
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.edit_confirm -> {
                confirmEdit()
                return true
            }
            R.id.restartlauncher -> {
                restartTarget("com.miui.home", R.string.restart_launcher_done, R.string.restart_launcher_failed)
                return true
            }
            R.id.restartsystemui -> {
                restartTargetProcess("com.android.systemui", R.string.restart_systemui_done, R.string.restart_systemui_failed)
                return true
            }
            R.id.restartsecuritycenter -> {
                restartTarget("com.miui.securitycenter", R.string.restart_securitycenter_done, R.string.restart_securitycenter_failed)
                return true
            }
            R.id.backuprestore -> {
                showBackupRestoreDialog()
                return true
            }
            R.id.softreboot -> {
                if (!AppHelper.moduleActive) {
                    showXposedDialog(activity as AppCompatActivity?)
                    return true
                }
                AlertDialog.Builder(getValidContext())
                    .setTitle(R.string.soft_reboot)
                    .setMessage(R.string.soft_reboot_ask)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(GlobalActions.ACTION_PREFIX + "FastReboot")
                        intent.setPackage("com.android.systemui")
                        getValidContext().sendBroadcast(intent)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                return true
            }
            R.id.about -> {
                openSubFragment(
                    AboutFragment(),
                    null,
                    AppHelper.SettingsType.Preference,
                    AppHelper.ActionBarType.HomeUp,
                    R.string.app_about,
                    R.xml.prefs_about
                )
                return true
            }
        }
        return false
    }

    open fun showXposedDialog(act: AppCompatActivity?) {
        if (act == null || act.isFinishing || act.isDestroyed) return
        AlertDialog.Builder(act)
            .setTitle(R.string.warning)
            .setMessage(R.string.module_not_active)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }

    /**
     * Restarts a package by force-stopping it (root shell). The system will restart it on demand.
     */
    private fun restartTarget(packageName: String, successRes: Int, failureRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val idResult = AppHelper.executeRootCommand("id")
            if (idResult.first != 0 || !idResult.second.contains("uid=0")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_no_root, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val cmd = "am force-stop $packageName"
            val result = AppHelper.executeRootCommand(cmd)
            withContext(Dispatchers.Main) {
                val ctx = getValidContext()
                if (result.first == 0) {
                    Toast.makeText(ctx, successRes, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("miuizer", "restart $packageName failed: exit=${result.first}, out=${result.second}")
                    Toast.makeText(ctx, "$failureRes: ${result.second}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Kills a running process by name (root shell). Used for SystemUI which should be restarted by the system.
     */
    private fun restartTargetProcess(processName: String, successRes: Int, failureRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val idResult = AppHelper.executeRootCommand("id")
            if (idResult.first != 0 || !idResult.second.contains("uid=0")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_no_root, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            // pidof returns exit 1 when no process is found; handle that explicitly.
            val pidResult = AppHelper.executeRootCommand("pidof $processName")
            if (pidResult.first != 0 || pidResult.second.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_target_not_running, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val pids = pidResult.second.split("\\s+".toRegex()).filter { it.isNotEmpty() }.joinToString(" ")
            val killResult = AppHelper.executeRootCommand("kill -9 $pids")
            withContext(Dispatchers.Main) {
                val ctx = getValidContext()
                if (killResult.first == 0) {
                    Toast.makeText(ctx, successRes, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("miuizer", "kill $processName failed: exit=${killResult.first}, out=${killResult.second}")
                    Toast.makeText(ctx, "$failureRes: ${killResult.second}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    open fun showBackupRestoreDialog() {
        val act = activity as AppCompatActivity?
        if (act == null || act.isFinishing || act.isDestroyed) return

        AlertDialog.Builder(act)
            .setTitle(R.string.backup_restore)
            .setMessage(R.string.backup_restore_choose)
            .setPositiveButton(R.string.do_restore) { _, _ -> restoreSettings(act) }
            .setNegativeButton(R.string.do_backup) { _, _ -> backupSettings(act) }
            .show()
    }

    private fun initFragment() {
        setHasOptionsMenu(toolbarMenu)
        val actionBar = getActionBar() ?: return

        val showBack = if (this is MainFragment) {
            val act = activity as AppCompatActivity?
            act != null && act.intent.getBooleanExtra("from.settings", false)
        } else {
            true
        }
        actionBar.setDisplayHomeAsUpEnabled(showBack)
    }

    @SuppressLint("WorldReadableFiles")
    open fun onCreate(savedInstanceState: Bundle?, prefDefaults: Int) {
        super.onCreate(savedInstanceState)
        try {
            PreferenceManager.setDefaultValues(getValidContext(), prefDefaults, false)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }
    }

    protected open fun fixStubLayout(view: View, postion: Int) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (headLayoutId > 0) {
            val stub = view.findViewById<ViewStub>(R.id.head_stub)
            stub.setLayoutResource(headLayoutId)
            fixStubLayout(stub.inflate(), 1)
        }
        if (tailLayoutId > 0) {
            val stub = view.findViewById<ViewStub>(R.id.tail_stub)
            stub.setLayoutResource(tailLayoutId)
            fixStubLayout(stub.inflate(), 2)
        }
        initFragment()
    }

    open fun openSubFragment(
        fragment: Fragment,
        args: Bundle?,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        titleResId: Int,
        contentResId: Int
    ) {
        openSubFragment(
            fragment,
            args,
            settingsType,
            abType,
            resources.getString(titleResId),
            contentResId
        )
    }

    open fun openSubFragment(
        fragment: Fragment,
        args: Bundle?,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        title: String?,
        contentResId: Int
    ) {
        val fragmentArgs = args ?: Bundle()
        fragmentArgs.putInt("settingsType", settingsType.ordinal)
        fragmentArgs.putInt("abType", abType.ordinal)
        fragmentArgs.putString("titleResId", title)
        fragmentArgs.putInt("contentResId", contentResId)
        var order = 100.0f
        try {
            val view = view
            if (view != null) order = view.translationZ
        } catch (_: Throwable) {
        }
        fragmentArgs.putFloat("order", order)

        val existingArgs = fragment.arguments
        if (existingArgs == null) {
            fragment.arguments = fragmentArgs
        } else {
            existingArgs.clear()
            existingArgs.putAll(fragmentArgs)
        }

        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.animator.fragment_open_enter,
                R.animator.fragment_open_exit,
                R.animator.fragment_close_enter,
                R.animator.fragment_close_exit
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (nextAnim == 0) return null

        val view = view ?: return null
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        if (screenWidth <= 0) return null

        val startTrans: Float
        val endTrans: Float
        when (nextAnim) {
            R.animator.fragment_open_enter -> {
                startTrans = screenWidth
                endTrans = 0.0f
            }
            R.animator.fragment_open_exit -> {
                startTrans = 0.0f
                endTrans = -screenWidth
            }
            R.animator.fragment_close_enter -> {
                startTrans = -screenWidth
                endTrans = 0.0f
            }
            R.animator.fragment_close_exit -> {
                startTrans = 0.0f
                endTrans = screenWidth
            }
            else -> return null
        }

        view.translationX = startTrans
        view.alpha = 1.0f

        val duration = (animDur * Helpers.getAnimationScale(2) + 0.5f).toLong()
        val animator = ValueAnimator.ofFloat(startTrans, endTrans)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator(1.2f)
        animator.addUpdateListener { animation ->
            view.translationX = animation.animatedValue as Float
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.translationX = endTrans
                view.alpha = 1.0f
            }

            override fun onAnimationCancel(animation: Animator) {
                view.translationX = endTrans
                view.alpha = 1.0f
            }
        })
        return animator
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        actContext = context
    }

    override fun onDetach() {
        super.onDetach()
        actContext = null
    }

    open fun getValidContext(): Context {
        val attachedContext = actContext
        if (attachedContext != null) return attachedContext
        val act = activity
        return if (act == null) requireContext() else act.applicationContext
    }

    @Suppress("UNUSED_PARAMETER")
    open fun backupSettings(act: AppCompatActivity) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        intent.putExtra(
            Intent.EXTRA_TITLE,
            "pengeek_backup_" + BACKUP_DATE_FORMAT.format(Date())
        )
        startActivityForResult(intent, SAVE_BACKFILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_BACKFILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                doRestoreSettings(data.data)
            }
        } else if (requestCode == SAVE_BACKFILE && resultCode == Activity.RESULT_OK) {
            try {
                val outputStream = getValidContext().contentResolver
                    .openOutputStream(data!!.data!!)
                ObjectOutputStream(outputStream!!).use { output ->
                    output.writeObject(AppHelper.appPrefs!!.all)
                }

                AlertDialog.Builder(getValidContext())
                    .setTitle(R.string.do_backup)
                    .setMessage(R.string.backup_ok)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            } catch (e: Throwable) {
                e.printStackTrace()
                AlertDialog.Builder(getValidContext())
                    .setTitle(R.string.warning)
                    .setMessage(getString(R.string.storage_cannot_backup) + "\n" + e.message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        }
    }

    open fun restoreSettings(act: AppCompatActivity) {
        if (!Helpers.checkStorageReadable(act)) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        startActivityForResult(intent, PICK_BACKFILE)
    }

    @Suppress("UNCHECKED_CAST")
    open fun doRestoreSettings(uri: Uri?) {
        val act = activity as AppCompatActivity?
        try {
            val validAct = act!!
            val inputStream = validAct.contentResolver.openInputStream(uri!!)
            val entries = ObjectInputStream(inputStream!!).use { input ->
                input.readObject() as Map<String, Any?>
            }
            AppHelper.syncPrefsToAnother(entries, AppHelper.appPrefs!!, 1, null, false)

            AlertDialog.Builder(validAct)
                .setTitle(R.string.do_restore)
                .setMessage(R.string.restore_ok)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    validAct.finish()
                    validAct.startActivity(validAct.intent)
                }
                .show()
        } catch (t: Throwable) {
            t.printStackTrace()
            AlertDialog.Builder(act!!)
                .setTitle(R.string.warning)
                .setMessage(R.string.storage_cannot_restore)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
    }

    companion object {
        const val PICK_BACKFILE = 11
        const val SAVE_BACKFILE = 12

        @JvmField
        protected val MAP_KEYS: Map<Int, String>

        private val BACKUP_DATE_FORMAT: SimpleDateFormat

        init {
            val map = HashMap<Int, String>()
            map[R.id.search_btn] = "search"
            map[R.id.restartlauncher] = "launcher"
            map[R.id.restartsystemui] = "systemui"
            map[R.id.restartsecuritycenter] = "securitycenter"
            map[R.id.edit_confirm] = "edit"
            map[R.id.softreboot] = "reboot"
            map[R.id.backuprestore] = "settings"
            map[R.id.resetsettings] = "reset"
            map[R.id.about] = "about"
            MAP_KEYS = Collections.unmodifiableMap(map)
            BACKUP_DATE_FORMAT = SimpleDateFormat("MMddHHmmss", Locale.US)
        }
    }
}
