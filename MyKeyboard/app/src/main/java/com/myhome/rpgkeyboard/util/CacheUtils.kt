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

    /** 파일 이름 생성 (원본 URL 해시 등으로 유니크하게) */
    fun makeCacheFile(context: Context, url: String): File {
        val fileName = url.hashCode().toString() + url.substringAfterLast('.', ".gif")
        return File(getGifCacheDir(context), fileName)
    }
}