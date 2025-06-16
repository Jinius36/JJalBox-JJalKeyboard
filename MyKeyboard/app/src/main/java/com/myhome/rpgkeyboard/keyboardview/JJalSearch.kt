package com.myhome.rpgkeyboard

import android.view.View
import android.widget.ImageButton
import android.widget.TextView

/**
 * 키보드 내 search_container(ViewGroup)에 붙어
 * TextView와 버튼을 제어하는 헬퍼 클래스
 */
class JJalSearch(
    private val container: View,
    private val onSearch: (String)->Unit
) {
    private val tvQuery: TextView = container.findViewById(R.id.tv_search_query)
    private val btnClear: ImageButton = container.findViewById(R.id.btn_clear)
    private val btnSearch: ImageButton = container.findViewById(R.id.btn_search)
    private val queryBuilder = StringBuilder()

    init {
        btnClear.setOnClickListener {
            queryBuilder.setLength(0)
            tvQuery.text = ""
        }
        btnSearch.setOnClickListener {
            val q = queryBuilder.toString().trim()
            if (q.isNotEmpty()) onSearch(q)
        }
    }

    /** 키보드 리스너에서 받은 키 텍스트(또는 null=삭제) */
    fun handleKey(text: String?) {
        if (text == null) {
            if (queryBuilder.isNotEmpty()) queryBuilder.setLength(queryBuilder.length - 1)
        } else {
            queryBuilder.append(text)
        }
        tvQuery.text = queryBuilder.toString()
    }

    /** 외부에서 검색 후 초기화할 때 호출 */
    fun clear() {
        queryBuilder.setLength(0)
        tvQuery.text = ""
    }
}