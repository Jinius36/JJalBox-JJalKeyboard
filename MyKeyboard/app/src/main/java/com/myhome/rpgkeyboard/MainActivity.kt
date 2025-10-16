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
    private val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY
    private val OPENAI_BASE_URL = BuildConfig.OPENAI_BASE_URL
    private val IMAGE_MODEL = BuildConfig.OPENAI_IMAGE_MODEL

    // === Gemini 설정 ===
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val GEMINI_IMAGE_MODEL = BuildConfig.GEMINI_IMAGE_MODEL
    private val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    private val TAG_GPT = "GPT_IMAGE"
    private val TAG_GEMINI = "GEMINI_IMAGE"



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
                val bytes = when (currentProvider) {
                    Provider.GPT -> {
                        if (imageUri == null) callOpenAITextToImage(prompt)
                        else callOpenAIImageEdit(prompt, imageUri)
                    }
                    Provider.GEMINI -> {
                        if (imageUri == null) callGeminiTextToImage(prompt)
                        else callGeminiImageEdit(prompt, imageUri)
                    }
                }

                if (bytes == null) throw RuntimeException("빈 응답")

                mainScope.launch {
                    resultImageBytes = bytes
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ivResult.setImageBitmap(bmp)
                    btnDownload.isEnabled = true
                    setLoading(false)
                }
            } catch (e: Exception) {
                val tag = if (currentProvider == Provider.GPT) TAG_GPT else TAG_GEMINI
                Log.e(tag, "이미지 생성 실패", e)
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

    /** 텍스트 → 이미지 생성 */
    private fun callOpenAITextToImage(prompt: String): ByteArray? {
        val url = "$OPENAI_BASE_URL/images/generations"
        val json = JSONObject().apply {
            put("model", IMAGE_MODEL)
            put("prompt", prompt)
            put("size", "1024x1024")
            // response_format 안 넣음 (문서상 기본 응답은 url이지만, b64_json이 오는 케이스 대비 폴백 처리)
        }.toString()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code} :: ${bodyStr.take(500)}")
            }

            // data[0]에서 url 또는 b64_json 처리
            val data0 = JSONObject(bodyStr)
                .getJSONArray("data")
                .getJSONObject(0)

            // 1순위: url
            val urlStr = data0.optString("url", null)
            if (!urlStr.isNullOrEmpty()) {
                return downloadBytes(urlStr)
            }

            // 2순위: b64_json
            val b64 = data0.optString("b64_json", null)
            if (!b64.isNullOrEmpty()) {
                val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                return reencodeToPng(raw)
            }

            // 둘 다 없으면 응답 본문 일부와 함께 예외
            throw RuntimeException("응답에 url/b64_json 없음 :: ${bodyStr.take(500)}")
        }
    }


    /** 이미지 편집 (첨부 이미지 + 프롬프트) */
    private fun callOpenAIImageEdit(prompt: String, imageUri: Uri): ByteArray? {
        val url = "$OPENAI_BASE_URL/images/edits"

        // 편집은 PNG가 안전하니 PNG로 업로드 (JPEG 선택해도 여기서 PNG로 통일)
        val original = readAllBytes(imageUri) ?: throw RuntimeException("이미지 읽기 실패")
        val bmp = BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: throw RuntimeException("이미지 디코드 실패")
        val pngBytes = ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }

        val imageBody = pngBytes.toRequestBody("image/png".toMediaTypeOrNull())

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", IMAGE_MODEL)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("size", "1024x1024")
            .addFormDataPart("image", "input.png", imageBody)
            .build()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(multipart)
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code} :: ${bodyStr.take(500)}")
            }

            val data0 = JSONObject(bodyStr)
                .getJSONArray("data")
                .getJSONObject(0)

            val urlStr = data0.optString("url", null)
            if (!urlStr.isNullOrEmpty()) {
                return downloadBytes(urlStr)
            }

            val b64 = data0.optString("b64_json", null)
            if (!b64.isNullOrEmpty()) {
                val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                return reencodeToPng(raw)
            }

            throw RuntimeException("응답에 url/b64_json 없음 :: ${bodyStr.take(500)}")
        }
    }

    // Gemini 응답에서 첫 번째 base64 이미지를 꺼내서 PNG로 통일
    private fun extractGeminiImageBytes(responseJson: String): ByteArray {
        val root = org.json.JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
            ?: throw RuntimeException("Gemini 응답에 candidates 없음 :: ${responseJson.take(300)}")
        val first = candidates.getJSONObject(0)
        val content = first.getJSONObject("content")
        val parts = content.getJSONArray("parts")

        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            // inline_data (snake_case) 또는 inlineData (camelCase)
            val blob = p.optJSONObject("inline_data") ?: p.optJSONObject("inlineData")
            if (blob != null && blob.has("data")) {
                val b64 = blob.getString("data")
                val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                // 그대로 써도 되지만, 갤러리 저장 일관성을 위해 PNG 재인코딩
                return reencodeToPng(raw)
            }
        }
        // 텍스트만 올 경우 대비(가이던스 메시지 등)
        val textPart = parts.optJSONObject(0)?.optString("text")
        throw RuntimeException("Gemini 응답에 이미지가 없습니다. text=${textPart ?: "없음"}")
    }


    // 텍스트 → 이미지 (Gemini)
    private fun callGeminiTextToImage(prompt: String): ByteArray? {
        val url = "$GEMINI_BASE_URL/models/$GEMINI_IMAGE_MODEL:generateContent"

        // 공식 REST와 동일한 형태: contents.parts에 text만
        val bodyJson = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(org.json.JSONObject().put("text", prompt))
                    })
                })
            })
        }.toString()

        val req = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Gemini HTTP ${resp.code} :: ${bodyStr.take(500)}")
            }
            return extractGeminiImageBytes(bodyStr)
        }
    }



    // 이미지 편집 (텍스트 + 입력 이미지 → 새 이미지)
    private fun callGeminiImageEdit(prompt: String, imageUri: Uri): ByteArray? {
        val url = "$GEMINI_BASE_URL/models/$GEMINI_IMAGE_MODEL:generateContent"

        // 입력 이미지를 PNG로 확보
        val original = readAllBytes(imageUri) ?: throw RuntimeException("이미지 읽기 실패")
        val bmp = BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: throw RuntimeException("이미지 디코드 실패")
        val pngBytes = java.io.ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos); bos.toByteArray()
        }
        val b64 = android.util.Base64.encodeToString(pngBytes, android.util.Base64.NO_WRAP)

        // 공식 REST 구조: contents.parts = [ {text}, {inlineData:{mimeType,data}} ]
        val bodyJson = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(org.json.JSONObject().put("text", prompt))
                        put(org.json.JSONObject().put("inlineData",
                            org.json.JSONObject()
                                .put("mimeType", "image/png")
                                .put("data", b64)
                        ))
                    })
                })
            })
        }.toString()

        val req = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Gemini HTTP ${resp.code} :: ${bodyStr.take(500)}")
            }
            return extractGeminiImageBytes(bodyStr)
        }
    }


    /** 이미지 URL → 바이트 다운로드 */
    private fun downloadBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw RuntimeException("이미지 다운로드 실패: HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw RuntimeException("빈 응답")
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
}
