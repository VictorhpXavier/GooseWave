package com.example.localplayer.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Extracts embedded album art from MP3s once and caches it as a small JPEG in app storage. */
object Art {
    /** Bumped whenever new art files appear so the UI re-checks. */
    val version = MutableStateFlow(0)

    private fun hash(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun file(uri: String): File = File(File(AppGraph.app.filesDir, "art"), hash(uri) + ".jpg")

    fun ensure(ctx: Context, uri: String): File? {
        val f = file(uri)
        if (f.exists()) return f
        val none = File(f.parentFile, f.name + ".none")
        if (none.exists()) return null
        f.parentFile?.mkdirs()
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, Uri.parse(uri))
            val data = r.embeddedPicture
            if (data == null) { none.createNewFile(); null } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 800) sample *= 2
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
                if (bmp == null) null else {
                    FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    f
                }
            }
        } catch (e: Exception) { f.delete(); null } finally { try { r.release() } catch (_: Exception) {} }
    }

    /** Covers songs imported before art caching existed. */
    suspend fun backfill(ctx: Context) {
        var n = 0
        for (s in AppGraph.db.dao().allSongs()) {
            if (!file(s.uri).exists()) {
                ensure(ctx, s.uri); n++
                if (n % 15 == 0) version.value = version.value + 1
            }
        }
        if (n > 0) version.value = version.value + 1
    }
}
