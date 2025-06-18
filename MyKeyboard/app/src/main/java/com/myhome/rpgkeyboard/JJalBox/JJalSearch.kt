package com.myhome.rpgkeyboard

import ImageAdapter
import MenuAdapter
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.myhome.rpgkeyboard.JJalBox.SearchActivity

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
        // 1) 뷰 바인딩
        val menuBar   = view.findViewById<RecyclerView>(R.id.menu_bar)
        val imageList = view.findViewById<RecyclerView>(R.id.image_list)

        // 2) 메뉴 항목 리스트: "검색","최근" + 기존 카테고리
        val categories = listOf("인기", "행복해", "재밌어", "최고야", "헐", "고마워")
        val menuItems  = listOf("검색", "최근") + categories

        // 3) menu_bar 레이아웃 매니저
        menuBar.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)

        // 4) 어댑터: 위치 0,1은 아이콘, 그 외는 텍스트
        menuBar.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = menuItems.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                // 모든 아이템은 menu_item.xml(TextView) 로 inflate
                val tv = LayoutInflater.from(parent.context)
                    .inflate(R.layout.menu_item, parent, false) as TextView
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView as TextView

                // 0:"검색", 1:"최근" 은 아이콘으로 대체
                when (position) {
                    0 -> {
                        tv.text = ""
                        tv.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_search_24dp,
                            0, 0, 0
                        )
                    }
                    1 -> {
                        tv.text = ""
                        tv.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_recent,
                            0, 0, 0
                        )
                    }
                    else -> {
                        tv.text = menuItems[position]
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    }
                }

                // 클릭 시 loadImagesFor 호출
                tv.setOnClickListener {
                    when (position) {
                        0 -> {
                            // 검색 아이콘 눌렀을 때 SearchActivity 실행
                            val intent = Intent(context, SearchActivity::class.java).apply {
                                // 서비스 컨텍스트에서 시작하므로 이 플래그가 필요합니다
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                        1 -> loadImagesFor("최신")
                        else -> loadImagesFor(menuItems[position])
                    }
                }
            }
        }

        // 5) 이미지 그리드(기존 로직)
        imageList.layoutManager = GridLayoutManager(context, 2)
        imageList.adapter = ImageAdapter(emptyList()) { imageUrl ->
            onSearch(imageUrl)
        }

        loadImagesFor("인기")
    }

    private fun loadImagesFor(category: String) {
        // 카테고리별 dummy 데이터
        val dummy = when (category) {
            "최신"     -> List(10) { "https://picsum.photos/200/200?recent=$it" }
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