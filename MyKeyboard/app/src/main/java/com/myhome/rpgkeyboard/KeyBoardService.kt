package com.myhome.rpgkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.myhome.rpgkeyboard.keyboardview.*

class KeyBoardService : InputMethodService() {
    // 기존 변수들 (절대 지우지 마세요)
    private lateinit var keyboardView: LinearLayout
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardKorean: KeyboardKorean
    private lateinit var keyboardEnglish: KeyboardEnglish
    private lateinit var keyboardSimbols: KeyboardSimbols
    var isQwerty = 0

    // ✨ 새로 추가된 변수
    private lateinit var btnJjalSearch: Button
    private lateinit var jjalSearch: JJalSearch
    private var isJjalSearchVisible = false

    // 기존 listener (순수히 modechange만 담당)
    val keyboardInterationListener = object : KeyboardInterationListener {
        override fun modechange(mode: Int) {
            currentInputConnection.finishComposingText()
            when (mode) {
                0 -> {
                    keyboardFrame.removeAllViews()
                    keyboardEnglish.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardEnglish.getLayout())
                }
                1 -> {
                    if (isQwerty == 0) {
                        keyboardFrame.removeAllViews()
                        keyboardKorean.inputConnection = currentInputConnection
                        keyboardFrame.addView(keyboardKorean.getLayout())
                    } else {
                        keyboardFrame.removeAllViews()
                        keyboardFrame.addView(
                            KeyboardChunjiin.newInstance(
                                applicationContext,
                                layoutInflater,
                                currentInputConnection,
                                this
                            )
                        )
                    }
                }
                2 -> {
                    keyboardFrame.removeAllViews()
                    keyboardSimbols.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardSimbols.getLayout())
                }
                3 -> {
                    keyboardFrame.removeAllViews()
                    keyboardFrame.addView(
                        KeyboardEmoji.newInstance(
                            applicationContext,
                            layoutInflater,
                            currentInputConnection,
                            this
                        )
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 1) 키보드 전체 레이아웃 inflate
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardFrame = keyboardView.findViewById(R.id.keyboard_frame)

        // 2) JJalSearch 인스턴스 미리 생성 (onSearch 콜백은 실제 검색 로직으로 대체)
        jjalSearch = JJalSearch(this, layoutInflater) { query ->
            // TODO: query를 백엔드에 넘기거나 클립보드 복사 등
            // 검색 완료 후 원래 키보드로 복귀
            hideJjalSearch()
        }
        // 뷰 계층에 미리 추가해 두고 숨겨둡니다
        keyboardView.addView(jjalSearch.view)
        jjalSearch.view.visibility = View.GONE

        // 3) “짤 검색!” 버튼 바인딩
        btnJjalSearch = keyboardView.findViewById(R.id.btnJjalSearch)
        btnJjalSearch.setOnClickListener {
            if (isJjalSearchVisible) hideJjalSearch()
            else showJjalSearch()
        }
    }

    override fun onCreateInputView(): View {
        // 4) 기존 키보드 모듈 초기화 (절대 건드리지 않기)
        keyboardKorean = KeyboardKorean(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardEnglish = KeyboardEnglish(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardSimbols = KeyboardSimbols(applicationContext, layoutInflater, keyboardInterationListener)

        keyboardKorean.inputConnection = currentInputConnection
        keyboardKorean.init()
        keyboardEnglish.inputConnection = currentInputConnection
        keyboardEnglish.init()
        keyboardSimbols.inputConnection = currentInputConnection
        keyboardSimbols.init()

        // 최초 한 번 한국어 키보드 모드로 그려두기
        keyboardInterationListener.modechange(1)

        return keyboardView
    }

    override fun updateInputViewShown() {
        super.updateInputViewShown()
        currentInputConnection.finishComposingText()
        // 숫자 모드 vs 문자 모드 분기
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
        // IME가 다시 보일 때는 짤 검색 UI를 항상 숨깁니다
        hideJjalSearch()
    }

    /** 짤 검색 UI 보여 주기 */
    private fun showJjalSearch() {
        // 1) 기존 키보드 숨기기
        keyboardFrame.visibility = View.GONE
        // 2) JJalSearch 뷰 보이기
        jjalSearch.view.visibility = View.VISIBLE
        isJjalSearchVisible = true
    }

    /** 짤 검색 UI 숨기고 키보드 복귀 */
    private fun hideJjalSearch() {
        jjalSearch.view.visibility = View.GONE
        // 기존 키보드 다시 보이기
        keyboardFrame.visibility = View.VISIBLE
        // 한국어 모드 재설정
        keyboardInterationListener.modechange(1)
        isJjalSearchVisible = false
    }
}