package com.jackwallner.pelojack.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Exists only so Android lets Pelojack see other apps' media sessions. */
class MediaListener : NotificationListenerService()

data class Track(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: Bitmap?,
    val appPackage: String,
)

/** What Spotify (or any music app) is playing, with transport controls. */
class NowPlaying(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val mutableTrack = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = mutableTrack

    private val mutableAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = mutableAccess

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    /** Call when the app comes to the front; access may have been granted meanwhile. */
    fun refresh() {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        mutableAccess.value = granted
        if (!granted) return
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            pick(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            mutableAccess.value = false
        }
    }

    fun togglePlay() {
        val controls = controller?.transportControls ?: return
        if (mutableTrack.value?.playing == true) controls.pause() else controls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()

    fun previous() = controller?.transportControls?.skipToPrevious()

    private fun pick(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            chosen?.registerCallback(controllerCallback, handler)
            controller = chosen
        }
        publish()
    }

    private fun publish() {
        val current = controller
        val metadata = current?.metadata
        mutableTrack.value = if (current == null || metadata == null) {
            null
        } else {
            Track(
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                playing = current.playbackState?.state == PlaybackState.STATE_PLAYING,
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
                appPackage = current.packageName,
            )
        }
    }
}
