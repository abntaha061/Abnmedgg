package com.example.ytdownloader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileHelper {

    fun saveFileToSaf(context: Context, sourcePath: String, targetFolderUriString: String?, title: String, ext: String): String? {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return null

        if (targetFolderUriString.isNullOrEmpty()) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, "${title.replace("[^a-zA-Z0-9]".toRegex(), "_")}.$ext")
            try {
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return destinationFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        try {
            val folderUri = Uri.parse(targetFolderUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return null
            val mimeType = if (ext == "mp3") "audio/mpeg" else "video/mp4"
            val newFileDoc = rootDoc.createFile(mimeType, title) ?: return null

            context.contentResolver.openOutputStream(newFileDoc.uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return newFileDoc.uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
