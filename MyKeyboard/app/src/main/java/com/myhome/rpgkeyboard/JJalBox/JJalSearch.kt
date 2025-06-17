package com.myhome.rpgkeyboard

import ImageAdapter
import MenuAdapter
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

    init {
        // ★ 이 시점엔 view가 이미 초기화되어 있습니다. (_view == view)
        val menuBar   = view.findViewById<RecyclerView>(R.id.menu_bar)
        val imageList = view.findViewById<RecyclerView>(R.id.image_list)

        // 가로 스크롤 메뉴바 세팅
        val menuItems = listOf("인기", "행복해", "재밌어", "최고야", "헐", "고마워")
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
        // 카테고리별 dummy 데이터
        val dummy = when (category) {
            "인기"     -> List(10) { "https://picsum.photos/200/200?popular=$it" }
            "행복해"   -> List(10) { "https://picsum.photos/200/200?happy=$it" }
            "재밌어"   -> List(10) { "https://picsum.photos/200/200?fun=$it" }
            "최고야"   -> List(10) { "https://picsum.photos/200/200?best=$it" }
            "헐"       -> List(10) { "https://picsum.photos/200/200?wow=$it" }
            "고마워"   -> List(10) { "https://picsum.photos/200/200?thanks=$it" }
            else       -> emptyList()
        }
        val recycler = view.findViewById<RecyclerView>(R.id.image_list)
        (recycler.adapter as? ImageAdapter)?.updateData(dummy)
    }
}