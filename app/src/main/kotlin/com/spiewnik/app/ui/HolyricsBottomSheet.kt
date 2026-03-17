package com.spiewnik.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.spiewnik.app.SongViewModel
import com.spiewnik.app.databinding.FragmentHolyricsBinding

class HolyricsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentHolyricsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SongViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHolyricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnHolyricsClose.setOnClickListener { dismiss() }

        // Numbers passed directly as arguments to avoid LiveData timing issues
        val raw = arguments?.getIntArray(ARG_NUMBERS)
        android.widget.Toast.makeText(requireContext(), "BottomSheet args: ${raw?.toList()}", android.widget.Toast.LENGTH_LONG).show()
        val numbers = raw?.toList() ?: emptyList()
        populateButtons(numbers)
    }

    private fun populateButtons(numbers: List<Int>) {
        android.util.Log.i("HolyricsBottomSheet", "populateButtons: numbers=$numbers")
        binding.llSongButtons.removeAllViews()
        val allSongs = viewModel.allSongs.value ?: emptyList()

        numbers.forEach { number ->
            val song = allSongs.find { it.number == number }
            val label = if (song != null) "$number. ${song.title}" else "$number"

            val btn = Button(requireContext()).apply {
                text = label
                textSize = 15f
                setOnClickListener {
                    viewModel.openSong(number)
                    dismiss()
                }
            }

            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            binding.llSongButtons.addView(btn, params)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "HolyricsBottomSheet"
        private const val ARG_NUMBERS = "numbers"

        fun show(fragmentManager: FragmentManager, numbers: List<Int>) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                val sheet = HolyricsBottomSheet()
                sheet.arguments = Bundle().apply {
                    putIntArray(ARG_NUMBERS, numbers.toIntArray())
                }
                sheet.show(fragmentManager, TAG)
            }
        }
    }
}
