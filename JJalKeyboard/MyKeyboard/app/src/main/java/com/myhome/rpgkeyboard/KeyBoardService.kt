package com.myhome.rpgkeyboard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.LinearLayout
import com.myhome.rpgkeyboard.keyboardview.*

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.myhome.rpgkeyboard.JJalBox.SearchActivity
import androidx.core.content.ContextCompat
import com.myhome.rpgkeyboard.JJalBox.JJalSearch


class KeyBoardService : InputMethodService() {
    // 기존 변수들 (절대 지우지 마세요)
    private lateinit var keyboardView: LinearLayout
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardKorean: KeyboardKorean
    private lateinit var keyboardEnglish: KeyboardEnglish
    private lateinit var keyboardSimbols: KeyboardSimbols
    var isQwerty = 0

    private lateinit var btnJjalSearch: Button
    private lateinit var jjalSearch: JJalSearch
    private var isJjalSearchVisible = false
    private var isSearchActivityOpen   = false

    // SearchActivity에 BroadCast 보내는 용도
    private lateinit var searchUiReceiver: BroadcastReceiver

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
                                applicationContext, layoutInflater, currentInputConnection, this
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
                            applicationContext, layoutInflater, currentInputConnection, this
                        )
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1) 키보드 레이아웃 inflate
        keyboardView  = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardFrame = keyboardView.findViewById(R.id.keyboard_frame)

        // 2) “짤 검색!” 버튼 바인딩
        btnJjalSearch = keyboardView.findViewById(R.id.btnJjalSearch)
        btnJjalSearch.setOnClickListener {
            if (isJjalSearchVisible) hideJjalSearch()
            else showJjalSearch()
        }

        // 3) JJalSearch 객체 생성 (menuAdapter 초기화 포함)
        jjalSearch = JJalSearch(
            context   = applicationContext,
            inflater  = layoutInflater
        ) { url ->
            currentInputConnection.commitText(url, 1)
            // 검색 후 바로 커밋만, UI 토글은 브로드캐스트로 처리
        }

        // 브로드캐스트 리시버 등록 (QUERY+VISIBLE 한번에 처리)
        searchUiReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val showActivity = intent
                    ?.getBooleanExtra(SearchActivity.EXTRA_IS_VISIBLE, true) ?: true
                val query = intent?.getStringExtra(SearchActivity.EXTRA_QUERY)

                // 1) SearchActivity 열리고 닫히는 신호 토글
                isSearchActivityOpen = !showActivity
                // 버튼 표시/숨김
                btnJjalSearch.visibility =
                    if (showActivity) View.VISIBLE else View.GONE

                // 2) 검색 완료 후 복귀: EXTRA_QUERY 가 있을 때만
                if (query != null) {
                    // SearchActivity 종료 → 실제 검색 모드로 전환
                    isSearchActivityOpen = false
                    showJjalSearch()
                    jjalSearch.loadImagesFor(query)
                    jjalSearch.selectMenuPosition(0)
                }
            }
        }
        ContextCompat.registerReceiver(
            this, searchUiReceiver,
            IntentFilter(SearchActivity.ACTION_SEARCH_UI),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(searchUiReceiver)
    }

    override fun onCreateInputView(): View {
        // 4) 기존 키보드 모듈 초기화 (절대 건드리지 않기)
        keyboardKorean =
            KeyboardKorean(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardEnglish =
            KeyboardEnglish(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardSimbols =
            KeyboardSimbols(applicationContext, layoutInflater, keyboardInterationListener)

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

        // ★ 검색 UI 모드이면서 •• SearchActivity 가 열려 있지 않을 때만 가드
        if (isJjalSearchVisible && !isSearchActivityOpen) {
            // Keep search UI visible when not in SearchActivity
            keyboardFrame.removeAllViews()
            val lp = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            keyboardFrame.addView(jjalSearch.view, lp)
            return
        }

        // 그렇지 않으면 원래 키보드 갱신 로직 실행
        currentInputConnection.finishComposingText()
        if (currentInputEditorInfo.inputType == EditorInfo.TYPE_CLASS_NUMBER) {
            keyboardFrame.removeAllViews()
            keyboardFrame.addView(
                KeyboardNumpad.newInstance(
                    applicationContext, layoutInflater,
                    currentInputConnection, keyboardInterationListener
                )
            )
        } else {
            keyboardInterationListener.modechange(1)
        }
    }

    private fun showJjalSearch() {
        // 1) 프레임 비우기
        keyboardFrame.removeAllViews()

        isJjalSearchVisible = true

        // 2) MATCH_PARENT x MATCH_PARENT 로 덮기
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        keyboardFrame.addView(jjalSearch.view, lp)
    }

    /** 짤 검색 UI 숨기고 키보드 복귀 */
    private fun hideJjalSearch() {
        isJjalSearchVisible = false

        // 1) jjalSearch.view 제거
        keyboardFrame.removeAllViews()

        // 2) 기존 키보드 모드(한글) 다시 붙이기
        keyboardInterationListener.modechange(1)
    }
}