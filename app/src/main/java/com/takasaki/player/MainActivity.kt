package com.takasaki.player

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 端末のライト／ダーク設定に追従する
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App(vm: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LaunchedEffect(granted) {
        if (granted) vm.loadTracks() else launcher.launch(audioPermission)
    }

    if (!granted) {
        PermissionScreen(onRequest = { launcher.launch(audioPermission) })
    } else {
        PlayerScreen(vm)
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("端末内の音楽を読み込むには権限が必要です")
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("権限を許可する")
        }
    }
}

@Composable
private fun PlayerScreen(vm: PlayerViewModel) {
    // 再生中は 500ms ごとに再生位置を更新する
    LaunchedEffect(vm.isPlaying) {
        while (vm.isPlaying) {
            vm.refreshPosition()
            delay(500)
        }
    }

    // アルバム詳細を開いているときは、戻るボタンでアルバム一覧に戻る
    val album = vm.selectedAlbum
    BackHandler(enabled = album != null) { vm.closeAlbum() }

    Scaffold(
        bottomBar = { if (vm.currentTrack != null) ControlBar(vm) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.tracks.isEmpty() -> EmptyMessage()
                album != null -> AlbumDetail(vm, album)
                else -> {
                    TabRow(selectedTabIndex = vm.selectedTab) {
                        Tab(selected = vm.selectedTab == 0, onClick = { vm.selectedTab = 0 }, text = { Text("曲") })
                        Tab(selected = vm.selectedTab == 1, onClick = { vm.selectedTab = 1 }, text = { Text("アルバム") })
                    }
                    if (vm.selectedTab == 0) SongList(vm) else AlbumGrid(vm)
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("音楽ファイルが見つかりません")
    }
}

/** 「曲」タブ: 全曲を一覧表示し、タップで全曲をキューにして再生 */
@Composable
private fun SongList(vm: PlayerViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(vm.tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                selected = track.id.toString() == vm.currentMediaId,
                onClick = { vm.play(vm.tracks, index) },
                leading = { AlbumArt(track.albumArtUri, Modifier.size(48.dp)) },
            )
            HorizontalDivider()
        }
    }
}

/** 「アルバム」タブ: アルバムを 2 列のグリッドで表示 */
@Composable
private fun AlbumGrid(vm: PlayerViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(vm.albums, key = { it.id }) { album ->
            Column(Modifier.clickable { vm.openAlbum(album) }) {
                AlbumArt(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "${album.artist} ・ ${album.tracks.size}曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** アルバム詳細: ジャケットと情報、再生ボタン、収録曲の一覧 */
@Composable
private fun AlbumDetail(vm: PlayerViewModel, album: Album) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::closeAlbum) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "アルバム一覧に戻る")
        }
        Text("アルバム", style = MaterialTheme.typography.titleMedium)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        AlbumArt(album.artUri, Modifier.size(128.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(album.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${album.tracks.size}曲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.play(album.tracks, 0) }) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("再生")
            }
        }
    }
    HorizontalDivider()
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                selected = track.id.toString() == vm.currentMediaId,
                onClick = { vm.play(album.tracks, index) },
                leading = {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(32.dp),
                    )
                },
            )
            HorizontalDivider()
        }
    }
}

/** アルバムアート。取得できない場合は音符アイコンを表示する */
@Composable
private fun AlbumArt(uri: Uri?, modifier: Modifier) {
    val placeholder = painterResource(R.drawable.ic_music_note)
    AsyncImage(
        model = uri,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun TrackRow(
    track: Track,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ControlBar(vm: PlayerViewModel) {
    val track = vm.currentTrack ?: return
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track.albumArtUri, Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // ドラッグ中はローカル値を表示し、指を離した時点で1回だけシークする
            val duration = track.durationMs.toFloat().coerceAtLeast(1f)
            var dragging by remember(track.id) { mutableStateOf<Float?>(null) }
            Slider(
                value = (dragging ?: vm.positionMs.toFloat()).coerceIn(0f, duration),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo(it.toInt()) }
                    dragging = null
                },
                valueRange = 0f..duration,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(dragging?.toInt() ?: vm.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(
                        painterResource(R.drawable.ic_shuffle),
                        contentDescription = if (vm.shuffleEnabled) "シャッフル オン" else "シャッフル オフ",
                        tint = if (vm.shuffleEnabled) activeColor else inactiveColor,
                    )
                }
                IconButton(onClick = vm::previous) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "前の曲")
                }
                IconButton(onClick = vm::togglePlayPause) {
                    Icon(
                        painterResource(if (vm.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (vm.isPlaying) "一時停止" else "再生",
                    )
                }
                IconButton(onClick = vm::next) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "次の曲")
                }
                IconButton(onClick = vm::cycleRepeatMode) {
                    val (icon, label, active) = when (vm.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Triple(R.drawable.ic_repeat_one, "1曲リピート", true)
                        Player.REPEAT_MODE_ALL -> Triple(R.drawable.ic_repeat, "全曲リピート", true)
                        else -> Triple(R.drawable.ic_repeat, "リピートなし", false)
                    }
                    Icon(
                        painterResource(icon),
                        contentDescription = label,
                        tint = if (active) activeColor else inactiveColor,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
