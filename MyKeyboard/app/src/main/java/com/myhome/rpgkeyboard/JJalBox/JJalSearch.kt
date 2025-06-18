package com.myhome.rpgkeyboard

import ImageAdapter
import MenuAdapter
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class JJalSearch(
    private val context: Context,
    private val inflater: LayoutInflater,
    private val onSearch: (query: String) -> Unit
) {
    // 1) Inflate 해 놓을 뷰를 먼저 만듭니다.
    private val _view: View = inflater.inflate(R.layout.image_view, null, false).apply {
        // 반드시 FrameLayout.LayoutParams 설정 (이전 답변 A번)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // 2) 뷰 프로퍼티를 init 이후에 할당
    val view: View = _view

    private val api: JjalApi = Retrofit.Builder()
        .baseUrl("http://3.26.31.15:5000/") // 🔁 EC2 IP로 교체
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JjalApi::class.java)

    init {
        // ★ 이 시점엔 view가 이미 초기화되어 있습니다. (_view == view)
        val menuBar   = view.findViewById<RecyclerView>(R.id.menu_bar)
        val imageList = view.findViewById<RecyclerView>(R.id.image_list)

        // 가로 스크롤 메뉴바 세팅
        val menuItems = listOf("인기", "강호동", "HI", "최고야", "헐", "고마워")
        menuBar.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        menuBar.adapter = MenuAdapter(menuItems) { category ->
            // 카테고리 클릭 시 이미지 로드
            loadImagesFor(category)
        }

        // 이미지 2열 그리드 세팅 (처음은 빈 리스트)
        imageList.layoutManager = GridLayoutManager(context, 2)
        imageList.adapter = ImageAdapter(emptyList()) { imageUrl ->
            onSearch(imageUrl)
        }

        // 3) **초기 한 번** 더미 이미지 로드
        loadImagesFor("인기")
    }

    private fun loadImagesFor(category: String) {
        val recycler = view.findViewById<RecyclerView>(R.id.image_list)
        val adapter = recycler.adapter as? ImageAdapter ?: return

        api.searchImages(category).enqueue(object : Callback<List<JjalImage>> {
            override fun onResponse(call: Call<List<JjalImage>>, response: Response<List<JjalImage>>) {
                if (response.isSuccessful) {
                    Log.d("JJalSearch", "응답 성공: ${response.body()?.size}개 이미지 수신됨")
                    val imageUrls = response.body()?.map { it.url } ?: emptyList()
                    adapter.updateData(imageUrls)
                } else {
                    adapter.updateData(emptyList())
                }
            }

            override fun onFailure(call: Call<List<JjalImage>>, t: Throwable) {
                Log.e("JJalSearch", "API 실패: ${t.message}")
                adapter.updateData(emptyList())
            }
        })
    }

    interface JjalApi {
        @GET("images/search")
        fun searchImages(@Query("query") keyword: String): Call<List<JjalImage>>
    }

    data class JjalImage(
        val id: Int,
        val url: String,
        val tag: List<String>,
        val text: String
    )
}