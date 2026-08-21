package org.fossify.musicplayer.dialogs

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.DialogAppUpdateBinding
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.update.AppUpdate
import org.fossify.musicplayer.update.UpdateInstaller
import org.fossify.musicplayer.update.UpdateService
import org.fossify.musicplayer.update.UpdateState
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/** Shows [update], and walks the user through downloading and installing it. */
class AppUpdateDialog(private val activity: BaseSimpleActivity, private val update: AppUpdate) {
    private val binding by activity.viewBinding(DialogAppUpdateBinding::inflate)
    private var dialog: AlertDialog? = null

    init {
        binding.appUpdateVersion.text = activity.getString(R.string.new_update_available_version, update.versionName)
        binding.appUpdateNotes.text = update.notes

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.download, null)
            .setNegativeButton(org.fossify.commons.R.string.later, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.new_update_available) { alertDialog ->
                    dialog = alertDialog
                    EventBus.getDefault().register(this@AppUpdateDialog)
                    render(UpdateService.state)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { primaryActionPressed() }
                    alertDialog.setOnDismissListener { EventBus.getDefault().unregister(this@AppUpdateDialog) }
                }
            }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateStateChanged(event: Events.UpdateStateChanged) = render(UpdateService.state)

    private fun primaryActionPressed() {
        val state = UpdateService.state
        if (state is UpdateState.Downloaded) install(state) else startDownload()
    }

    private fun startDownload() {
        val intent = Intent(activity, UpdateService::class.java).putExtra(UpdateService.EXTRA_UPDATE, update.toJson())
        ContextCompat.startForegroundService(activity, intent)
    }

    private fun install(state: UpdateState.Downloaded) {
        if (!UpdateInstaller.canInstallPackages(activity)) {
            activity.toast(R.string.grant_install_permission)
            activity.startActivity(UpdateInstaller.requestInstallPermissionIntent(activity))
            return
        }

        activity.startActivity(UpdateInstaller.installIntent(activity, state.apkFile))
        dialog?.dismiss()
    }

    private fun render(state: UpdateState) {
        val button = dialog?.getButton(AlertDialog.BUTTON_POSITIVE) ?: return

        binding.apply {
            when (state) {
                is UpdateState.Downloading -> {
                    button.isEnabled = false
                    appUpdateProgress.beVisible()
                    appUpdateProgress.progress = state.percent
                    appUpdateStatus.beVisible()
                    appUpdateStatus.text = activity.getString(R.string.downloading_update_percent, state.percent)
                }

                is UpdateState.Downloaded -> {
                    button.isEnabled = true
                    button.setText(R.string.install)
                    appUpdateProgress.beVisible()
                    appUpdateProgress.progress = 100
                    appUpdateStatus.beGone()
                }

                is UpdateState.Error -> {
                    button.isEnabled = true
                    button.setText(R.string.retry)
                    appUpdateProgress.beGone()
                    appUpdateStatus.beVisible()
                    appUpdateStatus.text = state.message
                }

                else -> {
                    button.isEnabled = true
                    button.setText(org.fossify.commons.R.string.download)
                    appUpdateProgress.beGone()
                    appUpdateStatus.beGone()
                }
            }
        }
    }
}
