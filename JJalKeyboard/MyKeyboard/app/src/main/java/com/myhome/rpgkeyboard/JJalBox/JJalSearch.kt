package com.myhome.rpgkeyboard.JJalBox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myhome.rpgkeyboard.R
import com.myhome.rpgkeyboard.data.JjalImage
import com.myhome.rpgkeyboard.data.JjalRepository

class JJalSearch(
    private val context: Context,
    private val inflater: LayoutInflater,
    private val onSearch: (query: String) -> Unit
) {
    // 1) 뷰 인플레이트
    private val _view: View = inflater.inflate(R.layout.image_view, null, false).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    val view: View = _view

    private lateinit var menuAdapter: MenuAdapter

    // 2) API 인스턴스
    private val api = JjalRepository().api

    // 3) BroadcastReceiver: SearchActivity에서 보내는 ACTION_SEARCH_UI 수신
    private val searchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val q = intent?.getStringExtra(SearchActivity.EXTRA_QUERY) ?: return
            // 화면에 짤 검색 UI 보이는 상태라면 그대로, 아니라면 showJjalSearch() 호출 필요
            loadImagesFor(q)
        }
    }

    init {
        // a) menuBar & imageList 바인딩
        val menuBar: RecyclerView   = view.findViewById(R.id.menu_bar)
        val imageList: RecyclerView = view.findViewById(R.id.image_list)

        // b) 메뉴 세팅 (검색·최근·카테고리)
        val categories = listOf("인기", "강호동", "HI", "최고야", "헐", "고마워", "무한도전")
        val menuItems  = listOf("검색", "최근") + categories
        val initial = menuItems.indexOf("인기")  // 보통 2

        // c) menu_bar 레이아웃 매니저 세팅
        menuBar.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)

        // d) MenuAdapter 생성 → initialSelected 파라미터에 initial 전달
        val menuAdapter = MenuAdapter(
            items = menuItems,
            onClick = { item, pos ->
                when (pos) {
                    0 -> {
                        // 검색 버튼: SearchActivity 실행
                        // JJalSearch 의 onBindViewHolder 안(검색 아이콘 클릭 시)
                        val intent = Intent(context, SearchActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_HISTORY or // 히스토리에 남기지 않음
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS // 최근 앱 목록에 안 보이도록
                        }
                        context.startActivity(intent)
                    }
                    1 -> loadImagesFor("최신")
                    else -> loadImagesFor(item)
                }
            },
            initialSelected = initial
        )

        menuBar.adapter = menuAdapter

        // e) 이미지 그리드
        imageList.layoutManager = GridLayoutManager(context, 2)
        imageList.adapter = ImageAdapter(emptyList()) { imageUrl ->
            onSearch(imageUrl)
        }

        // f) 초기 “인기” 로드
        loadImagesFor("인기")

        // g) 브로드캐스트 리시버 등록
        ContextCompat.registerReceiver(
            context,
            searchReceiver,
            IntentFilter(SearchActivity.ACTION_SEARCH_UI),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** 외부에서 메뉴 선택을 강제할 때 호출 */
    fun selectMenuPosition(pos: Int) {
        if (!::menuAdapter.isInitialized) return   // ← 초기화 안 됐으면 그냥 무시
        menuAdapter.setSelectedPosition(pos)
    }

    /** 실제 API 호출 부분 */
    fun loadImagesFor(category: String) {
        val recycler = view.findViewById<RecyclerView>(R.id.image_list)
        val adapter  = recycler.adapter as? ImageAdapter ?: return

        api.searchImages(category).enqueue(object : retrofit2.Callback<List<JjalImage>> {
            override fun onResponse(
                call: retrofit2.Call<List<JjalImage>>,
                response: retrofit2.Response<List<JjalImage>>
            ) {
                val urls = response.body()?.map { it.url } ?: emptyList()
                adapter.updateData(urls)
            }
            override fun onFailure(call: retrofit2.Call<List<JjalImage>>, t: Throwable) {
                adapter.updateData(emptyList())
            }
        })
    }
}