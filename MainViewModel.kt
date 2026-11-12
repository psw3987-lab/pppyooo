package com.photosorter.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TypeFilter(val label: String) { ALL("전체"), PHOTO("사진"), VIDEO("동영상") }

enum class SortMode(val label: String) {
    DATE_ASC("날짜순"), DATE_DESC("날짜역순"),
    SIZE_ASC("용량순"), SIZE_DESC("용량역순"),
    LEN_ASC("길이순"), LEN_DESC("길이역순");

    val isLength get() = this == LEN_ASC || this == LEN_DESC
    val isDate get() = this == DATE_ASC || this == DATE_DESC
}

sealed interface MoveResult {
    data object SameFolder : MoveResult
    data object Moved : MoveResult
    data object Exists : MoveResult
    data object Failed : MoveResult
}

private inline fun <reified T : Enum<T>> enumOr(s: String?, d: T): T =
    runCatching { enumValueOf<T>(s!!) }.getOrDefault(d)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath
    val cameraDir: String =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").absolutePath

    var hasAccess by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var loadedOnce by mutableStateOf(false); private set
    val items = mutableStateListOf<MediaItem>()

    // ---- 보기 설정 ----
    var currentDir by mutableStateOf(prefs.getString("dir", null) ?: cameraDir); private set
    var typeFilter by mutableStateOf(enumOr(prefs.getString("type", null), TypeFilter.ALL)); private set
    var sort by mutableStateOf(enumOr(prefs.getString("sort", null), SortMode.DATE_DESC)); private set
    var rangeStart by mutableStateOf(if (prefs.contains("rs")) prefs.getLong("rs", 0) else null); private set
    var rangeEnd by mutableStateOf(if (prefs.contains("re")) prefs.getLong("re", 0) else null); private set
    var heartsOnly by mutableStateOf(prefs.getBoolean("heartsOnly", false)); private set

    // ---- 설정 ----
    var columns by mutableStateOf(prefs.getInt("cols", 4)); private set
    var confirmDelete by mutableStateOf(prefs.getBoolean("confirmDelete", true)); private set

    // ---- 하트 / 폴더 버튼 / 만든 폴더 ----
    var hearts by mutableStateOf(prefs.getStringSet("hearts", emptySet())!!.toSet()); private set
    val slots = mutableStateListOf<String>().apply {
        repeat(5) { i -> add(prefs.getString("slot$i", "") ?: "") }
    }
    private var extraFolders by mutableStateOf(prefs.getStringSet("extra", emptySet())!!.toSet())

    /** 썸네일 화면에 보이는 목록 (필터 + 정렬 적용) */
    val visible: List<MediaItem> by derivedStateOf {
        val dir = currentDir
        val s = sort
        val type = if (s.isLength) TypeFilter.VIDEO else typeFilter
        val rs = rangeStart
        val re = rangeEnd
        val ho = heartsOnly
        val h = hearts
        val list = items.filter { m ->
            m.dir == dir &&
                when (type) {
                    TypeFilter.ALL -> true
                    TypeFilter.PHOTO -> !m.isVideo
                    TypeFilter.VIDEO -> m.isVideo
                } &&
                (rs == null || m.day >= rs) &&
                (re == null || m.day <= re) &&
                (!ho || m.path in h)
        }
        when (s) {
            SortMode.DATE_ASC -> list.sortedBy { it.date }
            SortMode.DATE_DESC -> list.sortedByDescending { it.date }
            SortMode.SIZE_ASC -> list.sortedBy { it.size }
            SortMode.SIZE_DESC -> list.sortedByDescending { it.size }
            SortMode.LEN_ASC -> list.sortedBy { it.duration }
            SortMode.LEN_DESC -> list.sortedByDescending { it.duration }
        }
    }

    // ---------------- 권한 / 불러오기 ----------------

    fun checkAccess() {
        hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (hasAccess) load()
    }

    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MediaRepo.query(ctx) }
            items.clear()
            items.addAll(list)
            loading = false
            loadedOnce = true
        }
    }

    // ---------------- 보기 설정 변경 ----------------

    fun openFolder(dir: String) { currentDir = dir; prefs.edit().putString("dir", dir).apply() }
    fun updateType(t: TypeFilter) { typeFilter = t; prefs.edit().putString("type", t.name).apply() }
    fun updateSort(s: SortMode) { sort = s; prefs.edit().putString("sort", s.name).apply() }
    fun updateHeartsOnly(v: Boolean) { heartsOnly = v; prefs.edit().putBoolean("heartsOnly", v).apply() }
    fun updateRange(start: Long?, end: Long?) {
        rangeStart = start; rangeEnd = end
        prefs.edit().apply {
            if (start == null) remove("rs") else putLong("rs", start)
            if (end == null) remove("re") else putLong("re", end)
        }.apply()
    }
    fun updateColumns(n: Int) { columns = n; prefs.edit().putInt("cols", n).apply() }
    fun updateConfirmDelete(v: Boolean) { confirmDelete = v; prefs.edit().putBoolean("confirmDelete", v).apply() }

    // ---------------- 하트 / 폴더 버튼 ----------------

    private fun saveHearts(h: Set<String>) { hearts = h; prefs.edit().putStringSet("hearts", h).apply() }
    fun toggleHeart(path: String) = saveHearts(if (path in hearts) hearts - path else hearts + path)

    fun setSlot(i: Int, dir: String) {
        // 같은 폴더가 다른 칸에 있으면 그 칸은 비움
        for (k in slots.indices) if (k != i && slots[k] == dir) { slots[k] = ""; prefs.edit().putString("slot$k", "").apply() }
        slots[i] = dir
        prefs.edit().putString("slot$i", dir).apply()
    }
    fun clearSlot(i: Int) { slots[i] = ""; prefs.edit().putString("slot$i", "").apply() }

    // ---------------- 폴더 ----------------

    fun displayName(dir: String): String = dir.substringAfterLast('/')
    fun relative(dir: String): String = dir.removePrefix(rootPath).trimStart('/').ifEmpty { "/" }

    /** 전체 폴더 목록: (경로, 항목 수) */
    fun folders(): List<Pair<String, Int>> {
        val counts = items.groupingBy { it.dir }.eachCount()
        val all = LinkedHashSet<String>()
        all.addAll(counts.keys)
        all.addAll(extraFolders.filter { File(it).isDirectory })
        all.addAll(slots.filter { it.isNotEmpty() })
        all.add(currentDir)
        return all.map { it to (counts[it] ?: 0) }
            .sortedWith(compareBy({ it.first != cameraDir }, { displayName(it.first).lowercase() }))
    }

    /** Pictures/이름 으로 새 폴더 생성. 성공하면 경로, 실패하면 null */
    fun createFolder(name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/') || n == "." || n == "..") return null
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), n)
        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null
        val p = dir.absolutePath
        extraFolders = extraFolders + p
        prefs.edit().putStringSet("extra", extraFolders).apply()
        return p
    }

    // ---------------- 이동 / 삭제 ----------------

    fun existsIn(item: MediaItem, destDir: String): Boolean = File(destDir, item.name).exists()

    suspend fun move(item: MediaItem, destDir: String): MoveResult {
        if (item.dir == destDir) return MoveResult.SameFolder
        val dest = File(destDir, item.name)
        if (dest.exists()) return MoveResult.Exists
        val ok = withContext(Dispatchers.IO) { MediaRepo.moveFile(File(item.path), dest) }
        if (!ok) return MoveResult.Failed
        MediaRepo.scan(ctx, item.path, dest.absolutePath)
        val moved = item.copy(path = dest.absolutePath)
        val idx = items.indexOfFirst { it.path == item.path }
        if (idx >= 0) items[idx] = moved else items.add(moved)
        if (item.path in hearts) saveHearts(hearts - item.path + moved.path)
        return MoveResult.Moved
    }

    suspend fun delete(item: MediaItem): Boolean {
        val ok = withContext(Dispatchers.IO) {
            val f = File(item.path)
            !f.exists() || f.delete()
        }
        if (!ok) return false
        MediaRepo.scan(ctx, item.path)
        items.removeAll { it.path == item.path }
        if (item.path in hearts) saveHearts(hearts - item.path)
        return true
    }
}
