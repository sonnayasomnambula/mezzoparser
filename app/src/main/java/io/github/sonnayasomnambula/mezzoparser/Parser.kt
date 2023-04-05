package io.github.sonnayasomnambula.mezzoparser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.StringWriter
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ParserThread(private val context: Context, private val resolver: ContentResolver, private val settings: SharedPreferences) : Thread() {
    private fun writeFile(uri: Uri, text: String) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val stream = resolver.openOutputStream(uri)
            if (stream == null) {
                notify(NotificationLevel.WARNINIG, "Unable to write $uri",)
                return
            }

            stream.use {
                stream.write(text.toByteArray())
                stream.flush()
            }

            notify(NotificationLevel.INFO, "write ok: $uri")
        } catch (e: java.io.FileNotFoundException) {
            notify(NotificationLevel.WARNINIG, e.localizedMessage)
        } catch (e: java.lang.Exception) {
            notify(NotificationLevel.WARNINIG, "exception while save '$uri'")
            Log.e(LOG_TAG, Log.getStackTraceString(e))
        }
    }

    private fun findTime(el: Element) : LocalTime? {
        for (child in el.children()) {
            if (child.tagName() == "span") {
                try {
                    return LocalTime.parse(child.text().trim(), DateTimeFormatter.ofPattern("H:mm"))
                } catch (e: java.time.format.DateTimeParseException) {
                    Log.w(LOG_TAG, "Parse error: '${child.text()}' is not a valid time string")
                }
            } else {
                val time = findTime(child)
                if (time != null)
                    return time
            }
        }

        return null
    }

    private fun findTitle(el: Element) : String? {
        for (child in el.children()) {
            if (child.className() == "title--3") {
                var title = child.ownText().trim()
                if (title.isEmpty())
                    title = child.text().trim()
                if (!title.isEmpty())
                    return title
            }

            val title = findTitle(child);
            if (title != null)
                return title
        }

        return null
    }

    private fun findIntermezzoList(el: Element) : String? {
        val descr = mutableListOf<String>()
        for (ul in el.children()) {
            if (ul.className() == "list-intermezzo") {
                val line = mutableListOf<String>()
                for (li in ul.children()) {
                    line.add(li.text())
                }
                if (line.isNotEmpty()) {
                    descr.add(line.joinToString(" | "))
                }
            }
        }

        if (descr.isNotEmpty()) {
            return descr.joinToString("\r\n")
        }

        for (child in el.children()) {
            val intermezzo = findIntermezzoList(child)
            if (intermezzo != null)
                return intermezzo
        }

        return null
    }

    private fun timeString(date: LocalDate, time: LocalTime) : String =
        date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + time.format(DateTimeFormatter.ofPattern("HHmmss")) + " +0300"


    private fun getSchedule() : String? {
        val lang = "ru"
        val serializer = Xml.newSerializer()
        val writer = StringWriter()

        var prevTime: LocalTime? = null
        var prevTitle: String? = null
        var prevDesc: String? = null

        val url = "https://www.mezzo.tv/en/tv-schedule"

        try {
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", false)
            serializer.docdecl(" tv SYSTEM \"xmltv.dtd\"")
            serializer.startTag("", "tv")
            serializer.attribute("generator-info-name", "Mezzo parser")

            serializer.startTag("", "channel")
            serializer.attribute("id", "mezzo")
            serializer.startTag("", "display-name")
            serializer.attribute("lang", lang)
            serializer.text("Mezzo")
            serializer.endTag("", "display-name")
            serializer.endTag("", "channel")

            serializer.startTag("", "channel")
            serializer.attribute("id", "mezzo_hd")
            serializer.startTag("", "display-name")
            serializer.attribute("lang", lang)
            serializer.text("Mezzo Live HD")
            serializer.endTag("", "display-name")
            serializer.endTag("", "channel")

            notify(NotificationLevel.PROGRESS,"Processing...", )

            val now = LocalDate.now()
            for (days in 0..3) {
                val currentDate = now.plusDays(days.toLong())
                val doc =
                    Jsoup.connect(url)
                        .timeout(12000)
                        .cookie("regional", "ru%7CEurope%2FMoscow")
                        .data("date", currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        .get()
                Log.i(LOG_TAG, "Parse shedule for ${currentDate}")
                val programmes = doc.getElementsByClass("list-programme")
//                Log.d(LOG_TAG, "found ${programmes.size} program section(s)")
                val channelIds = mutableListOf("mezzo_hd", "mezzo")
                for (program in programmes) {
                    val channelId = if (channelIds.isEmpty()) "0" else channelIds.removeAt(0)
//                    Log.d(LOG_TAG, "")
//                    Log.d(LOG_TAG, channelId)
                    for (li in program.children()) {
                        if (li.tagName() == "li") {
                            val time = findTime(li)
                            val title = findTitle(li)
                            val desc = findIntermezzoList(li)
                            if (time != null && title != null) {
//                                Log.d(LOG_TAG, "$time $title")

                                if (prevTime != null && prevTitle != null) {
                                    serializer.startTag("", "programme")
                                    serializer.attribute("start", timeString(currentDate, prevTime))
                                    serializer.attribute("stop", timeString(currentDate, time))
                                    serializer.attribute("channel", channelId)

                                    serializer.startTag("", "title")
                                    serializer.attribute("lang", lang)
                                    serializer.text(prevTitle)
                                    serializer.endTag("", "title")

                                    if (prevDesc != null) {
                                        serializer.startTag("", "desc")
                                        serializer.attribute("lang", lang)
                                        serializer.text(prevDesc)
                                        serializer.endTag("", "desc")
                                    }

                                    serializer.endTag("", "programme")
                                }

                                prevTime = time
                                prevTitle = title
                                prevDesc = desc
                            }
                        }
                    }

                    if (prevTime != null && prevTitle != null) {
                        serializer.startTag("", "programme")
                        serializer.attribute("start", timeString(currentDate, prevTime))
                        serializer.attribute("stop", timeString(currentDate, LocalTime.of(23, 59)))
                        serializer.attribute("channel", channelId)

                        serializer.startTag("", "title")
                        serializer.attribute("lang", lang)
                        serializer.text(prevTitle)
                        serializer.endTag("", "title")

                        if (prevDesc != null) {
                            serializer.startTag("", "desc")
                            serializer.attribute("lang", lang)
                            serializer.text(prevDesc)
                            serializer.endTag("", "desc")
                        }

                        serializer.endTag("", "programme")
                        prevTime = null
                        prevTitle = null
                    }
                }
            }

            serializer.endTag("", "tv")
            serializer.endDocument()
            notify(NotificationLevel.HIDE)
            return writer.toString()

        } catch (e: HttpStatusException) {
            notify(NotificationLevel.WARNINIG,"${e.url} returns ${e.statusCode} : ${e.message}")
            return null
        } catch (e: SocketTimeoutException) {
            notify(NotificationLevel.WARNINIG, "$url is not responding: ${e.message}")
            return null
        } catch (e: Exception) {
            notify(NotificationLevel.WARNINIG, "Parsing failed: ${e.message}")
            Log.e(LOG_TAG, Log.getStackTraceString(e))
            return null
        }
    }

    enum class NotificationLevel {
        HIDE, INFO, WARNINIG, PROGRESS
    }
    private fun notify(level: NotificationLevel, text: String = "") {
        if (level == NotificationLevel.INFO)
            Log.i(LOG_TAG, text)
        if (level == NotificationLevel.WARNINIG)
            Log.w(LOG_TAG, text)

        val CHANNEL_ID = "io.github.sonnayasomnambula.mezzoparser.parser"
        val NOTIFICATION_ID = 1

        if (level == NotificationLevel.HIDE) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Mezzo parser")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (level == NotificationLevel.PROGRESS)
            builder.setProgress(100, 0, true)

        with(NotificationManagerCompat.from(context)) {
            val channel = NotificationChannel(CHANNEL_ID, "Parser notifications", NotificationManager.IMPORTANCE_DEFAULT)
            createNotificationChannel(channel)
            notify(NOTIFICATION_ID, builder.build())
        }
    }


    private fun toXmlTv() : String {
        val lang = "ru"
        val serializer = Xml.newSerializer()
        try {
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            return serializer.document {
                element("tv") {
                    attribute("generator-info-name", "Mezzo parser 2")
                    element("channel") {
                        attribute("id", "mezzo")
                        element("display-name", "Mezzo") {
                            attribute("lang", lang)
                        }
                    }
                    element("channel") {
                        attribute("id", "mezzo_hd")
                        element("display-name", "Mezzo Live HD") {
                            attribute("lang", lang)
                        }
                    }
                    element("programme") {
                        attribute("start", "20230202100000 +0300")
                        attribute("stop", "20230202110000 +0300")
                        attribute("channel", "mezzo")
                        element("title", "A program at 10") {
                            attribute("lang", lang)
                        }
                        element("desc", "A very interesting program!") {
                            attribute("lang", lang)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, Log.getStackTraceString(e))
            return "error"
        }
    }

    override fun run() {
        super.run()

        val text = getSchedule()

        val fileUri = settings.getString(Settings.Tags.URI, null)
        if (text != null && fileUri != null)
            writeFile(Uri.parse(fileUri), text)
    }
}

class ParserService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "Service: onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "Service: onStartCommand")

        val thread = ParserThread(this, contentResolver, getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE))
        thread.start()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "Service: onDestroy")
        super.onDestroy()
    }
}