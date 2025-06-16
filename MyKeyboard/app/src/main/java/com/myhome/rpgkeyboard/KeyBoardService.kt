package com.myhome.rpgkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
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

    // --- 플로팅 바(UI) 관련 ---
    private var floatingSearchBar: View? = null
    private lateinit var etSearch: AppCompatEditText
    private lateinit var btnSearchIcon: ImageButton

    // 검색 모드 토글 플래그
    private var isSearchMode = false



    val keyboardInterationListener = object:KeyboardInterationListener{
        //inputconnection이 null일경우 재요청하는 부분 필요함
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
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardFrame = keyboardView.findViewById(R.id.keyboard_frame)

        val btnJjalSearch = keyboardView.findViewById<Button>(R.id.btnJjalSearch)  // oard_view.xml](file-service://file-Sxu91psH9kNGwWeTHcfn8t)
        btnJjalSearch.setOnClickListener {
            isSearchMode = !isSearchMode
            // 키보드 전체를 갱신하도록 강제 호출
            updateInputViewShown()
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


        // 2) floatingSearchBar 한 번만 inflate → 붙이기
        if (floatingSearchBar == null) {
            floatingSearchBar = layoutInflater.inflate(
                R.layout.jjal_search_bar, keyboardView, false
            )
            // 최상단에 붙이고 숨김
            (keyboardView as ViewGroup).addView(floatingSearchBar, 0)
            floatingSearchBar!!.visibility = View.GONE
        }
        // floatingSearchBar inflate 이후
        etSearch = floatingSearchBar!!.findViewById(R.id.et_search)
        btnSearchIcon = floatingSearchBar!!.findViewById(R.id.btn_search)

        // IME 액션(Search) 설정
        etSearch.imeOptions = EditorInfo.IME_ACTION_SEARCH
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                onSearchStub(query)
                true
            } else false
        }

// 돋보기 버튼 클릭 리스너
        btnSearchIcon.setOnClickListener {
            val query = etSearch.text.toString().trim()
            onSearchStub(query)
        }



        return keyboardView
    }

    /** 실제 검색 로직은 나중에 여기에 구현하세요 */
    private fun onSearchStub(query: String) {
        // TODO: 서버 호출하거나 클립보드 복사 등
    }

    override fun updateInputViewShown() {
        super.updateInputViewShown()
        currentInputConnection.finishComposingText()
        if (isSearchMode) {
            // 검색 모드: 검색 바만 보이게
            floatingSearchBar?.visibility = View.VISIBLE
            keyboardFrame.visibility = View.VISIBLE
        } else {
            // 일반 모드: 기존 숫자 vs 문자 모드 분기
            if (currentInputEditorInfo.inputType == EditorInfo.TYPE_CLASS_NUMBER) {
                keyboardFrame.removeAllViews()
                keyboardFrame.addView(
                    KeyboardNumpad.newInstance(
                        applicationContext,
                        layoutInflater,
                        currentInputConnection,
                        keyboardInterationListener
                    )
                )
            } else {
                keyboardInterationListener.modechange(1)
            }
            floatingSearchBar?.visibility = View.GONE
            keyboardFrame.visibility = View.VISIBLE
        }
    }

}
