package com.a3322505a.guitarlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.a3322505a.guitarlearning.audio.Player
import com.a3322505a.guitarlearning.ui.PracticeApp

class MainActivity : ComponentActivity() {
    private lateinit var player: Player
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = Player(this)
        setContent { PracticeApp(player) }
    }
    override fun onStop() { player.pause(); super.onStop() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}
