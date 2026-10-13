package com.vidgravity.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
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
            addView(Button(context).apply {
                text = "Open Snap Studio Camera"
                setOnClickListener {
                    startActivityForResult(Intent(context, com.vidgravity.camera.CameraCaptureActivity::class.java), 7302)
                }
            })
        })
    }

    @Deprecated("Activity result is used by the camera capture handoff")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7302 && resultCode == RESULT_OK && data?.data != null) {
            Toast.makeText(this, "Capture ready for Studio: ${data.data}", Toast.LENGTH_LONG).show()
        }
    }
}
