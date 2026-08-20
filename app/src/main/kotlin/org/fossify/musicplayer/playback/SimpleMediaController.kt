package org.fossify.musicplayer.playback

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.media3.common.Player.Listener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.fossify.musicplayer.extensions.getOrNull
import org.fossify.musicplayer.extensions.runOnPlayerThread
import org.fossify.musicplayer.playback.PlaybackService
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class SimpleMediaController(val context: Application) {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    /**
     * Every listener currently meant to be attached, replayed onto whatever [MediaController]
     * [controller] points at.
     *
     * A rebuild - the playback service having been stopped and the old controller left
     * disconnected - swaps in a whole new [MediaController] instance. Without this, whichever
     * screen was already listening (added back in its own onStart, long before the rebuild) would
     * stay attached to the disconnected one and never hear from the session again.
     *
     * Added to and removed from on the main thread by activity lifecycle callbacks, but replayed
     * from whatever thread completes the connection future - hence a set safe for that.
     */
    private val listeners = CopyOnWriteArraySet<Listener>()

    @Synchronized
    fun createControllerAsync() {
        controllerFuture = MediaController
            .Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()

        controllerFuture.addListener({
            controller = getControllerSync()
            controller?.let { newController -> listeners.forEach(newController::addListener) }
        }, MoreExecutors.directExecutor())
    }

    private fun getControllerSync() = controllerFuture.getOrNull()

    private fun shouldCreateNewController(): Boolean {
        return if (!::controllerFuture.isInitialized) {
            return true
        } else {
            controllerFuture.isCancelled || controllerFuture.isDone && getControllerSync()?.isConnected == false
        }
    }

    private fun acquireController(callback: (() -> Unit)? = null) {
        executorService.execute {
            if (shouldCreateNewController()) {
                createControllerAsync()
            } else {
                controller = getControllerSync()
            }

            callback?.invoke()
        }
    }

    fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun withController(callback: MediaController.() -> Unit) {
        val controller = controller
        if (controller != null && controller.isConnected) {
            controller.runOnPlayerThread(callback)
        } else {
            acquireController {
                getControllerSync()?.runOnPlayerThread(callback)
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        withController {
            addListener(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        withController {
            removeListener(listener)
        }
    }

    companion object {
        private var instance: SimpleMediaController? = null

        fun getInstance(context: Context): SimpleMediaController {
            if (instance == null) {
                instance = SimpleMediaController(context.applicationContext as Application)
            }

            return instance!!
        }

        fun destroyInstance() {
            instance?.releaseController()
            instance = null
        }
    }
}
