package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1000dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    @Test fun complexImportedPlansActuallyDrawVoicesTupletsAndChangingMeters() {
        val contract=File("../contracts/song-package/v1")
        val reader=SongReader(SongSchema { File(contract,it).readBytes() })
        for(name in listOf("complex","nested","techniques","harmonics","pickup")) {
            val song=reader.read(File(contract,"fixtures/"+name+".gpractice").readBytes())
            val plan=PracticeCompiler.compile(song.score,song.practices.single())
            val seq=Sequence(emptyList(),ceil(plan.length.value()*12).toInt(),"render",runtimePlan=plan)
            compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false,Modifier.fillMaxSize()) } }
            compose.waitForIdle()
            compose.onNodeWithTag("tablature").assertIsDisplayed()
            capture(name+"-first")
            if(name=="complex") {
                compose.onNodeWithTag("tablature").onChildren().filterToOne(hasScrollToIndexAction()).performScrollToIndex(1)
                capture(name+"-compound")
            }
        }
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=File("../work/music-package/screens/"+name+".png");file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
}
