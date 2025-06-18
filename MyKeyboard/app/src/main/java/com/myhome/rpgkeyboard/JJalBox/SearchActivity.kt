package com.myhome.rpgkeyboard.JJalBox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.myhome.rpgkeyboard.R

class SearchActivity : AppCompatActivity() {
    companion object {
        const val ACTION_SEARCH_UI = "com.myhome.rpgkeyboard.ACTION_SEARCH_UI"
        const val EXTRA_IS_VISIBLE = "extra_is_visible"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val etQuery = findViewById<EditText>(R.id.et_query)
        val btnGo   = findViewById<ImageButton>(R.id.btn_go)

        // IME에서 돋보기 버튼 누르면
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
        // 시스템 브로드캐스트로 전환
        sendBroadcast(
            Intent(ACTION_SEARCH_UI)
                .putExtra(EXTRA_IS_VISIBLE, false)
        )
    }

    override fun onPause() {
        super.onPause()
        sendBroadcast(
            Intent(ACTION_SEARCH_UI)
                .putExtra(EXTRA_IS_VISIBLE, true)
        )
    }

    private fun returnResult(query: String) {
        // 클립보드에 복사
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("search_query", query))
        finish()
    }
}