package com.myhome.rpgkeyboard.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JjalRepository {
    val api: JjalApi

    init {
        api = Retrofit.Builder()
            .baseUrl("http://3.26.31.15:5000/")    // ← 실제 EC2 IP로 교체
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JjalApi::class.java)
    }

    /** 콜백으로 성공 리스트/실패(Exception) 전달 */
    fun searchImages(
        query: String,
        onSuccess: (List<JjalImage>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        api.searchImages(query).enqueue(object : retrofit2.Callback<List<JjalImage>> {
            override fun onResponse(
                call: retrofit2.Call<List<JjalImage>>,
                response: retrofit2.Response<List<JjalImage>>
            ) {
                if (response.isSuccessful) {
                    onSuccess(response.body() ?: emptyList())
                } else {
                    onError(RuntimeException("HTTP ${response.code()}"))
                }
            }
            override fun onFailure(call: retrofit2.Call<List<JjalImage>>, t: Throwable) {
                onError(t)
            }
        })
    }
}