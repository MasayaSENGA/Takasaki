package com.takasaki.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Persists small album-art thumbnails so scrolling does not have to decode MediaStore art each launch. */
internal object AlbumArtCache {
    private const val MAX_EDGE = 512
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, Deferred<File?>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Must be called on Dispatchers.IO; also refreshes the entry's LRU timestamp. */
    private fun findCachedUri(context: Context, source: Uri): Uri? {
        val key = source.toString().toByteArray().sha256()
        val target = File(File(context.filesDir, "album_art"), "$key.jpg")
        return if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            Uri.fromFile(target)
        } else null
    }

    suspend fun getCachedUri(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "album_art").apply { mkdirs() }
        val key = source.toString().toByteArray().sha256()
        val target = File(directory, "$key.jpg")
        findCachedUri(context, source)?.let { return@withContext it }

        val task = pending.computeIfAbsent(key) {
            scope.async { createThumbnail(context.applicationContext, source, target) }
        }
        try {
            task.await()?.let(Uri::fromFile)
        } finally {
            pending.remove(key, task)
        }
    }

    private suspend fun createThumbnail(context: Context, source: Uri, target: File): File? =
        locks.computeIfAbsent(target.name) { Mutex() }.withLock {
            if (target.isFile && target.length() > 0L) return@withLock target

            withContext(Dispatchers.IO) {
                val temp = File(target.parentFile, "${target.name}.tmp")
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val boundsStream = context.contentResolver.openInputStream(source)
                        ?: return@withContext null
                    boundsStream.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }

                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                    }
                    val bitmap = context.contentResolver.openInputStream(source)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    } ?: return@withContext null

                    try {
                        FileOutputStream(temp).use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                                return@withContext null
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    if (!temp.renameTo(target)) return@withContext null
                    trimCache(target.parentFile, target)
                    target
                } catch (_: Exception) {
                    null
                } finally {
                    temp.delete()
                }
            }
        }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_EDGE || height / sample > MAX_EDGE) {
            sample *= 2
        }
        return sample
    }

    private fun trimCache(directory: File?, keep: File) {
        if (directory == null) return
        val files = directory.listFiles()?.filter { it.isFile && it.extension == "jpg" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (file in files.asSequence().filter { it != keep }.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { "%02x".format(it) }
}
