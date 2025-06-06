package com.myhome.rpgkeyboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 예시: 설정 화면 상단의 “제출” 버튼을 숨기고 싶다면
        // val settingHeader = findViewById<View>(R.id.setting_header)
        // val submitButton = settingHeader.findViewById<TextView>(R.id.submit_text)
        // submitButton.visibility = View.GONE
    }
}