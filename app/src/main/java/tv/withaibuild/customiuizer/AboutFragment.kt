package tv.withaibuild.customiuizer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppLocaleController
import java.util.Locale

class AboutFragment : Fragment() {

    private var localeDialog: AlertDialog? = null
    private var confirmationDialog: AlertDialog? = null
    private var donateDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initActionBar()
        bindRowClicks(view)
        updateHeadViews(view, resources.configuration)
        updateLanguageRow(view)
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        updateLanguageRow(view)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = view ?: return
        updateHeadViews(view, newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        localeDialog?.dismiss()
        localeDialog = null
        confirmationDialog?.dismiss()
        confirmationDialog = null
        donateDialog?.dismiss()
        donateDialog = null
    }

    private fun initActionBar() {
        val act = activity as? AppCompatActivity ?: return
        act.supportActionBar?.apply {
            setTitle(R.string.app_about)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun bindRowClicks(view: View) {
        view.findViewById<View>(R.id.about_language_row).setOnClickListener {
            showLocaleSelector()
        }
        view.findViewById<View>(R.id.about_donate_row).setOnClickListener {
            showDonationDialog()
        }
        view.findViewById<View>(R.id.about_repository_row).setOnClickListener {
            openLink(REPOSITORY_URL)
        }
        view.findViewById<View>(R.id.about_contact_row).setOnClickListener {
            openLink(CONTACT_URL)
        }
    }

    private fun updateHeadViews(view: View, config: Configuration) {
        view.findViewById<View>(R.id.miuizer_icon)?.visibility =
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE

        view.findViewById<TextView>(R.id.about_version)?.text =
            String.format(Locale.US, getString(R.string.about_version), BuildConfig.VERSION_NAME ?: "")
    }

    private fun updateLanguageRow(view: View) {
        val summary = AppLocaleController.getUserLocaleSummary(requireContext(), AppHelper.appPrefs)
        view.findViewById<TextView>(R.id.about_language_summary)?.text = summary
    }

    private fun showLocaleSelector() {
        val context = context ?: return
        val prefs = AppHelper.appPrefs ?: return
        val currentTag = AppLocaleController.getUserLocaleForUi(prefs)

        val (displayEntries, entryValues) = try {
            AppLocaleController.buildLocaleDisplayData(context)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            Log.e("AboutFragment", "Cannot build locale display data", t)
            return
        }

        var selectedIndex = entryValues.indexOf(currentTag)
        if (selectedIndex < 0) selectedIndex = 0

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.miuizer_locale_title)
            .setSingleChoiceItems(displayEntries, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onLocaleSelected(entryValues, selectedIndex)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                localeDialog = null
            }
            .create()

        localeDialog = dialog
        dialog.show()
    }

    private fun onLocaleSelected(entryValues: Array<String>, index: Int) {
        if (index < 0 || index >= entryValues.size) return
        val newTag = entryValues[index]

        val prefs = AppHelper.appPrefs ?: return
        val currentTag = AppLocaleController.getUserLocaleForUi(prefs)

        // Same value: no confirmation and no write.
        if (newTag == currentTag) return

        showLocaleChangeConfirmation(newTag)
    }

    private fun showLocaleChangeConfirmation(newTag: String) {
        val context = context ?: return

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.dialog_change_locale_title)
            .setMessage(R.string.dialog_change_locale_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dialog_change_locale_confirm) { _, _ ->
                applyLocale(newTag)
            }
            .setOnDismissListener {
                confirmationDialog = null
            }
            .create()

        confirmationDialog = dialog
        dialog.show()
    }

    private fun applyLocale(newTag: String) {
        val prefs = AppHelper.appPrefs ?: run {
            showToast(R.string.dialog_change_locale_save_failed)
            return
        }

        val success = AppLocaleController.setUserLocale(prefs, newTag)
        if (!success) {
            showToast(R.string.dialog_change_locale_save_failed)
            return
        }

        val act = activity as? AppCompatActivity ?: return
        if (act.isFinishing || act.isDestroyed || !isAdded) return
        AppLocaleController.exitApplicationAfterLocaleSave(act)
    }

    private fun showDonationDialog() {
        val context = context ?: return
        val bitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.wechat_donation_code,
            BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = DONATION_IMAGE_SAMPLE_SIZE
            }
        ) ?: run {
            showToast(R.string.about_donation_unavailable)
            return
        }

        val density = resources.displayMetrics.density
        val padding = (16f * density + 0.5f).toInt()
        val size = minOf(
            (300f * density + 0.5f).toInt(),
            resources.displayMetrics.widthPixels - padding * 2
        )

        val image = ImageView(context).apply {
            contentDescription = getString(R.string.about_donate_image_description)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
        }
        val container = FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(image, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.about_donate_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener {
                image.setImageDrawable(null)
                if (!bitmap.isRecycled) bitmap.recycle()
                donateDialog = null
            }
            .create()

        donateDialog = dialog
        dialog.show()
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.about_link_unavailable)
        }
    }

    private fun showToast(messageRes: Int) {
        val context = context ?: return
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REPOSITORY_URL = "https://github.com/tomthenpc/customiuizer-a14"
        const val CONTACT_URL = "https://t.me/Jinji_Kiko"
        const val DONATION_IMAGE_SAMPLE_SIZE = 2
    }
}
