package com.vidgravity.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (22 * resources.displayMetrics.density).toInt()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "VidGravity"; textSize = 28f })
            addView(TextView(context).apply {
                text = "Share a public media link from another app to start a VidGravity download. Save a preferred preset to enable automatic mode."
                textSize = 15f
            })
        })
    }
}
