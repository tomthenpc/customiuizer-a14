package tv.withaibuild.customiuizer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import androidx.annotation.Nullable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import tv.withaibuild.customiuizer.subs.CategorySelector
import tv.withaibuild.customiuizer.subs.Controls
import tv.withaibuild.customiuizer.subs.Launcher
import tv.withaibuild.customiuizer.subs.System as SubSystem
import tv.withaibuild.customiuizer.subs.Various
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.ModData
import tv.withaibuild.customiuizer.utils.ModSearchAdapter
import tv.withaibuild.customiuizer.utils.SearchRouteResolver
import tv.withaibuild.customiuizer.utils.SearchStateMachine
import tv.withaibuild.customiuizer.utils.XposedServiceManager

class MainFragment : PreferenceFragmentBase() {

    private val catSelector = CategorySelector()

    @JvmField
    var prefSystem = SubSystem()

    @JvmField
    var prefLauncher = Launcher()

    @JvmField
    var prefControls = Controls()

    @JvmField
    var prefVarious = Various()

    private var mActionMenu: Menu? = null
    private var listView: RecyclerView? = null
    private var resultView: ListView? = null

    private var isSearchFocused = false
    private var inSearchView = SearchStateMachine.STATE_IDLE
    private var lastFilter: String? = null
    private var isRestoringSearch = false

    private fun isFragmentReady(act: AppCompatActivity?): Boolean {
        return act != null && !act.isFinishing && isAdded
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        toolbarMenu = true
        activeMenus = "all"
        super.onCreate(savedInstanceState, R.xml.prefs_main)
        tailLayoutId = R.layout.prefs_main12

        val act = activity as? AppCompatActivity ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            if (act.isFinishing) return@launch
            Helpers.getAllMods(act, savedInstanceState != null)
        }

        checkModuleIsActive()

        savedInstanceState?.let {
            inSearchView = it.getInt("inSearchView", 0)
            lastFilter = it.getString("lastFilter")
            isSearchFocused = it.getBoolean("isSearchFocused", false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("inSearchView", inSearchView)
        outState.putString("lastFilter", lastFilter)
        outState.putBoolean("isSearchFocused", isSearchFocused)
    }

    private fun checkModuleIsActive() {
        lifecycleScope.launch {
            // Wait on the service manager's own deadline rather than a second, shorter one
            // of our own: giving up while the state is still UNKNOWN draws no conclusion
            // here and defers the dialog to the next time this screen is entered.
            val deadline = System.currentTimeMillis() + XposedServiceManager.BIND_DECISION_TIMEOUT_MS + 500L
            while (isActive && XposedServiceManager.state == XposedServiceManager.State.UNKNOWN && System.currentTimeMillis() < deadline) {
                delay(100L)
            }
            if (!isActive) return@launch
            val act = activity as? AppCompatActivity ?: return@launch
            if (isFragmentReady(act) && XposedServiceManager.state == XposedServiceManager.State.DISCONNECTED) {
                showXposedDialog(act)
            }
        }
    }

    override fun onCreatePreferences(@Nullable savedInstanceState: Bundle?, @Nullable rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.prefs_main, rootKey)
    }

    private fun doBaseReload() = super.reloadPreferences()

