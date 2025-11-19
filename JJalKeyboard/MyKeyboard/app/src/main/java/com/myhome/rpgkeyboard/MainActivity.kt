package com.myhome.rpgkeyboard

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.myhome.rpgkeyboard.BuildConfig
// 썸네일 미리보기를 위한 imports
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup


class MainActivity : AppCompatActivity() {

    // ====== 설정 ======
    private val PROXY_BASE_URL = BuildConfig.PROXY_BASE_URL
    private val TAG_PROXY = "IMAGE_PROXY"



    // ====== UI ======
    private lateinit var spinnerProvider: Spinner
    private lateinit var ivGuide: ImageView         // pixel,ac guide
    private lateinit var btnPickImage: Button
    private lateinit var tvSelected: TextView
    private lateinit var etPrompt: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progress: ProgressBar
    private lateinit var ivResult: ImageView
    private lateinit var btnDownload: Button
    private lateinit var rvThumbs: RecyclerView // 썸네일 미리보기

    // ====== 상태 ======
    enum class Provider(val displayName: String, val apiName: String) {
        GPT("GPT", "gpt"),
        GEMINI("Gemini", "gemini"),
        MEME_GALTEYA("갈테야테야 밈", "meme_galteya"),
        SNOW_NIGHT("눈 내리는 밤", "snow_night"),
        PIXEL_ART("픽셀 아트 캐릭터", "pixel_art"),
        ANIMAL_CROSSING("동물의 숲 캐릭터", "ac_style");
    }



    private var currentProvider: Provider = Provider.GPT


    // 첨부 이미지 상태 관리
    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var thumbAdapter: ThumbAdapter

    // 결과 이미지 Byte
    private var resultImageBytes: ByteArray? = null


    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("SetTextI18n")
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            selectedImageUris.clear()

            // ★ 현재 provider 기준으로 max 개수 계산
            val maxImages = if (
                currentProvider == Provider.PIXEL_ART ||
                currentProvider == Provider.ANIMAL_CROSSING
            ) 1 else 4

            if (uris.isNullOrEmpty()) {
                tvSelected.text = "선택된 이미지 없음"
                rvThumbs.visibility = View.GONE
            } else {
                if (uris.size > maxImages) {
                    Toast.makeText(
                        this,
                        "최대 ${maxImages}장까지 선택됩니다. ${uris.size - maxImages}장은 제외됩니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                selectedImageUris.addAll(uris.take(maxImages))
                tvSelected.text = "총 ${selectedImageUris.size}장 선택됨 (최대 ${maxImages}장)"

                rvThumbs.visibility = View.VISIBLE
            }

            updateThumbs()
        }



    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainScope.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 바인딩
        spinnerProvider = findViewById(R.id.spinnerProvider)
        ivGuide = findViewById(R.id.ivGuide)                    // 이미지 생성 가이드
        btnPickImage = findViewById(R.id.btnPickImage)
        tvSelected = findViewById(R.id.tvSelected)
        etPrompt = findViewById(R.id.etPrompt)
        btnGenerate = findViewById(R.id.btnGenerate)
        progress = findViewById(R.id.progress)
        ivResult = findViewById(R.id.ivResult)
        btnDownload = findViewById(R.id.btnDownload)
        rvThumbs = findViewById(R.id.rvThumbs)                  // 썸네일 미리보기

        // 드롭다운 어댑터 설정
        spinnerProvider.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            Provider.values()      // enum 직접 사용 가능
        )



        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentProvider = Provider.values()[position]

