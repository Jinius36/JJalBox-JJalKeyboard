package com.myhome.rpgkeyboard

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

class MainActivity : AppCompatActivity() {

    // ====== 설정 ======
    private val PROXY_BASE_URL = BuildConfig.PROXY_BASE_URL
    private val TAG_PROXY = "IMAGE_PROXY"



    // ====== UI ======
    private lateinit var spinnerProvider: Spinner
    private lateinit var btnPickImage: Button
    private lateinit var tvSelected: TextView
    private lateinit var etPrompt: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progress: ProgressBar
    private lateinit var ivResult: ImageView
    private lateinit var btnDownload: Button

    // ====== 상태 ======
    private enum class Provider { GPT, GEMINI }
    private var currentProvider: Provider = Provider.GPT

    private var selectedImageUri: Uri? = null
    private var resultImageBytes: ByteArray? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            selectedImageUri = uri
            tvSelected.text = uri?.lastPathSegment ?: "선택된 이미지 없음"
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
        btnPickImage = findViewById(R.id.btnPickImage)
        tvSelected = findViewById(R.id.tvSelected)
        etPrompt = findViewById(R.id.etPrompt)
        btnGenerate = findViewById(R.id.btnGenerate)
        progress = findViewById(R.id.progress)
        ivResult = findViewById(R.id.ivResult)
        btnDownload = findViewById(R.id.btnDownload)

        // 드롭다운 어댑터 설정 ("GPT", "Gemini")
        val providers = listOf("GPT", "Gemini")
        spinnerProvider.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                currentProvider = if (position == 0) Provider.GPT else Provider.GEMINI
                Log.d("IMAGE_PROVIDER", "선택: $currentProvider")
            }
            override fun onNothingSelected(parent: AdapterView<*>) { /* no-op */ }
        }
        // 기본값: GPT
        spinnerProvider.setSelection(0)

        btnPickImage.setOnClickListener { pickImage.launch("image/*") }

        btnGenerate.setOnClickListener {
            val prompt = etPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isBlank()) {
                Toast.makeText(this, "프롬프트를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateImage(prompt, selectedImageUri)
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

    private fun generateImage(prompt: String, imageUri: Uri?) {
        setLoading(true)
        resultImageBytes = null
        ivResult.setImageDrawable(null)

        ioScope.launch {
            try {
                val providerStr = if (currentProvider == Provider.GPT) "gpt" else "gemini"
                val mode = if (imageUri == null) "text2image" else "edit"
                val bytes = callProxyGenerate(
                    proxyBase = PROXY_BASE_URL,
                    provider = providerStr,
                    mode = mode,
                    prompt = prompt,
                    size = "1024x1024",
                    imageUri = imageUri
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
//                val tag = if (currentProvider == Provider.GPT) TAG_GPT else TAG_GEMINI
//                Log.e(tag, "이미지 생성 실패", e)
                Log.e(TAG_PROXY, "이미지 생성 실패", e)
                mainScope.launch { setLoading(false) }
            }

        }
    }

    // 원시 바이트(raw)가 JPG/PNG 등 어떤 포맷이든 Bitmap으로 디코드한 뒤
    // PNG로 다시 인코딩해서 바이트 배열로 반환
    private fun reencodeToPng(raw: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw RuntimeException("이미지 디코드 실패")
        return java.io.ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
    }

    /**
     * 프록시 서버 호출 (provider/gpt|gemini, mode/text2image|edit)
     * 서버는 image/png 바이너리를 직접 반환해야 한다.
     */
    private fun callProxyGenerate(
        proxyBase: String,
        provider: String,
        mode: String,
        prompt: String,
        size: String,
        imageUri: Uri?
    ): ByteArray? {
        val url = "$proxyBase/v1/images/generate"

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("provider", provider)
            .addFormDataPart("mode", mode)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("size", size)

        if (imageUri != null) {
            // MIME 추출 (없으면 PNG로 변환)
            val mime = contentResolver.getType(imageUri)
            val bytes = readAllBytes(imageUri) ?: throw RuntimeException("이미지 읽기 실패")

            val (finalBytes, finalMime, filename) =
                if (mime == null || !(mime == "image/png" || mime == "image/jpeg" || mime == "image/webp")) {
                    // 안전하게 PNG로 변환
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    Triple(bos.toByteArray(), "image/png", "input.png")
                } else {
                    // 원래 MIME 사용
                    val ext = when (mime) {
                        "image/jpeg" -> "jpg"
                        "image/webp" -> "webp"
                        else -> "png"
                    }
                    Triple(bytes, mime, "input.$ext")
                }

            val rb = finalBytes.toRequestBody(finalMime.toMediaTypeOrNull())
            builder.addFormDataPart("image", filename, rb)
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