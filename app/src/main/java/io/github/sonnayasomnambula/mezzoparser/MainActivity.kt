package io.github.sonnayasomnambula.mezzoparser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import io.github.sonnayasomnambula.mezzoparser.databinding.ActivityMainBinding
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity() {

    private lateinit var ui: ActivityMainBinding
    private lateinit var settings: SharedPreferences

    private var savedTextColor: Int? = null

    inner class PermissionChecker {
        private var required = ArrayList<String>()

        fun add(perm: String) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                required.add(perm)
            }
        }

        fun check() {
            if (required.isEmpty()) {
                onPermissionGranted()
                return
            }

            val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.containsValue(false)) {
                    Toast.makeText(applicationContext, "Error: no permission granted", Toast.LENGTH_LONG).show()
                } else {
                    onPermissionGranted()
                }
            }

            launcher.launch(required.toTypedArray())
        }
    }

    inner class FileCreator : ActivityResultContract<Void?, Uri?>() {
        override fun createIntent(context: Context, input: Void?) =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/xml"
                putExtra(Intent.EXTRA_TITLE, "mezzo.xml")
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            val uri = intent?.data
            Log.d(LOG_TAG, "result uri: $uri") // URI: content://com.android.externalstorage.documents/document/primary%3AMovies%2Fmezzo.xml
            if (uri == null || resultCode != Activity.RESULT_OK)
                return null
            ui.label.text = uri.toString()
            ui.label.setTextColor(savedTextColor ?: ui.label.currentTextColor)

            settings.edit().putString(Settings.Tags.URI, uri.toString()).apply()
            return uri
        }

    }

    val saver = registerForActivityResult(FileCreator()) { uri: Uri? ->
        Log.d(LOG_TAG, "URI: $uri")
        if (uri != null) {
            Toast.makeText(this, "File created", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPermissionGranted() {
        ui.btnCreateFile.isEnabled = true
        ui.btnStartService.isEnabled = true

        try {
            val path = ui.label.text.toString()
            val stream = contentResolver.openInputStream(Uri.parse(path))
            if (stream == null) {
                throw FileNotFoundException("Something goes wrong... Try to push CREATE FILE button.")
            } else {
                stream.close()
            }
        } catch (e : FileNotFoundException) {
            savedTextColor = ui.label.currentTextColor
            ui.label.text = e.message
            ui.label.setTextColor(Color.RED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        settings = getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE)

        Log.d(LOG_TAG, "Activity: onCreate")

        ui.btnCreateFile.setOnClickListener {
            saver.launch(null)
        }

        ui.btnStartService.setOnClickListener {
            Intent(this, ParserService::class.java).also { intent ->
                startService(intent)
            }
        }

        ui.label.text = settings.getString(Settings.Tags.URI, "")

        val checker = PermissionChecker()
        checker.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        // If your app targets Android 11, both the WRITE_EXTERNAL_STORAGE permission and the
        // WRITE_MEDIA_STORAGE privileged permission no longer provide any additional access.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            checker.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        checker.check()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "Activity: onDestroy")
    }
}