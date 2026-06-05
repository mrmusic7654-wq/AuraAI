package com.aura.ai.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.*

class FileAttachmentHandler(private val context: Context) {
    
    data class AttachedFile(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val content: String? = null
    )
    
    fun getFileInfo(uri: Uri): AttachedFile {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        var fileName = "unknown"
        var mimeType = "application/octet-stream"
        var size = 0L
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) fileName = it.getString(nameIndex) ?: "unknown"
                if (sizeIndex >= 0) size = it.getLong(sizeIndex)
            }
        }
        
        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        
        return AttachedFile(uri, fileName, mimeType, size)
    }
    
    fun readTextContent(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) { null }
    }
    
    fun readBytes(uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { it.readBytes() }
        } catch (e: Exception) { null }
    }
    
    fun getFileDescription(file: AttachedFile): String {
        val sizeStr = when {
            file.sizeBytes < 1024 -> "${file.sizeBytes} B"
            file.sizeBytes < 1024 * 1024 -> "${file.sizeBytes / 1024} KB"
            else -> "${file.sizeBytes / (1024 * 1024)} MB"
        }
        return "📎 ${file.fileName} (${file.mimeType}, $sizeStr)"
    }
    
    fun isImage(file: AttachedFile): Boolean = file.mimeType.startsWith("image/")
    fun isText(file: AttachedFile): Boolean = file.mimeType.startsWith("text/") || file.fileName.endsWith(".txt")
    fun isArchive(file: AttachedFile): Boolean = file.fileName.endsWith(".zip") || file.mimeType == "application/zip"
    fun isPdf(file: AttachedFile): Boolean = file.mimeType == "application/pdf"
}