    override fun reloadPreferences() {
        val act = activity as? AppCompatActivity ?: return super.reloadPreferences()
        lifecycleScope.launch(Dispatchers.IO) {
            Helpers.getAllMods(act, true)
            withContext(Dispatchers.Main) {
                doBaseReload()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        mActionMenu = menu
        val searchMenuItem = mActionMenu?.findItem(R.id.search_btn) ?: return

        val searchView = searchMenuItem.actionView as? SearchView ?: return
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                for (i in 0 until (mActionMenu?.size() ?: 0)) {
                    val menuItem = mActionMenu?.getItem(i) ?: continue
                    menuItem.isVisible = menuItem.itemId != R.id.edit_confirm
                }
                return true
            }

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                for (i in 0 until (mActionMenu?.size() ?: 0)) {
                    val menuItem = mActionMenu?.getItem(i) ?: continue
                    menuItem.isVisible = menuItem.itemId == R.id.search_btn
                }
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                if (isRestoringSearch || !SearchStateMachine.canFilter(inSearchView)) return false
                inSearchView = SearchStateMachine.transitionOnQuery(inSearchView, newText)
                findMod(newText ?: "")
                return false
            }
        })

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            isSearchFocused = hasFocus
        }

        if (SearchStateMachine.shouldClearOnReturn(inSearchView)) {
            resetSearchUi(searchMenuItem, searchView)
        } else if (inSearchView != SearchStateMachine.STATE_IDLE && !lastFilter.isNullOrEmpty()) {
            isRestoringSearch = true
            searchMenuItem.expandActionView()
            searchView.setQuery(lastFilter, false)
            if (!isSearchFocused) searchView.clearFocus()
            isRestoringSearch = false
            if (resultView != null && listView != null) findMod(lastFilter ?: "")
        }
    }

    override fun fixStubLayout(view: View, postion: Int) {
        if (postion == 2) {
            val lp = view.layoutParams
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = lp
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actionBar: ActionBar? = getActionBar()
        actionBar?.setTitle(R.string.app_name)

        val view = this.view ?: return

        resultView = view.findViewById(R.id.custom)
        resultView?.setDivider(null)
        resultView?.setDividerHeight(0)
        resultView?.adapter = ModSearchAdapter(requireActivity())
        resultView?.setOnItemClickListener { parent: AdapterView<*>, _, position: Int, _ ->
            val mod = parent.adapter?.getItem(position) as? ModData ?: return@setOnItemClickListener
            if (openModCat(mod.cat.name, mod.sub, mod.key)) {
                inSearchView = SearchStateMachine.STATE_NAVIGATED
                isSearchFocused = false
                Helpers.hideKeyboard(activity as? AppCompatActivity, this@MainFragment.view)
            }
        }
        resultView?.setOnTouchListener { _, event: MotionEvent ->
            if (isSearchFocused) {
                isSearchFocused = false
                lifecycleScope.launch {
                    delay(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                    Helpers.hideKeyboard(activity as? AppCompatActivity, this@MainFragment.view)
                    resultView?.requestFocus()
                }
            }
            false
        }

        listView = getListView()

        when {
            SearchStateMachine.shouldClearOnReturn(inSearchView) -> resetSearchUi(null, null)
            inSearchView != SearchStateMachine.STATE_IDLE && !lastFilter.isNullOrEmpty() -> findMod(lastFilter ?: "")
        }

        findPreference<Preference>("pref_key_miuizer_launchericon")?.setOnPreferenceChangeListener { _, newValue ->
            val act = activity as? AppCompatActivity ?: return@setOnPreferenceChangeListener false
            val pm = act.packageManager
            val component = ComponentName(act, GateWayLauncher::class.java)
            if (newValue == true) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            } else {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resultView = null
        listView = null
        mActionMenu = null
    }

    private fun findMod(filter: String) {
        if (isRestoringSearch || !SearchStateMachine.canFilter(inSearchView)) return
        lastFilter = filter
        resultView?.visibility = if (filter == "") View.GONE else View.VISIBLE
        listView?.isEnabled = filter == ""
        val adapter = resultView?.adapter ?: return
        (adapter as ModSearchAdapter).filter.filter(filter)
    }

    private fun resetSearchUi(searchMenuItem: MenuItem?, searchView: SearchView?) {
        if (!SearchStateMachine.shouldClearOnReturn(inSearchView)) return
        isRestoringSearch = true
        try {
            searchMenuItem?.collapseActionView()
            searchView?.setQuery("", false)
            searchView?.clearFocus()
            resultView?.visibility = View.GONE
            listView?.isEnabled = true
            isSearchFocused = false
        } finally {
            isRestoringSearch = false
        }
        inSearchView = SearchStateMachine.STATE_IDLE
        lastFilter = null
    }

    private fun openModCat(cat: String, sub: String?, mod: String): Boolean {
        val route = SearchRouteResolver.resolve(cat, sub, mod) ?: return false
        if (!isAdded) return false

        val bundle = Bundle().apply {
            putString("cat", route.category)
            putString("mod", route.key)
            route.sub?.let { putString("sub", it) }
        }

        return when (route.category) {
            "pref_key_system" -> {
                if (route.isCategorySelector()) {
                    catSelector.setTargetFragment(this, 0)
                    openSubFragment(catSelector, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_mods, R.xml.prefs_system_cat)
                } else {
                    openSubFragment(prefSystem, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_mods, R.xml.prefs_system)
                }
                true
            }
            "pref_key_launcher" -> {
                if (route.isCategorySelector()) {
                    catSelector.setTargetFragment(this, 0)
                    openSubFragment(catSelector, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.launcher_title, R.xml.prefs_launcher_cat)
                } else {
                    openSubFragment(prefLauncher, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.launcher_title, R.xml.prefs_launcher)
                }
                true
            }
            "pref_key_controls" -> {
                if (route.isCategorySelector()) {
                    catSelector.setTargetFragment(this, 0)
                    openSubFragment(catSelector, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.controls_mods, R.xml.prefs_controls_cat)
                } else {
                    openSubFragment(prefControls, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.controls_mods, R.xml.prefs_controls)
                }
                true
            }
            "pref_key_various" -> {
                openSubFragment(prefVarious, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_mods, R.xml.prefs_various)
                true
            }
            else -> false
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key ?: return super.onPreferenceTreeClick(preference)
        val modsCat = findPreference<PreferenceCategory>("prefs_cat")
        return if (modsCat?.findPreference<Preference>(key) != null && openModCat(key, null, key)) {
            true
        } else {
            super.onPreferenceTreeClick(preference)
        }
    }

}
