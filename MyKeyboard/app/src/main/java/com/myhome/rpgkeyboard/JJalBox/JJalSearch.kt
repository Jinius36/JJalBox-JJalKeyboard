package com.myhome.rpgkeyboard

import ImageAdapter
import MenuAdapter
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
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

    private val api: JjalApi = Retrofit.Builder()
        .baseUrl("http://3.26.31.15:5000/") // 🔁 EC2 IP로 교체
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JjalApi::class.java)

    init {
        // 1) 뷰 바인딩
        val menuBar   = view.findViewById<RecyclerView>(R.id.menu_bar)
        val imageList = view.findViewById<RecyclerView>(R.id.image_list)

      // 2) 메뉴 항목 리스트: "검색","최근" + 기존 카테고리
      val categories = listOf("인기", "강호동", "HI", "최고야", "헐", "고마워")
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