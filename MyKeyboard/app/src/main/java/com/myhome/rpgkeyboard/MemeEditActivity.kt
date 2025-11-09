package com.myhome.rpgkeyboard

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class MemeEditActivity : AppCompatActivity() {

    private val PROXY_BASE_URL = BuildConfig.PROXY_BASE_URL
    private val BASE_ASSET = "base/galteya.png"   // 고정 원본 경로(assets)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var spinnerMask: Spinner
    private lateinit var etPrompt: EditText
    private lateinit var btnRun: Button
    private lateinit var progress: ProgressBar
    private lateinit var ivResult: ImageView
    private lateinit var btnSave: Button

    private var resultBytes: ByteArray? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }


    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainScope.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meme_edit)
        supportActionBar?.title = "Meme Edit (마스크)"

        spinnerMask = findViewById(R.id.spinnerMask)
        etPrompt = findViewById(R.id.etPrompt2)
        btnRun = findViewById(R.id.btnRun)
        progress = findViewById(R.id.progress2)
        ivResult = findViewById(R.id.ivResult2)
        btnSave = findViewById(R.id.btnSave)

        // 앱에 동봉한 마스크 파일명 목록
        val masks = listOf(
            "mask_concert.png",
            "mask_siq_left.png",
            "mask_siq_right.png",
            "mask_building.png",
            "mask_building2.png"
        )
        spinnerMask.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, masks)


        btnRun.setOnClickListener {
            val p = etPrompt.text?.toString()?.trim().orElse("")
            val maskFile = spinnerMask.selectedItem as String
            if (p.isEmpty()) {
                toast("프롬프트를 입력하세요"); return@setOnClickListener
            }
            runEditWithFixedBase(p, maskFile)
        }

        btnSave.setOnClickListener {
            resultBytes?.let { b ->
                val ok = saveToGallery(b)
                toast(if (ok) "갤러리에 저장됨" else "저장 실패")
            }
        }
    }

    private fun setLoading(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
        btnRun.isEnabled = !b
        btnSave.isEnabled = !b && resultBytes != null
    }

    private fun runEditWithFixedBase(prompt: String, maskAsset: String) {
        setLoading(true)
        resultBytes = null
        ivResult.setImageDrawable(null)

        ioScope.launch {
            try {
                // ✅ base (assets 고정) → PNG 바이트
                val basePng = assets.open(BASE_ASSET).use { it.readBytes() }

                // mask (assets) → PNG 바이트
                val maskBytes = assets.open("masks/$maskAsset").use { it.readBytes() }

                val url = "${PROXY_BASE_URL}/api/meme_edit"

                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("prompt", prompt)
                    .addFormDataPart(
                        "base_image", "base.png",
                        basePng.toRequestBody("image/png".toMediaType())
                    )
                    .addFormDataPart(
                        "mask_image", maskAsset,
                        maskBytes.toRequestBody("image/png".toMediaType())
                    )
                    .build()

                val req = Request.Builder().url(url).post(form).build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = resp.body?.string().orEmpty()
                        throw RuntimeException("Proxy ${resp.code} :: $msg")
                    }
                    // { image_base64: "..." } JSON이라면 Gson 써도 되지만, 프록시가 바이너리 PNG 반환이면 bytes로 받음
                    // 여기선 JSON 응답 기준(앞서 테스트한 /api/meme_edit 예시)으로 처리:
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val b64 = json.optString("image_base64")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    mainScope.launch {
                        resultBytes = bytes
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ivResult.setImageBitmap(bmp)
                        setLoading(false)
                        btnSave.isEnabled = true
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                mainScope.launch {
                    toast("실패: ${t.message}")
                    setLoading(false)
                }
            }
        }
    }



    private fun saveToGallery(bytes: ByteArray): Boolean {
        val name = "Meme_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JJalBox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            true
        } catch (_: Throwable) { false }
    }

    private fun String?.orElse(def: String) = if (this.isNullOrBlank()) def else this
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
