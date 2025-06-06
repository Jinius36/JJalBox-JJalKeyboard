package com.myhome.rpgkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.myhome.rpgkeyboard.keyboardview.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class KeyBoardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    companion object {
        private const val PREFS_NAME = "keyboard_prefs"
        private const val KEY_FAVORITES = "key_favorites"
        private const val KEY_HISTORY = "key_history"
        private const val MAX_LIST_SIZE = 20
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1) 기존 키보드 뷰 관련 프로퍼티
    private lateinit var keyboardView: LinearLayout
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardKorean: KeyboardKorean
    private lateinit var keyboardEnglish: KeyboardEnglish
    private lateinit var keyboardSimbols: KeyboardSimbols
    private var isQwerty = 0 // SharedPreferences에 저장/불러오기 위해 사용

    // ──────────────────────────────────────────────────────────────────────────
    // 2) 검색창 및 리스트 영역 관련 프로퍼티
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageView
    private lateinit var listContainer: LinearLayout
    private lateinit var rvFavorites: RecyclerView
    private lateinit var rvHistory: RecyclerView
    private lateinit var imageRecyclerView: RecyclerView

    // ──────────────────────────────────────────────────────────────────────────
    // 3) SharedPreferences 및 데이터 리스트
    private lateinit var prefs: android.content.SharedPreferences
    private var favoritesList: MutableList<String> = mutableListOf()
    private var historyList: MutableList<String> = mutableListOf()

    // ──────────────────────────────────────────────────────────────────────────
    // 4) RecyclerView 어댑터
    private lateinit var favoritesAdapter: SimpleStringAdapter
    private lateinit var historyAdapter: SimpleStringAdapter

    // ──────────────────────────────────────────────────────────────────────────
    // 5) 현재 모드: 일반 키보드 모드(false) / 검색 모드(true)
    private var isSearchMode = false

    // ──────────────────────────────────────────────────────────────────────────
    // 6) 기존의 키보드 모드 전환 리스너
    private val keyboardInterationListener = object : KeyboardInterationListener {
        override fun modechange(mode: Int) {
            currentInputConnection.finishComposingText()
            when (mode) {
                0 -> {
                    // 영어 키보드 모드
                    keyboardFrame.removeAllViews()
                    keyboardEnglish.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardEnglish.getLayout())
                }
                1 -> {
                    // 한글 키보드 모드
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
                    // 기호 키보드 모드
                    keyboardFrame.removeAllViews()
                    keyboardSimbols.inputConnection = currentInputConnection
                    keyboardFrame.addView(keyboardSimbols.getLayout())
                }
                3 -> {
                    // 이모지 키보드 모드
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

    // ──────────────────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()

        // SharedPreferences 초기화
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 기존에 저장된 즐겨찾기/히스토리 불러오기 (Set<String> → List)
        favoritesList = prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.toMutableList() ?: mutableListOf()
        historyList = prefs.getStringSet(KEY_HISTORY, emptySet())
            ?.toMutableList() ?: mutableListOf()

        // keyboard_view.xml inflate → 키보드 뷰 참조
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardFrame = keyboardView.findViewById(R.id.keyboard_frame)

        // 검색창 및 리스트 영역 바인딩
        searchInput = keyboardView.findViewById(R.id.searchInput)
        searchButton = keyboardView.findViewById(R.id.searchButton)
        listContainer = keyboardView.findViewById(R.id.listContainer)
        rvFavorites = keyboardView.findViewById(R.id.rvFavorites)
        rvHistory = keyboardView.findViewById(R.id.rvHistory)

        // 이미지 RecyclerView는 동적으로 추가될 예정
        // (layout 파일에 따로 정의하지 않았으므로, 이곳에서 생성하고 추가합니다)
        imageRecyclerView = RecyclerView(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        // keyboardView 내 keyboard_frame 바로 밑에 imageRecyclerView를 삽입
        (keyboardFrame.parent as ViewGroup).addView(imageRecyclerView)
    }

    // ──────────────────────────────────────────────────────────────────────────
    override fun onCreateInputView(): View {
        // 1) 기존 키보드 객체 초기화
        keyboardKorean = KeyboardKorean(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardEnglish = KeyboardEnglish(applicationContext, layoutInflater, keyboardInterationListener)
        keyboardSimbols = KeyboardSimbols(applicationContext, layoutInflater, keyboardInterationListener)

        keyboardKorean.inputConnection = currentInputConnection
        keyboardKorean.init()

        keyboardEnglish.inputConnection = currentInputConnection
        keyboardEnglish.init()

        keyboardSimbols.inputConnection = currentInputConnection
        keyboardSimbols.init()

        // 2) RecyclerView 어댑터 초기화
        favoritesAdapter = SimpleStringAdapter(favoritesList) { clickedKeyword ->
            // 즐겨찾기 아이템 클릭 시, 검색창에 텍스트 반영
            searchInput.setText(clickedKeyword)
            searchInput.setSelection(clickedKeyword.length)
        }
        rvFavorites.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvFavorites.adapter = favoritesAdapter

        historyAdapter = SimpleStringAdapter(historyList) { clickedKeyword ->
            // 최근검색어 클릭 시, 검색창에 텍스트 반영
            searchInput.setText(clickedKeyword)
            searchInput.setSelection(clickedKeyword.length)
        }
        rvHistory.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvHistory.adapter = historyAdapter

        // 3) 검색 버튼 클릭 리스너 설정 (검색 모드 토글)
        searchButton.setOnClickListener {
            if (!isSearchMode) {
                enterSearchMode()
            } else {
                exitSearchMode()
            }
        }

        // 4) 키보드의 “검색” 엔터키 동작: IME_ACTION_SEARCH 시 performSearch()
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        return keyboardView
    }


    // ──────────────────────────────────────────────────────────────────────────
    /** 검색 모드 진입 **/
    private fun enterSearchMode() {
        isSearchMode = true

        // 1) 기존 키보드 화면 숨기기
        keyboardFrame.visibility = View.GONE

        // 2) 리스트 영역 보이기
        listContainer.visibility = View.VISIBLE

        // 3) searchButton 아이콘을 ‘닫기’로 변경
        searchButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)

        // 4) 검색창에 포커스 주기
        searchInput.requestFocus()
    }

    // ──────────────────────────────────────────────────────────────────────────
    /** 검색 모드 종료 **/
    private fun exitSearchMode() {
        isSearchMode = false

        // 1) 리스트 영역 숨기기
        listContainer.visibility = View.GONE

        // 2) 검색 결과(이미지 목록) 숨기기
        imageRecyclerView.visibility = View.GONE

        // 3) 키보드 화면 표시
        keyboardFrame.visibility = View.VISIBLE

        // 4) searchButton 아이콘을 ‘검색’으로 되돌리기
        searchButton.setImageResource(android.R.drawable.ic_menu_search)

        // 5) 검색창 텍스트 초기화
        searchInput.text.clear()
    }

    // ──────────────────────────────────────────────────────────────────────────
    /** 실제 검색 수행 로직: Retrofit을 통한 이미지 검색 예시 **/
    private fun performSearch() {
        val keyword = searchInput.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "검색어를 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1) 최근 검색어 저장
        saveToHistory(keyword)

        // 2) 서버에서 이미지 URL 리스트 받아오기 (예: ApiService.getImages)
        //    * ApiService와 RetrofitClient 설정은 “추가 리소스” 섹션을 참고하세요.
        imageRecyclerView.layoutManager = GridLayoutManager(this, 3)
        RetrofitClient.instance.getImages(keyword)
            .enqueue(object : Callback<List<String>> {
                override fun onResponse(
                    call: Call<List<String>>,
                    response: Response<List<String>>
                ) {
                    if (response.isSuccessful) {
                        val imageUrls = response.body() ?: emptyList()
                        // 3) ImageAdapter로 화면에 표시
                        imageRecyclerView.adapter = ImageAdapter(imageUrls) { imageUrl ->
                            copyImageToClipboard(imageUrl)
                        }
                        imageRecyclerView.visibility = View.VISIBLE
                        // 검색 모드 리스트 숨김
                        listContainer.visibility = View.GONE
                    } else {
                        Toast.makeText(this@KeyBoardService, "서버 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<String>>, t: Throwable) {
                    t.printStackTrace()
                    Toast.makeText(this@KeyBoardService, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ──────────────────────────────────────────────────────────────────────────
    /** 서버에서 받아온 이미지를 클립보드에 복사 **/
    private fun copyImageToClipboard(imageUrl: String) {
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        // 1) cacheDir/images 폴더에 파일로 저장
                        val imagesFolder = File(cacheDir, "images")
                        imagesFolder.mkdirs()
                        val file = File(imagesFolder, "shared_image.png")
                        val stream = FileOutputStream(file)
                        resource.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        stream.flush()
                        stream.close()

                        // 2) FileProvider로 URI 획득
                        val imageUri = FileProvider.getUriForFile(
                            this@KeyBoardService,
                            "${packageName}.provider",
                            file
                        )

                        // 3) 클립보드에 URI 복사
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newUri(contentResolver, "Image", imageUri)
                        clipboard.setPrimaryClip(clip)

                        Toast.makeText(this@KeyBoardService, "이미지를 클립보드에 복사했습니다.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@KeyBoardService, "이미지 복사 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    // ──────────────────────────────────────────────────────────────────────────
    /** SharedPreferences에 최근검색어 저장 **/
    private fun saveToHistory(keyword: String) {
        // ① 이미 있는 키워드는 제거하고 맨 앞으로
        historyList.remove(keyword)
        historyList.add(0, keyword)

        // ② 최대 개수 제한 (20개)
        if (historyList.size > MAX_LIST_SIZE) {
            historyList = historyList.subList(0, MAX_LIST_SIZE).toMutableList()
        }

        // ③ SharedPreferences에 Set<String> 형태로 저장
        prefs.edit()
            .putStringSet(KEY_HISTORY, historyList.toSet())
            .apply()

        // ④ RecyclerView 갱신
        historyAdapter.updateData(historyList)
    }

    // ──────────────────────────────────────────────────────────────────────────
    /** SharedPreferences에 즐겨찾기 저장 **/
    private fun saveToFavorites(keyword: String) {
        favoritesList.remove(keyword)
        favoritesList.add(0, keyword)

        if (favoritesList.size > MAX_LIST_SIZE) {
            favoritesList = favoritesList.subList(0, MAX_LIST_SIZE).toMutableList()
        }

        prefs.edit()
            .putStringSet(KEY_FAVORITES, favoritesList.toSet())
            .apply()

        favoritesAdapter.updateData(favoritesList)
        Toast.makeText(this, "‘$keyword’ 즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shift 상태를 관리하는 플래그 (KeyboardView에 내장된 게 아니므로 직접 선언)
    private var isShifted = false

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection: InputConnection? = currentInputConnection
        if (inputConnection == null) return

        // 1) DEL 키 처리
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            if (isSearchMode) {
                val text = searchInput.text
                val selStart = searchInput.selectionStart
                if (text.isNotEmpty() && selStart > 0) {
                    text.delete(selStart - 1, selStart)
                }
            } else {
                inputConnection.deleteSurroundingText(1, 0)
            }
            return
        }

        // 2) Shift 키 처리
        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift()
            return
        }

        // 3) DONE(엔터) 키 처리
        if (primaryCode == Keyboard.KEYCODE_DONE) {
            if (isSearchMode) {
                performSearch()
            } else {
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            }
            return
        }

        // 4) 일반 문자 입력
        var code = primaryCode.toChar()
        if (isShifted && code.isLetter()) {
            code = code.toUpperCase()
        }

        if (isSearchMode) {
            val pos = searchInput.selectionStart
            searchInput.text.insert(pos, code.toString())
        } else {
            inputConnection.commitText(code.toString(), 1)
        }
    }


    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    // ──────────────────────────────────────────────────────────────────────────
    /** Shift 토글 처리 **/
    private fun handleShift() {
        isShifted = !isShifted
        if (isShifted) {
            keyboardFrame.removeAllViews()
            keyboardFrame.addView(keyboardEnglish.getLayout())
        } else {
            keyboardFrame.removeAllViews()
            keyboardFrame.addView(keyboardKorean.getLayout())
        }
        keyboardFrame.invalidate()
    }


    // ──────────────────────────────────────────────────────────────────────────
    override fun updateInputViewShown() {
        super.updateInputViewShown()
        currentInputConnection.finishComposingText()
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
    }
}