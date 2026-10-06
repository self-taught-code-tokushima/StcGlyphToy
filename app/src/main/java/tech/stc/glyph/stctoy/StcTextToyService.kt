package tech.stc.glyph.stctoy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy

/**
 * Phone (4a) Pro 用の AOD Glyph Toy。
 * 背面 Glyph Matrix (13x13) に "STC" を固定表示する。
 *
 * - marqueeType 既定値 = TYPE_MARQUEE_NONE(0) なので setText のみで固定表示
 * - 4a Pro は Glyph Touch 非対応だが、AOD 中に毎分 EVENT_AOD が届くため
 *   Messenger を返して再描画に備える
 */
class StcTextToyService : Service() {

    companion object {
        private const val TAG = "StcGlyphToy"
        private const val FIXED_TEXT = "STC"
    }

    private var gm: GlyphMatrixManager? = null

    private val handler = Handler(Looper.getMainLooper())

    private val serviceHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    val event: String? = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                    if (GlyphToy.EVENT_AOD == event) {
                        // AOD として選ばれている間、毎分呼ばれる。他アプリ等の
                        // 割り込みで消えていても復旧できるよう再描画する。
                        Log.d(TAG, "EVENT_AOD: redraw")
                        showFixedText()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(serviceHandler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            Log.d(TAG, "onServiceConnected")
            gm?.let {
                it.register(Glyph.DEVICE_25111p) // Phone (4a) Pro
                showFixedText()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        gm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        gm?.let {
            it.turnOff()
            it.unInit()
        }
        gm = null
        return true
    }

    private fun showFixedText() {
        val gmm = gm ?: return
        val len = Common.getDeviceMatrixLength() // 4a Pro = 13
        handler.post {
            try {
                // "STC" の 3 文字。NDot 既定グリフ。marqueeType 未設定 = 固定表示。
                val textObj = GlyphMatrixObject.Builder()
                    .setText(FIXED_TEXT)
                    .setPosition(0, len / 2 - 3) // 縦中央（実機確認済: -2 下すぎ / -5 上すぎ / -3 が丁度）
                    .setBrightness(255)
                    .build()

                val frame = GlyphMatrixFrame.Builder()
                    .addTop(textObj)
                    .build(applicationContext)

                gmm.setMatrixFrame(frame.render())
                Log.d(TAG, "rendered \"$FIXED_TEXT\" on ${len}x${len} matrix")
            } catch (e: Exception) {
                Log.e(TAG, "render failed", e)
            }
        }
    }
}
