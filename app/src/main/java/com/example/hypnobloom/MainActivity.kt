package com.example.hypnobloom

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "HypnoBloom v0.1\nContinuous motion works without audio. Grant audio access for optional music reactivity."
            textSize = 20f
        })
        layout.addView(Button(this).apply {
            text = "Enable music reactivity"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                }
            }
        })
        layout.addView(Button(this).apply {
            text = "Preview / set wallpaper"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, HypnoWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            }
        })
        setContentView(layout)
    }
}