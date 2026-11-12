package com.photosorter.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0F1012)
val Panel = Color(0xFF1A1C20)
val Line = Color(0xFF2A2D33)
val Muted = Color(0xFF8B9099)
val Accent = Color(0xFF4F8CFF)
val HeartRed = Color(0xFFFF4D6A)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = Panel,
                    onSurface = Color(0xFFECEEF1),
                    onBackground = Color(0xFFECEEF1),
                )
            ) {
                Box(Modifier.fillMaxSize().background(Bg)) { AppRoot(vm) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkAccess()
    }
}

@Composable
fun AppRoot(vm: MainViewModel) {
    if (!vm.hasAccess) {
        PermissionScreen(vm)
        return
    }
    var viewerStart by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        GalleryScreen(vm, onOpen = { viewerStart = it })
        val start = viewerStart
        if (start != null) {
            key(start) {
                ViewerScreen(vm, startPath = start, onClose = { viewerStart = null })
            }
        }
    }
}

@Composable
fun PermissionScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.checkAccess() }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📷 사진 분류", fontSize = 26.sp, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(
            "사진과 동영상을 다른 폴더로 옮기고 삭제하려면\n'모든 파일 접근' 권한이 필요해요.\n\n" +
                "다음 화면에서 이 앱을 찾아 권한을 켜 주세요.",
            color = Muted, textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + ctx.packageName)
                        )
                    )
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                )
            }
        }) { Text("권한 허용하러 가기", fontSize = 16.sp) }
    }
}
