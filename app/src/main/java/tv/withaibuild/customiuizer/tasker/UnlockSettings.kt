package tv.withaibuild.customiuizer.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tv.withaibuild.customiuizer.R

class UnlockSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.tasker_unlock)

        val callingPackage = callingPackage
        val provider = UnlockTokenProvider()
        provider.clearLegacyGlobalToken(this)

        val hostInfoText = findViewById<TextView>(R.id.host_info)

        if (callingPackage == null) {
            hostInfoText.text = getString(R.string.unlock_host_missing)
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val hostInfo = provider.getHostInfo(this, callingPackage)
        if (hostInfo == null) {
            hostInfoText.text = getString(R.string.unlock_host_untrusted, callingPackage)
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val hostToken = provider.getOrCreateToken(this, hostInfo)
        if (hostToken == null) {
            hostInfoText.text = getString(
                R.string.unlock_host_cert_mismatch,
                hostInfo.applicationLabel,
                hostInfo.packageName,
                hostInfo.currentFingerprint ?: "?"
            )
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val existing = provider.getToken(this, hostInfo.packageName)
        val summary = getString(
            R.string.unlock_host_summary,
            hostInfo.applicationLabel,
            hostInfo.packageName,
            hostInfo.currentFingerprint ?: "?",
            hostInfo.historySummary
        )
        val explanation = if (existing != null) {
            getString(R.string.unlock_host_reuse, summary)
        } else {
            getString(R.string.unlock_host_first, summary)
        }
        hostInfoText.text = explanation

        val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
        if (bundle != null) {
            val opt = bundle.getInt("system_noscreenlock_force", -1)
            val checkedId = when (opt) {
                0 -> R.id.force_locked
                1 -> R.id.force_unlocked
                else -> R.id.force_off
            }
            findViewById<RadioGroup>(R.id.force_option).check(checkedId)
        }

        val ok = findViewById<Button>(R.id.force_ok)
        ok.setOnClickListener {
            val opt = findViewById<RadioGroup>(R.id.force_option).checkedRadioButtonId
            val lockState = when (opt) {
                R.id.force_locked -> 0
                R.id.force_unlocked -> 1
                else -> -1
            }

            val stringRes = when (lockState) {
                1 -> R.string.system_noscreenlock_force_unlocked
                0 -> R.string.system_noscreenlock_force_locked
                else -> R.string.system_noscreenlock_force_off
            }

            val resultIntent = Intent().apply {
                putExtra(Constants.EXTRA_STRING_BLURB, getString(stringRes))
                val outBundle = Bundle().apply {
                    putInt("system_noscreenlock_force", lockState)
                    putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN, hostToken.token)
                    putString(UnlockTokenProvider.BUNDLE_KEY_HOST_PACKAGE, hostToken.hostPackage)
                }
                putExtra(Constants.EXTRA_BUNDLE, outBundle)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        if (bundle == null || !bundle.containsKey(UnlockTokenProvider.BUNDLE_KEY_TOKEN)) {
            Toast.makeText(this, R.string.unlock_token_missing, Toast.LENGTH_LONG).show()
        }
    }
}
