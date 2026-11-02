package com.a3322505a.guitarlearning

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
        val model = ViewModelProvider(this)[PracticeModel::class.java]
        player = model.player
        if (!model.initialIntentHandled) {
            model.acceptImport(intent)
            model.initialIntentHandled = true
        }
        setContent { PracticeApp(player, model.pendingImport) { model.pendingImport = null } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ViewModelProvider(this)[PracticeModel::class.java].acceptImport(intent)
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
    var initialIntentHandled = false
    var pendingImport by mutableStateOf<Pair<Int, Uri>?>(null)
    private var importNumber = 0
    @Suppress("DEPRECATION")
    fun acceptImport(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)
            else -> null
        }
        if (uri?.scheme == "content") pendingImport = ++importNumber to uri
    }
    override fun onCleared() { player.release() }
}
