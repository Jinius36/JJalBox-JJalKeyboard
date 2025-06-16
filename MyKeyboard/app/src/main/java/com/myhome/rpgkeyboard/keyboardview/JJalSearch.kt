package com.myhome.rpgkeyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.myhome.rpgkeyboard.R

/**
 * 짤검색용 검색바 뷰와 내부 키 입력 처리기
 */
class JJalSearch(
    context: Context,
    inflater: LayoutInflater,
    private val onSearch: (query: String) -> Unit
) {
    private val queryBuilder = StringBuilder()
    val view: View = inflater.inflate(R.layout.jjal_search_bar, null, false)

    private val tvQuery: TextView = view.findViewById(R.id.et_search)
    private val btnSearch: ImageButton = view.findViewById(R.id.btn_search)

    init {
        btnSearch.setOnClickListener {
            val q = queryBuilder.toString().trim()
            if (q.isNotEmpty()) onSearch(q)
        }
    }

    /**
     * 키 이벤트(문자 입력·삭제 버튼)를
     * 이 메서드를 통해 전달하세요.
     * @param text 입력할 문자 (백스페이스인 경우 null)
     */
    fun handleKey(text: String?) {
        if (text == null) {
            // 삭제
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.setLength(queryBuilder.length - 1)
            }
        } else {
            // 일반 문자
            queryBuilder.append(text)
        }
        tvQuery.text = queryBuilder.toString()
    }

    /** 검색창 초기화 */
    fun clear() {
        queryBuilder.clear()
        tvQuery.text = ""
    }
}