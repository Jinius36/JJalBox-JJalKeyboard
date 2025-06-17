package com.myhome.rpgkeyboard.JJalBox

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import com.myhome.rpgkeyboard.R

class SearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // simple layout: EditText + Button
        setContentView(R.layout.activity_search)

        // 1) 키보드 높이 받아오기 (픽셀)
        val height = intent.getIntExtra("keyboard_height",
            resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
        )

        val etQuery = findViewById<EditText>(R.id.et_query)
        val btnGo   = findViewById<ImageButton>(R.id.btn_go)

        // IME 액션(Search) 처리
        etQuery.imeOptions = EditorInfo.IME_ACTION_SEARCH
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                returnResult(etQuery.text.toString())
                true
            }
            false
        }
        btnGo.setOnClickListener {
            returnResult(etQuery.text.toString())
        }
    }

    private fun returnResult(query: String) {
        // 클립보드에 복사
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("search_query", query))
        finish()
    }
}