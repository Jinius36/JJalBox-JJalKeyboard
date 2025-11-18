package com.myhome.rpgkeyboard.data

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface JjalApi {
    /**
     * 예: GET http://3.26.31.15:5000/images/search?query=강호동
     */
    @GET("images/search")
    fun searchImages(
        @Query("query") keyword: String
    ): Call<List<JjalImage>>
}