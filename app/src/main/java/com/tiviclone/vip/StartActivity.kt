package com.tiviclone.vip

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tiviclone.vip.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLiveTv.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("URL", "https://raw.githubusercontent.com/Adolfo761/lista-iptv-permanente/main/playlist.m3u")
            startActivity(intent)
        }

        binding.btnVod.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java) // Reusing MainActivity for now or VodActivity if implemented
            intent.putExtra("URL", "https://raw.githubusercontent.com/Adolfo761/lista-iptv-permanente/main/vod.m3u")
            intent.putExtra("IS_VOD", true)
            startActivity(intent)
        }
        
        binding.btnLiveTv.requestFocus()
    }
}
