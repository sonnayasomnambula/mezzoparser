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

    private fun findTimeAndTitle(el: Element) : Pair<LocalTime, String>? {
        for (child in el.children()) {
            if (child.tagName() == "span") {
                try {
                    val time = LocalTime.parse(child.text().trim(), DateTimeFormatter.ofPattern("H:mm"))
                    val title = el.ownText().trim()
                    return Pair(time, title)
                } catch (e: java.time.format.DateTimeParseException) {
                    Log.w(LOG_TAG, "Parse error: '${child.text()}' is not a valid time string")
                }
            } else {
                val pair = findTimeAndTitle(child)
                if (pair != null)
                    return pair
            }
        }

        return null
    }

    private fun getUl(el: Element, className: String) : Element? {
        for (child in el.children()) {
            if (child.tagName() == "ul" && child.className() == className) {
                return child
            }
        }
        return null
    }

    private fun parseIntermezzoList(el: Element) : String? {
        val descr = mutableListOf<String>()
        for (li in el.children()) {
            for (ul in li.children()) {
                val line = mutableListOf<String>()
                for (li2 in ul.children()) {
                    line.add(li2.text())
                }
                descr.add(line.joinToString(" | "))
            }
        }
        return if (descr.isEmpty()) null else descr.joinToString("\r\n")
    }

    private fun findIntermezzoList(el: Element) : String? {
        val ul = getUl(el, "list-intermezzo")
        if (ul != null) {
            return parseIntermezzoList(ul)
        }

        for (child in el.children()) {
            val descr = findIntermezzoList(child)
            if (descr != null)
                return descr
        }

        return null
    }

    private fun timeString(date: LocalDate, time: LocalTime) : String =
        date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + time.format(DateTimeFormatter.ofPattern("HHmmss")) + " +0300"


    private fun getShedule() : String {
        val lang = "ru"
        val serializer = Xml.newSerializer()
        val writer = StringWriter()

        var prevTime: LocalTime? = null
        var prevTitle: String? = null
        var prevDesc: String? = null

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

            val url = "https://www.mezzo.tv/en/tv-schedule"
            val now = LocalDate.now()
            for (days in 0..7) {
                val currentDate = now.plusDays(days.toLong())
                val doc =
                    Jsoup.connect(url)
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
                            val desc = findIntermezzoList(li)
                            val timeAndTitle = findTimeAndTitle(li)
                            if (timeAndTitle != null) {
                                val time = timeAndTitle.first
                                val title = timeAndTitle.second
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

        } catch (e: HttpStatusException ) {
            notify(NotificationLevel.WARNINIG,"${e.url} returns ${e.statusCode} : ${e.message}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, Log.getStackTraceString(e))
        } finally {

        }

        return "error\r\n"
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

        val text = getShedule()

        val fileUri = settings.getString(Settings.Tags.URI, null)
        if (fileUri != null)
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