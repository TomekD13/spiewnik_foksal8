package com.spiewnik.app.ui.settings

import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.spiewnik.app.BuildConfig
import com.spiewnik.app.CrashLogger
import com.spiewnik.app.R
import com.spiewnik.app.SongViewModel
import com.spiewnik.app.databinding.FragmentSettingsBinding
import com.spiewnik.app.holyrics.HolyricsQrParser

class SettingsFragment : DialogFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SongViewModel by activityViewModels()

    // Skaner QR (ZXing) — sam prosi o uprawnienie do aparatu przy uruchomieniu.
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleHolyricsQr(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Holyrics connection settings
        binding.etHolyricsIp.setText(viewModel.settings.holyricsIp)
        binding.etHolyricsToken.setText(viewModel.settings.holyricsToken)

        // Reveal token for 5 s, then mask again
        binding.btnRevealToken.setOnClickListener {
            val et = binding.etHolyricsToken
            et.transformationMethod = null
            et.setSelection(et.text?.length ?: 0)
            et.postDelayed({
                _binding?.etHolyricsToken?.transformationMethod = PasswordTransformationMethod.getInstance()
            }, 5000)
        }
        binding.btnScanHolyricsQr.setOnClickListener {
            qrScanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(getString(R.string.holyrics_scan_qr))
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
            )
        }
        binding.btnSaveHolyrics.setOnClickListener {
            viewModel.settings.holyricsIp = binding.etHolyricsIp.text.toString().trim()
            viewModel.settings.holyricsToken = binding.etHolyricsToken.text.toString().trim()
            viewModel.showToast(getString(R.string.holyrics_saved))
        }

        // Auto-follow Holyrics toggle
        binding.swAutoFollow.isChecked = viewModel.settings.holyricsAutoFollow
        binding.swAutoFollow.setOnCheckedChangeListener { _, checked ->
            viewModel.settings.holyricsAutoFollow = checked
            if (checked) viewModel.startHolyricsAutoFollow() else viewModel.stopHolyricsAutoFollow()
        }

        // Send-to-Holyrics toggle (shows/hides the top-bar button)
        binding.swHolyricsSend.isChecked = viewModel.settings.holyricsSend
        binding.swHolyricsSend.setOnCheckedChangeListener { _, checked ->
            viewModel.setHolyricsSend(checked)
        }

        binding.btnHelp.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.settings_close, null)
                .show()
        }

        // Crash logging on/off
        binding.swCrashLog.isChecked = viewModel.settings.crashLogEnabled
        binding.swCrashLog.setOnCheckedChangeListener { _, checked ->
            viewModel.settings.crashLogEnabled = checked
        }

        binding.btnShareCrashLog.setOnClickListener {
            val intent = CrashLogger.shareIntent(requireContext())
            if (intent == null) {
                viewModel.showToast(getString(R.string.crash_log_empty))
            } else {
                startActivity(android.content.Intent.createChooser(intent, getString(R.string.crash_log_share)))
            }
        }

        binding.tvAppInfo.text = "${getString(R.string.settings_version)} ${BuildConfig.VERSION_NAME}"

        binding.btnClose.setOnClickListener { dismiss() }
    }

    /**
     * Wypełnia pola IP + token z zeskanowanego kodu QR Holyrics (parsowanie w :core).
     * Nie zapisuje — użytkownik sprawdza i klika „Zapisz".
     */
    private fun handleHolyricsQr(contents: String) {
        val binding = _binding ?: return
        val config = HolyricsQrParser.parse(contents)
        if (config == null) {
            viewModel.showToast(getString(R.string.holyrics_qr_invalid))
            return
        }
        binding.etHolyricsIp.setText(config.ip)
        binding.etHolyricsToken.setText(config.token)
        viewModel.showToast(getString(R.string.holyrics_qr_loaded))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SettingsFragment"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                SettingsFragment().show(fragmentManager, TAG)
            }
        }
    }
}
