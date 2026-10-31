package com.aistra.hail.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aistra.hail.BuildConfig
import com.aistra.hail.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.HailData
import com.aistra.hail.app.TagBackupArchive
import com.aistra.hail.app.HailData.tags
import com.aistra.hail.databinding.FragmentHomeBinding
import com.aistra.hail.extensions.applyDefaultInsetter
import com.aistra.hail.extensions.isLandscape
import com.aistra.hail.extensions.isRtl
import com.aistra.hail.extensions.paddingRelative
import com.aistra.hail.ui.main.MainFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class HomeFragment : MainFragment() {
    var multiselect: Boolean = false
    val selectedList: MutableList<AppInfo> = mutableListOf()
    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    private val exportTagBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri?.let(::writeTagBackup)
        }

    private val importTagBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::readTagBackup)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        if (tags.size == 1) binding.tabs.isVisible = false
        binding.pager.adapter = HomeAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tags[position].first
        }.attach()
        binding.tabs.post { bindTagLongPress() }
        binding.manageTagsButton.setOnClickListener { TagEditorDialog(this).show() }
        binding.tabs.applyDefaultInsetter { paddingRelative(isRtl, start = !activity.isLandscape, end = true) }
        return binding.root
    }

    fun selectedTagId(): Int = tags.getOrNull(binding.pager.currentItem)?.second ?: 0

    fun refreshTags(preferredTagId: Int?) {
        val pagerAdapter = binding.pager.adapter as? HomeAdapter ?: return
        pagerAdapter.notifyDataSetChanged()
        binding.tabs.isVisible = tags.size > 1
        binding.tabs.post {
            bindTagLongPress()
            val position = tags.indexOfFirst { it.second == preferredTagId }
            binding.pager.setCurrentItem(position.takeIf { it >= 0 } ?: 0, false)
            childFragmentManager.fragments
                .filterIsInstance<PagerFragment>()
                .firstOrNull { it.isResumed }
                ?.refreshAfterTagConfigChanged()
        }
    }

    fun refreshAppLists() {
        childFragmentManager.fragments
            .filterIsInstance<PagerFragment>()
            .forEach { it.refreshAfterTagConfigChanged() }
    }

    fun launchTagBackupExport() {
        exportTagBackupLauncher.launch("Hail-Tag-Backup.zip")
    }

    fun launchTagBackupImport() {
        importTagBackupLauncher.launch(
            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
        )
    }

    private fun writeTagBackup(uri: Uri) {
        val resolver = requireContext().contentResolver
        val backup = HailData.createTagBackup()
        val applicationId = BuildConfig.APPLICATION_ID
        val versionName = BuildConfig.VERSION_NAME

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    TagBackupArchive.encode(backup, applicationId, versionName)
                }
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the selected destination")
                    output.use { it.write(bytes) }
                }
                if (isAdded) {
                    Toast.makeText(
                        requireContext(), R.string.tag_backup_export_success, Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tag_backup_error, error.message ?: error.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun readTagBackup(uri: Uri) {
        val resolver = requireContext().contentResolver
        lifecycleScope.launch {
            val backup = try {
                withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri)
                        ?: throw IOException("Could not open the selected backup")
                    input.use(TagBackupArchive::decode)
                }
            } catch (error: Exception) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tag_backup_error, error.message ?: error.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            if (!isAdded) return@launch
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tag_backup_import_title)
                .setMessage(
                    getString(
                        R.string.tag_backup_import_summary,
                        backup.tags.size,
                        backup.apps.size
                    )
                )
                .setPositiveButton(R.string.tag_backup_import_confirm) { _, _ ->
                    val selectedId = selectedTagId()
                    try {
                        HailData.importTagBackup(backup)
                        refreshTags(selectedId)
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.tag_backup_import_success,
                                backup.tags.size,
                                backup.apps.size
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (error: Exception) {
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.tag_backup_error,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun bindTagLongPress() {
        for (position in 0 until binding.tabs.tabCount) {
            binding.tabs.getTabAt(position)?.view?.setOnLongClickListener {
                if (isResumed) TagEditorDialog(this).show()
                true
            }
        }
    }

    override fun onDestroyView() {
        multiselect = false
        selectedList.clear()
        super.onDestroyView()
        _binding = null
    }
}
