@file:OptIn(ExperimentalMaterial3Api::class)

package com.photosorter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val shortFmt = DateTimeFormatter.ofPattern("yy.M.d", Locale.KOREAN)
private const val DAY_MS = 86_400_000L

fun formatDuration(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun GalleryScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    var showFolders by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val list = vm.visible

    Column(Modifier.fillMaxSize()) {
        // ---- 상단: 폴더 선택 ----
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showFolders = true }, modifier = Modifier.weight(1f)) {
                Text(
                    "📁 ${vm.displayName(vm.currentDir)} ▾",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("${list.size}개", color = Muted, fontSize = 14.sp)
            TextButton(onClick = { showSettings = true }) { Text("⚙", fontSize = 20.sp, color = Muted) }
        }

        FilterBar(vm)

        // ---- 썸네일 격자 ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !vm.loadedOnce -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    "표시할 항목이 없어요", color = Muted,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> MediaGrid(vm, list, onOpen)
            }
        }
    }

    if (showFolders) {
        FolderScreen(
            vm = vm,
            title = "볼 폴더 선택",
            onPick = { vm.openFolder(it); showFolders = false },
            onClose = { showFolders = false },
        )
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun MediaGrid(vm: MainViewModel, list: List<MediaItem>, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val hearts = vm.hearts
    LazyVerticalGrid(
        columns = GridCells.Fixed(vm.columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val thumb: @Composable (MediaItem) -> Unit = { m ->
            Box(
                Modifier.aspectRatio(1f).background(Panel).clickable { onOpen(m.path) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(File(m.path)).size(320).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (m.isVideo) {
                    Text(
                        "▶ ${formatDuration(m.duration)}",
                        color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .background(Color(0x99000000)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (m.path in hearts) {
                    Text(
                        "♥", color = HeartRed, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                    )
                }
            }
        }

        if (vm.sort.isDate) {
            // 날짜별 묶음 (groupBy는 순서를 유지함)
            list.groupBy { it.day }.forEach { (day, group) ->
                item(key = "h$day", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LocalDate.ofEpochDay(day).format(headerFmt),
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${group.size}", color = Muted, fontSize = 13.sp)
                    }
                }
                items(group, key = { it.path }) { thumb(it) }
            }
        } else {
            items(list, key = { it.path }) { thumb(it) }
        }
    }
}

@Composable
private fun FilterBar(vm: MainViewModel) {
    var typeOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 정렬
        Box {
            FilterChip(selected = true, onClick = { sortOpen = true }, label = { Text("↕ ${vm.sort.label}") })
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortMode.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text((if (s == vm.sort) "✓ " else "   ") + s.label) },
                        onClick = { vm.updateSort(s); sortOpen = false },
                    )
                }
            }
        }
        // 종류
        Box {
            val lenSort = vm.sort.isLength
            FilterChip(
                selected = lenSort || vm.typeFilter != TypeFilter.ALL,
                onClick = { if (!lenSort) typeOpen = true },
                label = { Text(if (lenSort) "동영상" else vm.typeFilter.label) },
            )
            DropdownMenu(expanded = typeOpen, onDismissRequest = { typeOpen = false }) {
                TypeFilter.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text((if (t == vm.typeFilter) "✓ " else "   ") + t.label) },
                        onClick = { vm.updateType(t); typeOpen = false },
                    )
                }
            }
        }
        // 기간
        val rs = vm.rangeStart
        val re = vm.rangeEnd
        val rangeLabel = when {
            rs == null && re == null -> "전체 기간"
            else -> "${rs?.let { LocalDate.ofEpochDay(it).format(shortFmt) } ?: ""}~${re?.let { LocalDate.ofEpochDay(it).format(shortFmt) } ?: ""}"
        }
        FilterChip(
            selected = rs != null || re != null,
            onClick = { rangeOpen = true },
            label = { Text("📅 $rangeLabel") },
        )
        // 하트만
        FilterChip(
            selected = vm.heartsOnly,
            onClick = { vm.updateHeartsOnly(!vm.heartsOnly) },
            label = { Text("♥ 하트만") },
        )
    }

    if (rangeOpen) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = vm.rangeStart?.let { it * DAY_MS },
            initialSelectedEndDateMillis = vm.rangeEnd?.let { it * DAY_MS },
        )
        DatePickerDialog(
            onDismissRequest = { rangeOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis?.let { Math.floorDiv(it, DAY_MS) }
                    val e = state.selectedEndDateMillis?.let { Math.floorDiv(it, DAY_MS) } ?: s
                    vm.updateRange(s, e)
                    rangeOpen = false
                }) { Text("적용") }
            },
            dismissButton = {
                TextButton(onClick = { vm.updateRange(null, null); rangeOpen = false }) { Text("전체 기간") }
            },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f))
        }
    }
}
