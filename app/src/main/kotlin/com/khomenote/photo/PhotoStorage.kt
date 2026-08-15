package com.khomenote.photo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoStorage(private val context: Context) {
    suspend fun copyToInternalStorage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Cannot open input stream for $sourceUri" }
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.absolutePath
        }.getOrNull()
    }

    fun deletePhoto(path: String) {
        runCatching { File(path).delete() }
    }
}
