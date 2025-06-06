// 파일 경로: app/src/main/java/com/myhome/rpgkeyboard/ApiService.kt
package com.myhome.rpgkeyboard

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    /**
     * 예시: GET http://3.26.31.15:5000/search_images?keyword=가나다
     * 반환 타입: List<String> (이미지 URL 목록)
     */
    @GET("search_images")
    fun getImages(@Query("keyword") keyword: String): Call<List<String>>
}