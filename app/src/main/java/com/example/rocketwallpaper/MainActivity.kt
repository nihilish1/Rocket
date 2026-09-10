package com.example.rocketwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * A minimal launcher screen. Some launchers (including some Samsung One UI
 * versions) don't reliably list third-party live wallpapers in their own
 * "Wallpaper services" picker. This screen talks to Android directly
 * instead, so it works regardless of what the launcher's picker shows.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        val label = TextView(this).apply {
            text = "Rocket Wallpaper"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 48)
        }

        val button = Button(this).apply {
            text = "Set as Live Wallpaper"
            setOnClickListener { applyWallpaper() }
        }

        val settingsButton = Button(this).apply {
            text = "Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        layout.addView(label)
        layout.addView(button)
        layout.addView(settingsButton)
        setContentView(layout)
    }

    private fun applyWallpaper() {
        val component = ComponentName(this, RocketWallpaperService::class.java)
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Couldn't open the wallpaper picker: ${e2.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
