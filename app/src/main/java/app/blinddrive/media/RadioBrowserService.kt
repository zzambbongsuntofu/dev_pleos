package app.blinddrive.media

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import app.blinddrive.story.SampleStories
import app.blinddrive.ui.MainActivity

/**
 * Lets the Pleos media library list this app.
 * Browsing does not play audio and does not send anything off the device.
 */
class RadioBrowserService : MediaBrowserServiceCompat() {
    private lateinit var session: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "BlindDrive").apply {
            setCallback(callback)
            setPlaybackState(idle())
            isActive = true
        }
        sessionToken = session.sessionToken
    }

    override fun onDestroy() {
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot = BrowserRoot(ROOT, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val items = when (parentId) {
            ROOT -> mutableListOf(radioFolder())
            RADIO -> SampleStories.items.map { story -> playable(story.id, story.text) }.toMutableList()
            else -> mutableListOf()
        }
        result.sendResult(items)
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            showApp()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val story = SampleStories.items.firstOrNull { it.id == mediaId }
            if (story != null) {
                session.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, story.id)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "블라인드 드라이브")
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, story.text)
                        .build(),
                )
            }
            session.setPlaybackState(idle())
            showApp()
        }
    }

    private fun radioFolder(): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(RADIO)
            .setTitle("라디오")
            .setSubtitle("샘플 이야기")
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )
    }

    private fun playable(id: String, text: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle("이야기")
            .setSubtitle(text)
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )
    }

    private fun idle(): PlaybackStateCompat {
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
            )
            .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
            .build()
    }

    private fun showApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val ROOT = "blind-drive"
        const val RADIO = "blind-drive-radio"
    }
}
