package com.myhome.rpgkeyboard.JJalBox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import com.myhome.rpgkeyboard.R

class SearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val etQuery = findViewById<EditText>(R.id.et_query)
        val btnGo = findViewById<ImageButton>(R.id.btn_go)

        // IME 키보드의 검색(돋보기) 버튼 누르면 returnResult 호출
        etQuery.imeOptions = EditorInfo.IME_ACTION_SEARCH
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                returnResult(etQuery.text.toString())
                true
            } else false
        }
        btnGo.setOnClickListener {
            returnResult(etQuery.text.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        // SearchActivity 실행 중엔 짤 검색 버튼 숨김
        sendBroadcast(
            Intent(ACTION_SEARCH_UI)
                .putExtra(EXTRA_IS_VISIBLE, false)
        )
    }

    override fun onPause() {
        super.onPause()
        // SearchActivity 종료(다시 키보드로 돌아갈 때)엔 짤 검색 버튼 보이기
        sendBroadcast(
            Intent(ACTION_SEARCH_UI)
                .putExtra(EXTRA_IS_VISIBLE, true)
        )
    }

    private fun returnResult(query: String) {
        // 검색어 + 토글 신호를 함께 보냅니다
        Intent(ACTION_SEARCH_UI).also { intent ->
            intent.putExtra(EXTRA_QUERY, query)
            intent.putExtra(EXTRA_IS_VISIBLE, false)  // 검색 모드로 전환
            sendBroadcast(intent)
        }
        // Activity 종료
        finish()
    }

    companion object {
        const val ACTION_SEARCH_UI = "com.myhome.rpgkeyboard.ACTION_SEARCH_UI"
        const val EXTRA_IS_VISIBLE = "extra_is_visible"
        const val EXTRA_QUERY = "extra_query"
    }
}