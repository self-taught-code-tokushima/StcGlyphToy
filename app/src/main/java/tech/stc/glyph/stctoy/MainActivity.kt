package tech.stc.glyph.stctoy

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 起動時に「Manage Glyph Toys へ移動」ボタンだけを出す最小アクティビティ。
 * README の Best Practice（配布時の誘導）に対応。
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "STC Toy を有効にするには、Glyph Toys 管理画面でこのトイを選択し、" +
                "Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy で選んでください。"
            textSize = 16f
        })
        layout.addView(Button(this).apply {
            text = "Manage Glyph Toys を開く"
            setOnClickListener {
                try {
                    startActivity(Intent().apply {
                        setComponent(
                            ComponentName(
                                "com.nothing.thirdparty",
                                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
                            )
                        )
                    })
                } catch (e: Exception) {
                    text = "Glyph Toys 画面を開けませんでした。手動で Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy を開いてください"
                }
            }
        })
        setContentView(layout)
    }
}
