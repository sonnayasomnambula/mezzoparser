package io.github.sonnayasomnambula.mezzoparser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class Parser : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "Service: onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "Service: onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "Service: onDestroy")
        super.onDestroy()
    }
}