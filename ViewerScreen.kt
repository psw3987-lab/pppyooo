package com.photosorter.app

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File

private val SlotColors = listOf(
    Color(0xFF3FB37F), Color(0xFF4F8CFF), Color(0xFFD9A23A), Color(0xFFA371F7), Color(0xFF2BB5C4),
)
private val MenuColor = Color(0xFF2A2D33)

@Composable
fun ViewerScreen(vm: MainViewModel, startPath: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 열 때의 필터/정렬 순서를 그대로 따라감
    val queue = remember { vm.visible.toMutableStateList() }
    var pos by remember { mutableIntStateOf(queue.indexOfFirst { it.path == startPath }.coerceAtLeast(0)) }
    var busy by remember { mutableStateOf(false) }
    var dup by remember { mutableStateOf<Pair<MediaItem, String>?>(null) }
    var confirmDel by remember { mutableStateOf<MediaItem?>(null) }
    var folderPick by remember { mutableStateOf<Int?>(null) } // -1: 이동, 0~4: 해당 칸 지정
    var showSettings by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    if (pos > queue.lastIndex) pos = queue.lastIndex
    val item = queue[pos]

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun advance() {
        if (pos < queue.lastIndex) pos++ else { toast("마지막 항목이에요"); onClose() }
    }
    fun prev() { if (pos > 0) pos-- else toast("첫 항목이에요") }

    // 현재 항목이 목록에서 빠짐 → 같은 위치에 다음 항목이 옴
    fun removedCurrent() {
        queue.removeAt(pos)
        when {
            queue.isEmpty() -> { toast("모두 정리했어요"); onClose() }
            pos > queue.lastIndex -> { toast("마지막 항목이에요"); onClose() }
        }
    }

    fun moveTo(dest: String) {
        if (busy) return
        busy = true
        scope.launch {
            when (vm.move(item, dest)) {
                MoveResult.SameFolder -> advance()
                MoveResult.Moved -> removedCurrent()
                MoveResult.Exists -> dup = item to dest
                MoveResult.Failed -> toast("이동하지 못했어요")
            }
            busy = false
        }
    }

    fun deleteNow(m: MediaItem) {
        if (busy) return
        busy = true
        scope.launch {
            if (vm.delete(m)) removedCurrent() else toast("삭제하지 못했어요")
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ---- 상단 바 ----
        Row(
            Modifier.fillMaxWidth().background(Bg).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("←", fontSize = 22.sp, color = Color.White) }
            Text("${pos + 1} / ${queue.size}", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (item.path in vm.hearts) Text("♥", color = HeartRed, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        }

        // ---- 사진 / 동영상 ----
        var drag by remember { mutableStateOf(0f) }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(pos, queue.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -120f) advance() else if (drag > 120f) prev()
                        },
                    ) { _, d -> drag += d }
                },
            contentAlignment = Alignment.Center,
        ) {
            key(item.path) {
                if (item.isVideo) VideoPlayer(item.path)
                else AsyncImage(
                    model = ImageRequest.Builder(ctx).data(File(item.path)).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            item.name, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(Bg).padding(top = 4.dp),
        )

        // ---- 하단 고정 버튼 10개 ----
        Column(
            Modifier.fillMaxWidth().background(Bg).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 5) {
                    val dir = vm.slots[i]
                    if (dir.isEmpty()) {
                        GridButton("+ 지정", Color(0xFF22252A), Muted) { folderPick = i }
                    } else {
                        val here = dir == item.dir
                        GridButton(
                            vm.displayName(dir),
                            if (here) SlotColors[i].copy(alpha = 0.35f) else SlotColors[i],
                            Color.White,
                        ) { moveTo(dir) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GridButton("다른폴더", MenuColor, Color.White) { folderPick = -1 }
                GridButton("삭제", MenuColor, Color(0xFFFF7A70)) {
                    if (vm.confirmDelete) confirmDel = item else deleteNow(item)
                }
                val hearted = item.path in vm.hearts
                GridButton(if (hearted) "♥ 해제" else "♡ 하트", MenuColor, if (hearted) HeartRed else Color.White) {
                    vm.toggleHeart(item.path)
                }
                GridButton("공유", MenuColor, Color.White) {
                    try {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(item.path))
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(if (item.isVideo) "video/*" else "image/*")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(send, "공유"))
                    } catch (e: Exception) {
                        toast("공유할 수 없어요")
                    }
                }
                GridButton("설정", MenuColor, Color.White) { showSettings = true }
            }
        }
    }

    // ---- 같은 이름 파일이 이미 있을 때 ----
    dup?.let { (m, dest) ->
        val curName = vm.displayName(m.dir)
        val destName = vm.displayName(dest)
        AlertDialog(
            onDismissRequest = { dup = null; advance() },
            title = { Text("이미 존재하는 파일") },
            text = {
                Text(
                    "'$destName' 폴더에 같은 이름의 파일이 이미 있습니다.\n\n${m.name}\n\n" +
                        "현재 폴더('$curName')에 있는 이 파일을 삭제할까요?\n" +
                        "('$destName' 폴더의 파일은 그대로 남습니다)"
                )
            },
            confirmButton = {
                TextButton(onClick = { dup = null; deleteNow(m) }) {
                    Text("현재 폴더('$curName')에서 삭제", color = Color(0xFFFF7A70))
                }
            },
            dismissButton = {
                TextButton(onClick = { dup = null; advance() }) { Text("삭제 안 함") }
            },
        )
    }

    // ---- 삭제 확인 ----
    confirmDel?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDel = null },
            title = { Text("영구 삭제") },
            text = { Text("이 파일을 영구 삭제할까요?\n복구할 수 없어요.\n\n${m.name}") },
            confirmButton = {
                TextButton(onClick = { confirmDel = null; deleteNow(m) }) {
                    Text("삭제", color = Color(0xFFFF7A70))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("취소") } },
        )
    }

    // ---- 폴더 목록 ----
    folderPick?.let { mode ->
        if (mode < 0) {
            FolderScreen(
                vm = vm,
                title = "이동할 폴더 선택",
                onPick = { dir -> folderPick = null; moveTo(dir) },
                onClose = { folderPick = null },
            )
        } else {
            FolderScreen(
                vm = vm,
                title = "${mode + 1}번 버튼에 넣을 폴더",
                onPick = { dir -> vm.setSlot(mode, dir); folderPick = null },
                onClose = { folderPick = null },
            )
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun RowScope.GridButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

/** 소리 없이 자동 재생, 화면을 누르면 소리 켜기/끄기 */
@Composable
private fun VideoPlayer(path: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var muted by remember { mutableStateOf(true) }
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            muted = !muted
            val v = if (muted) 0f else 1f
            try { player?.setVolume(v, v) } catch (_: Exception) {}
        },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        player = mp
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    setVideoPath(path)
                }
            },
            onRelease = { v ->
                player = null
                v.stopPlayback()
            },
            modifier = Modifier.wrapContentSize(),
        )
        Text(
            if (muted) "🔇 눌러서 소리 켜기" else "🔊",
            color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
