package com.photosorter.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 전체 폴더 목록 (화면 위에 덮어서 표시)
 * - 누르면 onPick
 * - ☆ 를 누르면 하단 폴더 버튼(1~5번)에 지정
 * - + 새 폴더: Pictures/이름 으로 만들고 바로 onPick
 */
@Composable
fun FolderScreen(
    vm: MainViewModel,
    title: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var starFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    BackHandler { onClose() }

    val folders = vm.folders().filter {
        query.isBlank() || vm.relative(it.first).contains(query.trim(), ignoreCase = true)
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("←", fontSize = 22.sp, color = Color.White) }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("폴더 검색") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { creating = true }) { Text("+ 새 폴더") }
            }
            Text(
                "☆를 누르면 하단 폴더 버튼(1~5번)에 넣을 수 있어요",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(folders, key = { it.first }) { (dir, count) ->
                    val slot = vm.slots.indexOf(dir)
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(dir) }
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.displayName(dir) + if (dir == vm.cameraDir) "  (카메라)" else "",
                                color = Color.White, fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${vm.relative(dir)} · ${count}개",
                                color = Muted, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { starFor = dir }) {
                            Text(
                                if (slot >= 0) "★${slot + 1}" else "☆",
                                fontSize = 18.sp,
                                color = if (slot >= 0) Color(0xFFFFC94D) else Muted,
                            )
                        }
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("새 폴더") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text("폴더 이름") }, singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("위치: Pictures/${name.trim()}", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = vm.createFolder(name)
                    if (p == null) {
                        Toast.makeText(ctx, "폴더를 만들 수 없어요 (이름을 확인해 주세요)", Toast.LENGTH_SHORT).show()
                    } else {
                        creating = false
                        onPick(p)
                    }
                }) { Text("만들기") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("취소") } },
        )
    }

    starFor?.let { dir ->
        val current = vm.slots.indexOf(dir)
        AlertDialog(
            onDismissRequest = { starFor = null },
            title = { Text("'${vm.displayName(dir)}'을(를) 넣을 버튼") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 5) {
                        val s = vm.slots[i]
                        OutlinedButton(
                            onClick = { vm.setSlot(i, dir); starFor = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${i + 1}번: " + (if (s.isEmpty()) "비어 있음" else vm.displayName(s)) +
                                    if (i == current) "  ✓" else "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (current >= 0) {
                    TextButton(onClick = { vm.clearSlot(current); starFor = null }) { Text("지정 해제") }
                }
            },
            dismissButton = { TextButton(onClick = { starFor = null }) { Text("닫기") } },
        )
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("썸네일 한 줄에 표시할 개수", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in 2..6) {
                        val sel = vm.columns == n
                        if (sel) Button(onClick = { vm.updateColumns(n) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("$n") }
                        else OutlinedButton(onClick = { vm.updateColumns(n) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("$n") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("삭제 전에 확인창 띄우기", modifier = Modifier.weight(1f))
                    Switch(checked = vm.confirmDelete, onCheckedChange = { vm.updateConfirmDelete(it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )
}
