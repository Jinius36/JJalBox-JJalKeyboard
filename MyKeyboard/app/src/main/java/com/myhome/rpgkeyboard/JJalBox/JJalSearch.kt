package com.myhome.rpgkeyboard.JJalBox

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.myhome.rpgkeyboard.R
import com.myhome.rpgkeyboard.data.JjalApi
import com.myhome.rpgkeyboard.data.JjalImage
import com.myhome.rpgkeyboard.data.JjalRepository
import com.myhome.rpgkeyboard.JJalBox.ImageAdapter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JJalSearch(
    private val context: Context,
    private val inflater: LayoutInflater,
    private val onSearch: (query: String) -> Unit
) {
    // 1) Inflate the view container
    private val _view: View = inflater.inflate(R.layout.image_view, null, false).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    val view: View = _view

    private val api = JjalRepository().api

    init {
        // 1) 뷰 바인딩
        val menuBar: RecyclerView   = view.findViewById(R.id.menu_bar)
        val imageList: RecyclerView = view.findViewById(R.id.image_list)

        // 2) 메뉴 리스트 구성 (“검색”, “최근” + 카테고리)
        val categories = listOf("인기", "강호동", "HI", "최고야", "헐", "고마워", "무한도전")
        val menuItems  = listOf("검색", "최근") + categories

        // 3) “인기”의 인덱스를 찾아서 initialSelected로 넘김
        val initial = menuItems.indexOf("인기")  // 보통 2

        // 4) menu_bar 레이아웃 매니저 세팅
        menuBar.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)

        // 5) MenuAdapter 생성 → initialSelected 파라미터에 initial 전달
        val menuAdapter = MenuAdapter(
            items = menuItems,
            onClick = { selected, position ->
                when (position) {
                    0 -> {
                        // 검색 아이콘
                        val intent = Intent(context, SearchActivity::class.java)
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                    1 -> {
                        // 최근
                        loadImagesFor("최신")
                    }
                    else -> {
                        // 카테고리
                        loadImagesFor(selected)
                    }
                }
            },
            initialSelected = initial      // ← 여기!!
        )

        // 6) 어댑터 할당
        menuBar.adapter = menuAdapter

        // 7) 이미지 그리드 세팅
        imageList.layoutManager = GridLayoutManager(context, 2)
        imageList.adapter       = ImageAdapter(emptyList()) { imageUrl ->
            onSearch(imageUrl)
        }

        // 8) 초기 데이터 로드
        loadImagesFor("인기")
    }

    /** 실제 API 호출 부분 */
    private fun loadImagesFor(category: String) {
        val recycler = view.findViewById<RecyclerView>(R.id.image_list)
        val adapter  = recycler.adapter as? ImageAdapter ?: return

        // 화면을 비우고
        adapter.updateData(emptyList())

        // ① 분리된 Repository 대신, JjalApi 직접 사용
        api.searchImages(category)
            .enqueue(object : retrofit2.Callback<List<JjalImage>> {
                override fun onResponse(
                    call: retrofit2.Call<List<JjalImage>>,
                    response: retrofit2.Response<List<JjalImage>>
                ) {
                    if (response.isSuccessful) {
                        // Response<List<JjalImage>> → List<String>
                        val urls = response.body()
                            ?.map { it.url }
                            ?: emptyList()
                        adapter.updateData(urls)
                    } else {
                        adapter.updateData(emptyList())
                    }
                }
                override fun onFailure(call: retrofit2.Call<List<JjalImage>>, t: Throwable) {
                    adapter.updateData(emptyList())
                }
            })
    }
}