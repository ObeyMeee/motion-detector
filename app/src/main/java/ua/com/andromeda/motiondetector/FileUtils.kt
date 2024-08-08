package ua.com.andromeda.motiondetector

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

fun copyVideoToInternalStorage(context: Context, videoUri: Uri): Uri {
    val videosDirectory = getVideosDirectory(context)
    val fileName = videoUri.getFileName(context)
    val internalVideoFile = context.getFileStreamPath(fileName)
    if (internalVideoFile.exists()) {
        return internalVideoFile.toUri()
    }
    val copiedVideoFile = File(videosDirectory, fileName.toString())
    val videoInputStream = context.contentResolver.openInputStream(videoUri)
    videoInputStream.copy(copiedVideoFile)
    return copiedVideoFile.toUri()
}

private fun getVideosDirectory(context: Context): File {
    val videoDirectory = File(context.filesDir, "videos")
    if (!videoDirectory.exists()) {
        videoDirectory.mkdir()
    }
    return videoDirectory
}

private fun InputStream?.copy(destination: File) {
    this?.use { input ->
        FileOutputStream(destination).use { output ->
            input.copyTo(output)
        }
    }
}

fun Uri.getFileName(context: Context): String? {
    val cursor = context.contentResolver.query(this, null, null, null, null) ?: return null
    val fileName = cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        it.getString(nameIndex)
    }
    return fileName
}