                when (currentProvider) {
                    Provider.PIXEL_ART -> {
                        ivGuide.visibility = View.VISIBLE
                        ivGuide.setImageResource(R.drawable.guide_pixel_art)
                        etPrompt.visibility = View.GONE
                    }
                    Provider.ANIMAL_CROSSING -> {
                        ivGuide.visibility = View.VISIBLE
                        ivGuide.setImageResource(R.drawable.guide_ac_style)
                        etPrompt.visibility = View.GONE
                    }
                    else -> {
                        ivGuide.visibility = View.GONE
                        etPrompt.visibility = View.VISIBLE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 기본값: GPT
        spinnerProvider.setSelection(0)

        btnPickImage.setOnClickListener { pickImage.launch("image/*") }

        // 썸네일 RecyclerView 설정
        val gridLayoutManager = GridLayoutManager(this, 1)     // 처음엔 1열
        rvThumbs.layoutManager = gridLayoutManager
        thumbAdapter = ThumbAdapter()
        rvThumbs.adapter = thumbAdapter
        rvThumbs.visibility = View.GONE                        // 처음엔 숨김

        btnGenerate.setOnClickListener {
            val rawPrompt = etPrompt.text?.toString()?.trim().orEmpty()

            // 1) 이 provider들은 프롬프트가 필요 없음
            val promptNeeded =
                currentProvider != Provider.PIXEL_ART &&
                        currentProvider != Provider.ANIMAL_CROSSING

            // 2) PIXEL / AC는 이미지 1장 필수
            val requireExactlyOneImage =
                currentProvider == Provider.PIXEL_ART ||
                        currentProvider == Provider.ANIMAL_CROSSING

            // TODO: 다른 모드에서 4장 선택한 상태에서 모드 바꾸면 그대로 유지되는 오류 해결 필요

            if (requireExactlyOneImage && selectedImageUris.size != 1) {
                Toast.makeText(this, "이 모드는 반드시 이미지 1장을 첨부해야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 실제로 서버로 보낼 prompt (필요 없는 경우 null로 처리)
            val effectivePrompt: String? =
                if (promptNeeded) rawPrompt else null

            if (promptNeeded && effectivePrompt.isNullOrBlank()) {
                Toast.makeText(this, "프롬프트를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 여러 장을 그대로 넘긴다 (PIXEL/AC일 때는 위에서 size == 1로 보장)
            generateImage(effectivePrompt, selectedImageUris.toList())
        }



        btnDownload.setOnClickListener {
            resultImageBytes?.let { bytes ->
                val ok = saveToGallery(bytes)
                val msg = if (ok) "갤러리에 저장되었습니다." else "저장 실패"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnGenerate.isEnabled = !loading
        btnPickImage.isEnabled = !loading
        btnDownload.isEnabled = !loading && resultImageBytes != null
    }

    // 썸네일 갱신 및 위치 조절 함수
    private fun updateThumbs() {
        // 이미지 개수에 따라 열 개수 조정
        val count = selectedImageUris.size
        val lm = rvThumbs.layoutManager as? GridLayoutManager
        lm?.spanCount = when (count) {
            0, 1 -> 2          // 1장일 땐 크게 1열
            2 -> 2             // 2장이면 2열
            3, 4 -> 2          // 3~4장은 2x2 형태
            else -> 2
        }

        thumbAdapter.notifyDataSetChanged()
    }

    // 썸네일 어댑터 클래스
    private inner class ThumbAdapter : RecyclerView.Adapter<ThumbAdapter.ThumbVH>() {

        inner class ThumbVH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbVH {
            // 한 줄에 spanCount 개가 들어갈 때 각 썸네일의 한 변 길이 계산
            val span = (rvThumbs.layoutManager as? GridLayoutManager)?.spanCount ?: 1
            val parentWidth = parent.measuredWidth.takeIf { it > 0 }
                ?: (parent.resources.displayMetrics.widthPixels - parent.paddingLeft - parent.paddingRight)
            val size = parentWidth / span

            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(size, size)   // 정사각형 썸네일
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP           // 잘리지 않게 비율 유지 + 적당히 크롭
            }
            return ThumbVH(iv)
        }

        override fun getItemCount(): Int = selectedImageUris.size

        override fun onBindViewHolder(holder: ThumbVH, position: Int) {
            val uri = selectedImageUris[position]
            holder.iv.setImageURI(uri)
        }
    }

    // ====== 이미지 생성 ======
    private fun generateImage(prompt: String?, imageUris: List<Uri>) {
        setLoading(true)
        resultImageBytes = null
        ivResult.setImageDrawable(null)

        ioScope.launch {
            try {
                val providerStr = currentProvider.apiName

                val bytes = callProxyGenerate(
                    proxyBase = PROXY_BASE_URL,
                    provider = providerStr,
                    prompt = prompt,
                    imageUris = imageUris
                )

                if (bytes == null) throw RuntimeException("빈 응답")

                mainScope.launch {
                    resultImageBytes = bytes

                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ivResult.setImageBitmap(bmp)

                    btnDownload.isEnabled = true
                    setLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG_PROXY, "이미지 생성 실패", e)
                mainScope.launch { setLoading(false) }
            }
        }
    }


    /**
     * 프록시 서버 호출
     * 서버는 image/png 바이너리를 직접 반환해야 한다.
     */
    private fun callProxyGenerate(
        proxyBase: String,
        provider: String,
        prompt: String?,       // 👈 nullable
        imageUris: List<Uri>
    ): ByteArray? {
        val url = "$proxyBase/v1/images/generate"

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("provider", provider)

        // prompt가 있을 때만 form에 추가
        if (!prompt.isNullOrBlank()) {
            builder.addFormDataPart("prompt", prompt)
        } else {
            // 만약 서버에서 prompt를 필수로 Form(...) 받고 있다면,
            // 아래 한 줄로 빈 문자열만 보내도록 바꿀 수도 있음:
            // builder.addFormDataPart("prompt", "")
        }

        // 이하 이미지는 그대로 유지
        if (imageUris.isNotEmpty()) {
            for ((index, uri) in imageUris.withIndex()) {

                val mime = contentResolver.getType(uri)
                val bytes = readAllBytes(uri) ?: throw RuntimeException("이미지 읽기 실패")

                val (finalBytes, finalMime, filename) =
                    if (mime == null || !(mime == "image/png" || mime == "image/jpeg" || mime == "image/webp")) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                        Triple(bos.toByteArray(), "image/png", "input_$index.png")
                    } else {
                        val ext = when (mime) {
                            "image/jpeg" -> "jpg"
                            "image/webp" -> "webp"
                            else -> "png"
                        }
                        Triple(bytes, mime, "input_$index.$ext")
                    }

                val rb = finalBytes.toRequestBody(finalMime.toMediaTypeOrNull())
                builder.addFormDataPart("images", filename, rb)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(builder.build())
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(400) ?: "no body"
                throw RuntimeException("Proxy HTTP ${resp.code} :: $msg")
            }
            return resp.body?.bytes()
        }
    }




    private fun readAllBytes(uri: Uri): ByteArray? =
        contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun saveToGallery(bytes: ByteArray): Boolean {
        val fileName = "AI_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyKeyboard")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        return try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // === 메뉴: Meme Edit 화면으로 이동 ===
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_meme_edit -> {
                startActivity(android.content.Intent(this, MemeEditActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}