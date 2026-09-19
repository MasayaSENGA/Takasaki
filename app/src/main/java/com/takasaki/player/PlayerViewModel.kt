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
    val albumId: Long,
    val album: String,
    /** ディスク番号込みのトラック番号（MediaStore の TRACK 列。並び順にだけ使う） */
    val trackNumber: Int,
    /** アルバムアート（無い場合は読み込みに失敗するのでプレースホルダー表示になる） */
    val albumArtUri: Uri?,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val tracks: List<Track>,
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
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_ALL)
        private set

    /** 再生中の曲の ID（MediaItem の mediaId と同じ文字列） */
    var currentMediaId by mutableStateOf<String?>(null)
        private set
    val currentTrack: Track? by derivedStateOf { tracks.firstOrNull { it.id.toString() == currentMediaId } }

    /** 曲一覧をアルバム ID でまとめたもの。アルバム名順、アルバム内はトラック番号順 */
    val albums: List<Album> by derivedStateOf {
        tracks.groupBy { it.albumId }.map { (id, list) ->
            val artists = list.map { it.artist }.distinct()
            Album(
                id = id,
                title = list.first().album,
                artist = if (artists.size == 1) artists.first() else "Various Artists",
                artUri = list.first().albumArtUri,
                // トラック番号順。番号の無い曲（0 以下）は末尾に回す
                tracks = list.sortedWith(
                    compareBy({ if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }, { it.title.lowercase() })
                ),
            )
        }.sortedBy { it.title.lowercase() }
    }

    // 画面の表示状態（回転しても保持されるよう ViewModel に置く）
    var selectedTab by mutableIntStateOf(0)
    var selectedAlbumId by mutableStateOf<Long?>(null)
        private set
    val selectedAlbum: Album? get() = albums.firstOrNull { it.id == selectedAlbumId }

    fun openAlbum(album: Album) {
        selectedAlbumId = album.id
    }

    fun closeAlbum() {
        selectedAlbumId = null
    }

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
        shuffleEnabled = player.shuffleModeEnabled
        repeatMode = player.repeatMode
    }

    /** 端末内の音楽ファイルを MediaStore から読み込む */
    fun loadTracks() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
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
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown",
                    durationMs = cursor.getInt(durationCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    albumId = cursor.getLong(albumIdCol),
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    trackNumber = cursor.getInt(trackCol),
                    albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, cursor.getLong(albumIdCol)),
                )
            }
        }
        tracks = result
    }

    /** queue（全曲やアルバムの曲一覧）をプレイリストとしてセットし、index の曲から再生する */
    fun play(queue: List<Track>, index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        when {
            c.mediaItemCount == 0 -> if (tracks.isNotEmpty()) play(tracks, 0)
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

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** 全曲リピート → 1曲リピート → リピートなし の順に切り替える */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
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

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()
}
