package org.fossify.musicplayer.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.fossify.musicplayer.activities.ScreenHostActivity

/**
 * Builds the launch [Intent] for a [Dest]: the shared host, carrying the destination.
 * Generic by design - adding a destination never touches this file.
 */
fun Dest.toIntent(context: Context): Intent =
    Intent(context, ScreenHostActivity::class.java)
        .putExtra(ScreenHostActivity.EXTRA_DEST, this)

/**
 * [AppNavigator] backed by Activity intents. Provided by the host so any composable can navigate
 * ([go]) or pop the current screen ([back]), with the platform owning the back stack, transitions
 * and predictive back - there is no nav graph and no custom back stack to keep in step.
 */
class IntentNavigator(private val activity: Activity) : AppNavigator {
    override fun go(dest: Dest) = activity.startActivity(dest.toIntent(activity))

    override fun back() {
        activity.finish()
    }
}
