package com.myhome.rpgkeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.view.KeyEvent  // KEYCODE_DEL 사용을 위해
import androidx.appcompat.widget.AppCompatEditText
import com.myhome.rpgkeyboard.R
import com.myhome.rpgkeyboard.keyboardview.*


class KeyBoardService : InputMethodService(){
    lateinit var keyboardView:LinearLayout
    lateinit var keyboardFrame:FrameLayout
    lateinit var keyboardKorean:KeyboardKorean
    lateinit var keyboardEnglish:KeyboardEnglish
    lateinit var keyboardSimbols:KeyboardSimbols
    var isQwerty = 0 // shared preference에 데이터를 저장하고 불러오는 기능 필요

    // === 검색 UI용 필드 추가 ===
    private lateinit var searchContainer: View
    private lateinit var btnJjalSearch: Button
    private lateinit var tvSearchQuery: TextView
    private lateinit var btnClear: ImageButton
    private lateinit var btnSearch: ImageButton
    private var isSearchMode = false
    private val queryBuilder = StringBuilder()


    val keyboardInterationListener = object:KeyboardInterationListener{
        //inputconnection이 null일경우 재요청하는 부분 필요함

        // ─── 이 부분을 추가 ───────────────────────────────────────────────
        override fun onKey(primaryCode: Int, keyCodes: IntArray) {
            if (isSearchMode) {
                // 삭제키 처리
                if (primaryCode == KeyEvent.KEYCODE_DEL) {
                    if (queryBuilder.isNotEmpty()) {
                        queryBuilder.setLength(queryBuilder.length - 1)
                        tvSearchQuery.text = queryBuilder.toString()
                    }
                }
                // 일반 문자 입력 처리
                else {
                    val ch = primaryCode.toChar().toString()
                    queryBuilder.append(ch)
                    tvSearchQuery.text = queryBuilder.toString()
                }
            } else {
                // 일반 모드: 기존 로직 유지
                if (primaryCode == KeyEvent.KEYCODE_DEL) {
                    currentInputConnection.deleteSurroundingText(1, 0)
                } else {
                    currentInputConnection.commitText(primaryCode.toChar().toString(), 1)
                }
            }
        }

        override fun modechange(mode: Int) {
            currentInputConnection.finishComposingText()
            when(mode){
                0 ->{
                    keyboardFrame.removeAllViews()
                    keyboardEnglish.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardEnglish.getLayout())
                }
                1 -> {
                    if(isQwerty == 0){
                        keyboardFrame.removeAllViews()
                        keyboardKorean.inputConnection = currentInputConnection
                        keyboardFrame.addView(keyboardKorean.getLayout())
                    }
                    else{
                        keyboardFrame.removeAllViews()
                        keyboardFrame.addView(KeyboardChunjiin.newInstance(applicationContext, layoutInflater, currentInputConnection, this))
                    }
                }
                2 -> {
                    keyboardFrame.removeAllViews()
                    keyboardSimbols.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardSimbols.getLayout())
                }
                3 -> {
                    keyboardFrame.removeAllViews()
                    keyboardFrame.addView(KeyboardEmoji.newInstance(applicationContext, layoutInflater, currentInputConnection, this))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1) 전체 키보드 레이아웃 inflate
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardFrame = keyboardView.findViewById(R.id.keyboard_frame)

        // 2) 검색 컨테이너(ViewGroup)를 findViewById로 가져와 숨김 처리
        searchContainer = keyboardView.findViewById(R.id.search_container)
        searchContainer.visibility = View.GONE

        // 3) 검색 컨테이너 내부의 뷰 바인딩
        tvSearchQuery = searchContainer.findViewById(R.id.tv_search_query)
        btnClear      = searchContainer.findViewById(R.id.btn_clear)
        btnSearch     = searchContainer.findViewById(R.id.btn_search)

        // 4) 삭제 버튼 클릭 시 텍스트 지우기
        btnClear.setOnClickListener {
            queryBuilder.setLength(0)
            tvSearchQuery.text = ""
        }
        // 5) 검색 버튼 클릭 시 performSearch 호출
        btnSearch.setOnClickListener {
            performSearch(queryBuilder.toString())
        }

        // 6) “짤 검색!” 토글 버튼 바인딩
        btnJjalSearch = keyboardView.findViewById(R.id.btnJjalSearch)
        // KeyBoardService.kt

        btnJjalSearch.setOnClickListener {
            // 1) 검색 Activity 호출 준비
            val intent = Intent(this, SearchActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // 2) 현재 keyboardFrame 높이를 측정해서 넘겨준다
            //    (레이아웃이 아직 그려지지 않았을 수도 있으니 post() 사용)
            keyboardFrame.post {
                val h = keyboardFrame.height
                intent.putExtra("keyboard_height", h)
                startActivity(intent)
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboardKorean = KeyboardKorean(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardEnglish = KeyboardEnglish(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardSimbols = KeyboardSimbols(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardKorean.inputConnection = currentInputConnection
        keyboardKorean.init()
        keyboardEnglish.inputConnection = currentInputConnection
        keyboardEnglish.init()
        keyboardSimbols.inputConnection = currentInputConnection
        keyboardSimbols.init()

        keyboardInterationListener.modechange(1)

        return keyboardView
    }
    override fun updateInputViewShown() {
        super.updateInputViewShown()
        currentInputConnection.finishComposingText()
        updateSearchUI()
    }

    private fun updateSearchUI() {
        if (isSearchMode) {
            searchContainer.visibility = View.VISIBLE
            queryBuilder.setLength(0)
            tvSearchQuery.text = ""
        } else {
            searchContainer.visibility = View.GONE
            keyboardFrame.visibility = View.VISIBLE
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        // TODO: 검색 로직
        isSearchMode = false
        updateSearchUI()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 검색 모드에서 돌아왔다면 검색 모드 해제
        isSearchMode = false
        // 기본 모드(한국어)를 강제로 붙여 줍니다
        keyboardInterationListener.modechange(1)
        // 검색 컨테이너가 떠 있을 수도 있으니 숨깁니다
        keyboardFrame.visibility = View.VISIBLE
        searchContainer.visibility = View.GONE
    }
}
