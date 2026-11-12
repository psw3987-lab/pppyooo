package com.photosorter.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class MediaItem(
    val path: String,
    val isVideo: Boolean,
    val size: Long,
    val date: Long,      // 촬영(또는 수정) 시각, ms
    val day: Long,       // 로컬 날짜 (epochDay)
    val duration: Long,  // 동영상 길이, ms (사진은 0)
) {
    val name: String get() = path.substringAfterLast('/')
    val dir: String get() = path.substringBeforeLast('/')
}

object MediaRepo {

    fun query(ctx: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val zone = ZoneId.systemDefault()
        queryUri(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, zone, out)
        queryUri(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, zone, out)
        return out
    }

    @Suppress("DEPRECATION")
    private fun queryUri(ctx: Context, uri: Uri, video: Boolean, zone: ZoneId, out: MutableList<MediaItem>) {
        val proj = mutableListOf<String>(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            "datetaken",
        )
        if (video) proj.add("duration")
        val c = try {
            ctx.contentResolver.query(uri, proj.toTypedArray(), null, null, null)
        } catch (e: Exception) {
            null
        } ?: return
        c.use {
            val iData = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iSize = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iMod = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iTaken = it.getColumnIndex("datetaken")
            val iDur = if (video) it.getColumnIndex("duration") else -1
            while (it.moveToNext()) {
                val path = it.getString(iData) ?: continue
                val f = File(path)
                if (!f.exists()) continue
                val taken = if (iTaken >= 0) it.getLong(iTaken) else 0L
                val mod = it.getLong(iMod) * 1000L
                val date = when {
                    taken > 0 -> taken
                    mod > 0 -> mod
                    else -> f.lastModified()
                }
                val size = it.getLong(iSize).let { s -> if (s > 0) s else f.length() }
                val dur = if (iDur >= 0) it.getLong(iDur) else 0L
                val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate().toEpochDay()
                out.add(MediaItem(path, video, size, date, day, dur))
            }
        }
    }

    /** 파일 이동. 같은 저장소면 이름 변경(즉시), 아니면 복사 후 삭제. */
    fun moveFile(src: File, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        if (dest.exists()) return false
        if (src.renameTo(dest)) return true
        return try {
            src.copyTo(dest, overwrite = false)
            dest.setLastModified(src.lastModified())
            if (src.delete()) true else {
                dest.delete(); false
            }
        } catch (e: Exception) {
            dest.delete(); false
        }
    }

    /** 갤러리 앱에 변경 사항을 알림 */
    fun scan(ctx: Context, vararg paths: String) {
        MediaScannerConnection.scanFile(ctx.applicationContext, arrayOf(*paths), null, null)
    }
}
