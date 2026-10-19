@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning.alphatab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.Event
import com.a3322505a.guitarlearning.practice.Sequence
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** P0-only A/B of the real SDK scroll surface. No production library or settings are changed. */
class TeachingViewportProbeActivity : ComponentActivity() {
    private val handler=Handler(Looper.getMainLooper())
    private var started=false
    private var closed=false
    private val results=JSONArray()
    private lateinit var host: PracticeScoreHost
    private var fixed=true
    @Volatile private var lastPaint=0L
    private val names=listOf("top-before","bottom","top-return","middle","top-final","after-visible","resized","after-resize")
    private var capturedInk = 0
    private fun capture(name: String, commit: Boolean = true): String {
        val bitmap=Bitmap.createBitmap(host.width,host.height,Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val ink=pixels.count { Color.alpha(it)>0 && Color.red(it)<180 && Color.green(it)<180 && Color.blue(it)<180 }
        capturedInk=ink
        val bytes=java.nio.ByteBuffer.allocate(pixels.size*4)
        pixels.forEach { bytes.putInt(it) }
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
        val directory=File(filesDir,"viewport-probe").apply { mkdirs() }
        if(commit) File(directory,"${if(fixed) "fixed" else "baseline"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        val item=JSONObject().put("step",name).put("inkPixels",ink).put("pixelHash",sha)
        if(commit) { results.put(item); Log.i("ViewportProbe",item.toString()) }
        return sha
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fixed=intent.getBooleanExtra("fixed",true)
        host=PracticeScoreHost(this,fixed).apply { setBackgroundColor(Color.WHITE) }
        setContentView(host)
        val native=host.native
        native.settings=AlphaScoreAdapter.settings().apply { player.playerMode=alphaTab.PlayerMode.Disabled }
        native.setBackgroundColor(Color.WHITE)
        val sequence=Sequence((0 until 128).map { Event(it*12,12,listOf(Position(3,it%4+1))) },128*12,"Rendering fixture")
        native.api.renderer.partialRenderFinished.on { lastPaint=android.os.SystemClock.uptimeMillis() }
        native.api.renderFinished.on {
            if(!started) {
                started=true
                handler.postDelayed({ runStep(0) },2000)
            }
        }
        // Start this standalone fixture after its initial Android measurement. The SDK's
        // constructor otherwise sees width=0; both A/B branches use the same bootstrap.
        val tracks=AlphaScoreAdapter.fromSequence(sequence).tracks.toList()
        val outer=native.findViewById<android.widget.HorizontalScrollView>(net.alphatab.R.id.outerScroll)
        outer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if(outer.measuredWidth<=0) return
                outer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                native.api.renderer.width=outer.measuredWidth/resources.displayMetrics.density.toDouble()
                native.tracks=tracks
            }
        })
    }
    private fun runStep(step: Int, previous: String? = null, stable: Int = 0, attempts: Int = 0) {
        if(closed) return
        val name=names[step]
        val hash=capture(name,false)
        val unchanged=if(hash==previous) stable+1 else 0
        val settled=capturedInk>1000 && unchanged>=2 && android.os.SystemClock.uptimeMillis()-lastPaint>=700
        if(!settled && attempts<48) {
            handler.postDelayed({ runStep(step,hash,unchanged,attempts+1) },500)
            return
        }
        capture(name)
        results.getJSONObject(results.length()-1).put("settled",settled).put("samples",attempts+1)
        val scroll=host.native.findViewById<ScrollView>(net.alphatab.R.id.innerScroll)
        val bottom=(scroll.getChildAt(0).height-scroll.height).coerceAtLeast(0)
        when(step) {
            0 -> scroll.scrollTo(0,bottom)
            1 -> scroll.scrollTo(0,0)
            2 -> scroll.scrollTo(0,bottom/2)
            3 -> scroll.scrollTo(0,0)
            4 -> {
                host.visibility=android.view.View.INVISIBLE
                handler.postDelayed({ host.visibility=android.view.View.VISIBLE },500)
            }
            5 -> host.layoutParams=host.layoutParams.apply { height=host.height*2/3 }
            6 -> host.layoutParams=host.layoutParams.apply { height=android.view.ViewGroup.LayoutParams.MATCH_PARENT }
            7 -> {
                val result=JSONObject().put("fixed",fixed).put("scrollRange",bottom).put("captures",results)
                File(filesDir,"viewport-probe/${if(fixed) "fixed" else "baseline"}.json").writeText(result.toString(2))
                Log.i("ViewportProbe","COMPLETE $result")
                return
            }
        }
        handler.postDelayed({ runStep(step+1) },1000)
    }
    override fun onDestroy() { closed=true;handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
