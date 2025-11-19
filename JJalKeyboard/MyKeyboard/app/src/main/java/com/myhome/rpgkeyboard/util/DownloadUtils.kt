package com.myhome.rpgkeyboard.util

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

fun downloadToCache(
    context: Context,
    imageUrl: String,
    onSuccess: (cacheFile: File) -> Unit,
    onError: (Throwable) -> Unit
) {
    Thread {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val bytes = resp.body?.bytes() ?: throw Exception("empty body")
                val outFile = CacheUtils.makeCacheFile(context, imageUrl)
                FileOutputStream(outFile).use { it.write(bytes) }
                onSuccess(outFile)
            }
        } catch (e: Throwable) {
            onError(e)
        }
    }.start()
}