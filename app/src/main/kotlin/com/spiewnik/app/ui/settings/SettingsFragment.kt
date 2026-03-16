package com.spiewnik.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.spiewnik.app.BuildConfig
import com.spiewnik.app.NavMode
import com.spiewnik.app.R
import com.spiewnik.app.SongViewModel
import com.spiewnik.app.databinding.FragmentSettingsBinding

class SettingsFragment : DialogFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SongViewModel by activityViewModels()

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

        // Set current nav mode in radio group
        when (viewModel.state.value?.navMode ?: NavMode.SPREAD) {
            NavMode.SPREAD -> binding.rbSpread.isChecked = true
            NavMode.PAGE   -> binding.rbPage.isChecked = true
            NavMode.SONG   -> binding.rbSong.isChecked = true
        }

        binding.rgNavMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbSpread -> NavMode.SPREAD
                R.id.rbPage   -> NavMode.PAGE
                R.id.rbSong   -> NavMode.SONG
                else          -> NavMode.SPREAD
            }
            viewModel.setNavMode(mode)
        }

        binding.btnResetPosition.setOnClickListener {
            viewModel.resetPosition()
            dismiss()
        }

        binding.tvAppInfo.text = "${getString(R.string.settings_version)} ${BuildConfig.VERSION_NAME}"

        binding.btnClose.setOnClickListener { dismiss() }
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
