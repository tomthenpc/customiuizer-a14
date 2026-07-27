package tv.withaibuild.customiuizer

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.preference.Preference
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import java.util.Locale

class AboutFragment : SubFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headLayoutId = R.layout.fragment_about_head
        tailLayoutId = R.layout.fragment_about_tail
    }

    override fun fixStubLayout(view: View, postion: Int) {
        if (postion == 2) {
            val lp = view.layoutParams as? RelativeLayout.LayoutParams ?: return
            lp.addRule(RelativeLayout.BELOW, android.R.id.list_container)
            view.layoutParams = lp
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findPreference<ListPreferenceEx>("pref_key_miuizer_locale")?.let { locale ->
            AppHelper.setupLocalePreference(locale)
        }

        updateHeadViews(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view == null) return
        updateHeadViews(newConfig)
    }

    private fun updateHeadViews(config: Configuration) {
        val root = view ?: return
        root.findViewById<View>(R.id.miuizer_icon)?.visibility =
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE

        val versionView = root.findViewById<TextView>(R.id.about_version)
        if (versionView != null) try {
            val validContext = getValidContext()
            val versionName = validContext.packageManager.getPackageInfo(validContext.packageName, 0).versionName
            versionView.text = String.format(Locale.US, getString(R.string.about_version), versionName)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
