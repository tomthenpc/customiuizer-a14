package tv.withaibuild.customiuizer

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppLocaleController
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
            AppLocaleController.setupLocalePreference(locale, AppHelper.appPrefs)
            installLocaleChangeListener(locale)
        }

        updateHeadViews(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view == null) return
        updateHeadViews(newConfig)
    }

    private fun installLocaleChangeListener(localePref: ListPreferenceEx) {
        val prefs = AppHelper.appPrefs ?: return

        localePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val newTag = newValue as? String ?: return@OnPreferenceChangeListener false
            val currentTag = AppLocaleController.getUserLocale(prefs)

            // Same value: let the ListPreference dialog close normally.
            if (newTag == currentTag) return@OnPreferenceChangeListener true

            showLocaleChangeConfirmation(newTag, localePref)
            // Always block the ListPreference from persisting automatically; the
            // confirmation dialog is the only path that may write and exit.
            false
        }
    }

    private fun showLocaleChangeConfirmation(newTag: String, localePref: ListPreferenceEx) {
        val prefs = AppHelper.appPrefs ?: return

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_change_locale_title)
            .setMessage(R.string.dialog_change_locale_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> /* dismiss only */ }
            .setPositiveButton(R.string.dialog_change_locale_confirm) { _, _ ->
                val success = AppLocaleController.setUserLocale(prefs, newTag)
                if (!success) {
                    Toast.makeText(
                        requireContext(),
                        R.string.dialog_change_locale_save_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                AppLocaleController.exitApplicationAfterLocaleSave(requireActivity())
            }
            .setOnDismissListener {
                // If the dialog is dismissed without confirming, restore the persisted
                // value so the summary and ListPreference selection stay in sync.
                localePref.value = AppLocaleController.getUserLocale(prefs)
            }
            .create()

        dialog.show()
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
