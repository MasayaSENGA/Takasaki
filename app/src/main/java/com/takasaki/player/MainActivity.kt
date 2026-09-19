package com.takasaki.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Scaffold(
        bottomBar = { if (vm.currentTrack != null) ControlBar(vm) },
    ) { padding ->
        if (vm.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("音楽ファイルが見つかりません")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(vm.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        selected = index == vm.currentIndex,
                        onClick = { vm.play(index) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
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
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
