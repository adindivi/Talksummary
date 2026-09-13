package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * KakaoTalk direct image share helper with graceful fallback to system chooser.
 */
fun shareImageDirectlyToKakaoTalk(
    context: Context,
    imageFile: File,
    captionText: String,
    fallbackChooserTitle: String
) {
    try {
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        val kakaoIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (captionText.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, captionText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.kakao.talk")
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(kakaoIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                if (captionText.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, captionText)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(fallbackIntent, fallbackChooserTitle).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        }
    } catch (_: Exception) {
        // Fallback to text share if image URI generation fails
        shareDirectlyToKakaoTalk(context, captionText, fallbackChooserTitle)
    }
}

/**
 * KakaoTalk direct share helper with graceful fallback to system chooser.
 */
fun shareDirectlyToKakaoTalk(context: Context, text: String, fallbackChooserTitle: String) {
    val kakaoIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.kakao.talk")
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        context.startActivity(kakaoIntent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val chooser = Intent.createChooser(fallbackIntent, fallbackChooserTitle).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (name.isNullOrBlank()) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name
}
