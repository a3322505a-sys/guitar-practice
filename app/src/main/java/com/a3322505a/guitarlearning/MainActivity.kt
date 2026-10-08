package com.a3322505a.guitarlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import com.a3322505a.guitarlearning.audio.Player
import com.a3322505a.guitarlearning.ui.PracticeApp

class MainActivity : ComponentActivity() {
    private lateinit var player: Player
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ViewModelProvider(this)[PracticeModel::class.java].player
        setContent { PracticeApp(player) }
    }
    override fun onStop() { if (!isChangingConfigurations) player.pause(); super.onStop() }
}

class PracticeModel(application: Application) : AndroidViewModel(application) {
    val player = Player(application)
    override fun onCleared() { player.release() }
}
