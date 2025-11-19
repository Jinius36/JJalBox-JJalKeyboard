package com.myhome.rpgkeyboard.util

import android.content.Context
import java.io.File

object CacheUtils {
    /** 캐시 루트 (gif_cache 폴더) */
    fun getGifCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "gif_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun makeCacheFile(context: Context, url: String): File {
        val cacheDir = File(context.cacheDir, "gif_cache").apply { if (!exists()) mkdirs() }
        // url 끝에서 .gif/.png 포함된 부분
        val rawExt = url.substringAfterLast('.', "")
        // 확장자가 비어있거나 숫자처럼 오염됐다면, 기본 ".gif" 붙이기
        val ext = when (rawExt.lowercase()) {
            "gif", "png" -> rawExt
            else         -> "gif"
        }
        // 반드시 이름에 . 확장자를 포함
        val name = "${url.hashCode()}.$ext"
        return File(cacheDir, name)
    }
}