package com.takasaki.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Int,
    val uri: Uri,
)

/**
 * UI 用の状態を持ち、実際の再生は PlaybackService（ExoPlayer）に MediaController 経由で指示する。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set

    private var currentMediaId by mutableStateOf<String?>(null)
    val currentIndex: Int by derivedStateOf { tracks.indexOfFirst { it.id.toString() == currentMediaId } }
    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState(player)
    }

    init {
        // サービスに接続する（サービスが既に再生中なら、その状態がそのまま UI に反映される）
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            syncState(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun syncState(player: Player) {
        isPlaying = player.isPlaying
        currentMediaId = player.currentMediaItem?.mediaId
        positionMs = player.currentPosition.toInt()
    }

    /** 端末内の音楽ファイルを MediaStore から読み込む */
    fun loadTracks() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<Track>()

        getApplication<Application>().contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown",
                    durationMs = cursor.getInt(durationCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                )
            }
        }
        tracks = result
    }

    /** 一覧全体をプレイリストとしてセットし、index の曲から再生する */
    fun play(index: Int) {
        val c = controller ?: return
        if (index !in tracks.indices) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        when {
            c.mediaItemCount == 0 -> if (tracks.isNotEmpty()) play(0)
            c.isPlaying -> c.pause()
            else -> c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Int) {
        controller?.seekTo(ms.toLong())
        positionMs = ms
    }

    /** UI 側から定期的に呼び、再生位置を更新する */
    fun refreshPosition() {
        controller?.let { positionMs = it.currentPosition.toInt() }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
        .build()
}
