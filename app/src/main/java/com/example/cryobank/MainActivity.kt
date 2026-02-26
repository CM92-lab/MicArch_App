package com.example.cryobank

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_merge).setOnClickListener {
            startActivity(Intent(this, BarcodeMergingActivity::class.java))
        }

        findViewById<Button>(R.id.btn_finder).setOnClickListener {
            startActivity(Intent(this, SampleFinderActivity::class.java))
        }

        findViewById<Button>(R.id.btn_live_scan).setOnClickListener {
            startActivity(
                Intent(this, LiveScanActivity::class.java)
            )
        }

        findViewById<Button>(R.id.btn_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))

        }



    }

}
