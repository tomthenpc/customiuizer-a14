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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        findPreference<ListPreferenceEx>("pref_key_miuizer_locale")?.let { locale ->
            AppHelper.setupLocalePreference(locale)
        }

        // Add version name to support title
        val view = view
        if (view != null) try {
            val version = view.findViewById<TextView>(R.id.about_version)
            val validContext = getValidContext()
            val versionName = validContext.packageManager.getPackageInfo(validContext.packageName, 0).versionName
            version?.text = String.format(Locale.US, getString(R.string.about_version), versionName)
        } catch (e: Throwable) {
            // Shouldn't happen...
            e.printStackTrace()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (view == null) return
        view?.findViewById<View>(R.id.miuizer_icon)?.visibility =
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
        super.onConfigurationChanged(newConfig)
    }
}
