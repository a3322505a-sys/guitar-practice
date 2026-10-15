package com.a3322505a.guitarlearning

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.a3322505a.guitarlearning.audio.Player
import com.a3322505a.guitarlearning.ui.PracticeApp

/** Historical workbench regression fixture. Never included in the release APK.
 * These JVM tests cover retained UI/legacy behavior, not the native alphaTab entry point.
 */
class LegacyWorkbenchTestActivity : ComponentActivity() {
    private lateinit var player: Player
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveStatusBar(window)
        player = ViewModelProvider(this)[LegacyWorkbenchTestModel::class.java].player
        setContent { PracticeApp(player) }
    }
    override fun onResume() { super.onResume(); immersiveStatusBar(window) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveStatusBar(window)
    }
    override fun onStop() { if (!isChangingConfigurations) player.pause(); super.onStop() }
}

class LegacyWorkbenchTestModel(application: Application) : AndroidViewModel(application) {
    val player = Player(application)
    override fun onCleared() { player.release() }
}
