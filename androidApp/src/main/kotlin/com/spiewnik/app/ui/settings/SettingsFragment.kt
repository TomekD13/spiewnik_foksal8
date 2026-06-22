package com.spiewnik.app.ui.settings

import android.os.Bundle
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

        binding.btnResetPosition.setOnClickListener {
            viewModel.resetPosition()
            dismiss()
        }

        // Holyrics connection settings
        binding.etHolyricsIp.setText(viewModel.settings.holyricsIp)
        binding.etHolyricsToken.setText(viewModel.settings.holyricsToken)
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

        binding.btnHelp.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.settings_close, null)
                .show()
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
