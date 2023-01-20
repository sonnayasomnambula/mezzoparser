package io.github.sonnayasomnambula.mezzoparser

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(LOG_TAG, "Activity: onCreate")

        Intent(this, Parser::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "Activity: onDestroy")
    }
}