package org.fossify.musicplayer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.musicplayer.activities.ScreenHostActivity
import org.fossify.musicplayer.adapters.ExcludedFoldersAdapter
import org.fossify.musicplayer.databinding.FragmentExcludedFoldersBinding
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.getTintedTextColor

/**
 * The folders kept out of the library.
 *
 * Native rather than Compose because removing folders runs through the Commons adapter's
 * contextual action bar, which is chrome only a hosted fragment can drive.
 */
class ExcludedFoldersFragment : Fragment(), RefreshRecyclerViewListener {

    private var _binding: FragmentExcludedFoldersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExcludedFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as ScreenHostActivity).bindScrollingBody(binding.excludedFoldersList)
        updateFolders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateFolders() {
        val activity = requireActivity() as ScreenHostActivity
        val folders = activity.config.excludedFolders.toMutableList() as ArrayList<String>
        binding.excludedFoldersPlaceholder.apply {
            beVisibleIf(folders.isEmpty())
            setTextColor(activity.getTintedTextColor())
        }

        binding.excludedFoldersList.adapter =
            ExcludedFoldersAdapter(activity, folders, this, binding.excludedFoldersList) {}
    }

    override fun refreshItems() {
        updateFolders()
    }
}
