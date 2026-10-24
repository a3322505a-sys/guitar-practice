package com.a3322505a.guitarlearning

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import com.a3322505a.guitarlearning.alphatab.AlphaPlayback
import com.a3322505a.guitarlearning.ui.PracticeApp

class MainActivity : ComponentActivity() {
    private lateinit var player: AlphaPlayback
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveStatusBar(window)
        player = ViewModelProvider(this)[PracticeModel::class.java].player
        setContent { PracticeApp(player) }
    }
    override fun onResume() { super.onResume(); immersiveStatusBar(window) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveStatusBar(window)
    }
    override fun onStop() { if (!isChangingConfigurations) player.pause(); super.onStop() }
}

/** One window policy for portrait, TAB, and the settings dialog. No view switch shows bars. */
internal fun immersiveStatusBar(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
    }
}

class PracticeModel(application: Application) : AndroidViewModel(application) {
    val player = AlphaPlayback(application)
    override fun onCleared() { player.release() }
}
