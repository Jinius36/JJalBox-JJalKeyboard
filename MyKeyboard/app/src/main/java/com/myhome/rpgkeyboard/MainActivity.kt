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
    private val IMAGE_MODEL = BuildConfig.IMAGE_MODEL

    // ====== UI ======
    private lateinit var btnPickImage: Button
    private lateinit var tvSelected: TextView
    private lateinit var etPrompt: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progress: ProgressBar
    private lateinit var ivResult: ImageView
    private lateinit var btnDownload: Button

    // ====== 상태 ======
    private var selectedImageUri: Uri? = null
    private var resultImageBytes: ByteArray? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 연결 대기
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // 응답 대기
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // 전송 대기
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

        btnPickImage = findViewById(R.id.btnPickImage)
        tvSelected = findViewById(R.id.tvSelected)
        etPrompt = findViewById(R.id.etPrompt)
        btnGenerate = findViewById(R.id.btnGenerate)
        progress = findViewById(R.id.progress)
        ivResult = findViewById(R.id.ivResult)
        btnDownload = findViewById(R.id.btnDownload)

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
                val bytes = if (imageUri == null)
                    callOpenAITextToImage(prompt)
                else
                    callOpenAIImageEdit(prompt, imageUri)

                if (bytes == null) throw RuntimeException("빈 응답")

                mainScope.launch {
                    resultImageBytes = bytes
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ivResult.setImageBitmap(bmp)
                    btnDownload.isEnabled = true
                    setLoading(false)
                }
            } catch (e: Exception) {
                Log.e("GPT_IMAGE", "이미지 생성 실패", e)
                mainScope.launch { setLoading(false) }
            }
        }
    }

    // 원시 바이트(raw)가 JPG/PNG 등 어떤 포맷이든 Bitmap으로 디코드한 뒤
    // PNG로 다시 인코딩해서 바이트 배열로 반환
    private fun reencodeToPng(raw: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw RuntimeException("이미지 디코드 실패")
        return ByteArrayOutputStream().use { bos ->
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
