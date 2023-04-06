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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Whitelist
import java.io.StringWriter
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime
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
            notify(NotificationLevel.WARNINIG, "${e.localizedMessage}")
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
                findTime(child)?.let { return it }
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

            findTitle(child)?.let { return it }
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

        if (descr.isNotEmpty())
            return descr.joinToString("\r\n")

        for (child in el.children())
            findIntermezzoList(child)?.let { return it }

        return null
    }

    private fun findUrl(el: Element) : String? {
        el.select("a[href]").firstOrNull()?.let {
            val href = it.absUrl("href")
            if (href.isNotEmpty())
                return href
        }

        return null
    }

    private fun downloadDescription(url: String?) : String? {
        if (url == null)
            return null

        try {
            val doc =
                Jsoup.connect(url)
                    .timeout(12000)
                    .get()

            doc.getElementsByClass("programme-mosaic__content editorial").firstOrNull()?.let {
                val desc = mutableListOf<String>()
                it.getElementsByClass("list-authors").firstOrNull()?.let {
                    val authors = mutableListOf<String>()
                    for (li in it.children()) {
                        authors.add(li.text())
                    }
                    desc.add(authors.joinToString(" | "))
                }

                if (desc.isNotEmpty())
                    desc.add("")

                for (p in it.getElementsByTag("p")) {
                    for (line in p.html().split("<br>")) {
                        val clean = Jsoup.clean(line.replace("&nbsp;", " "), Whitelist.none()).trim()
                        if (clean.isNotEmpty())
                            desc.add(clean)
                    }
                }

                if (desc.isNotEmpty())
                    return desc.joinToString("\r\n")
            }

            return null

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

    class Serializer {
        private val lang = "ru"
        private val ser = Xml.newSerializer()
        private val writer = StringWriter()

        fun start() {
            ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            ser.setOutput(writer)
            ser.startDocument("UTF-8", false)
            ser.docdecl(" tv SYSTEM \"xmltv.dtd\"")
            ser.startTag("", "tv")
            ser.attribute("generator-info-name", "Mezzo parser")

            ser.startTag("", "channel")
            ser.attribute("id", "mezzo")
            ser.startTag("", "display-name")
            ser.attribute("lang", lang)
            ser.text("Mezzo")
            ser.endTag("", "display-name")
            ser.endTag("", "channel")

            ser.startTag("", "channel")
            ser.attribute("id", "mezzo_hd")
            ser.startTag("", "display-name")
            ser.attribute("lang", lang)
            ser.text("Mezzo Live HD")
            ser.endTag("", "display-name")
            ser.endTag("", "channel")
        }

        fun write(start: LocalDateTime, stop: LocalDateTime, channel: String, title: String, desc: String? ) {
            ser.startTag("", "programme")
            ser.attribute("start", timeString(start))
            ser.attribute("stop", timeString(stop))
            ser.attribute("channel", channel)

            ser.startTag("", "title")
            ser.attribute("lang", lang)
            ser.text(title)
            ser.endTag("", "title")

            if (desc != null) {
                ser.startTag("", "desc")
                ser.attribute("lang", lang)
                ser.text(desc)
                ser.endTag("", "desc")
            }

            ser.endTag("", "programme")
        }

        fun finish() {
            ser.endTag("", "tv")
            ser.endDocument()
        }

        override fun toString(): String =
            writer.toString()

        private fun timeString(time: LocalDateTime) : String =
            time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + " +0300"
    }


    private fun getSchedule() : String? {
        val serializer = Serializer()

        var prevTime: LocalTime? = null
        var prevTitle: String? = null
        var prevDesc: String? = null

        val url = "https://www.mezzo.tv/en/tv-schedule"

        val doDownloadDescription = settings.getBoolean(Settings.Tags.DOWNLOAD_DESCRIPTION, false)

        try {
            serializer.start()

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
                            val desc = findIntermezzoList(li) ?: if (doDownloadDescription) downloadDescription(findUrl(li)) else null
//                            val desc = findIntermezzoList(li)
                            if (time != null && title != null) {
//                                Log.d(LOG_TAG, "$time $title")

                                val intent = Intent(BroadcastMessage.ANOTHER_TIME)
                                intent.putExtra(BroadcastMessage.DATA_DATE, currentDate.toString())
                                intent.putExtra(BroadcastMessage.DATA_TIME, time.toString())
                                intent.putExtra(BroadcastMessage.DATA_PROGRESS, 1.0 * program.children().indexOf(li) / program.children().size)
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

                                if (prevTime != null && prevTitle != null) {
                                    serializer.write(LocalDateTime.of(currentDate, prevTime),
                                              LocalDateTime.of(currentDate, time),
                                              channelId,
                                              prevTitle,
                                              prevDesc)
                                }

                                prevTime = time
                                prevTitle = title
                                prevDesc = desc
                            }
                        }
                    }

                    if (prevTime != null && prevTitle != null) {
                        serializer.write(LocalDateTime.of(currentDate, prevTime),
                                  LocalDateTime.of(currentDate, LocalTime.of(23, 59)),
                                  channelId,
                                  prevTitle,
                                  prevDesc)

                        prevTime = null
                        prevTitle = null
                    }
                }
            }

            serializer.finish()

            val intent = Intent(BroadcastMessage.DONE)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

            notify(NotificationLevel.HIDE)
            return serializer.toString()

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