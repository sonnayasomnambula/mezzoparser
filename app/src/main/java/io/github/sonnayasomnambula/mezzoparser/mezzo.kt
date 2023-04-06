package io.github.sonnayasomnambula.mezzoparser

val LOG_TAG = "_mezzo_"

class Settings {
    companion object {
        val FILE = "settings"
    }

    class Tags {
        companion object {
            val URI = "uri"
            val DOWNLOAD_DESCRIPTION = "download_description"
        }
    }
}

class BroadcastMessage {
    companion object {
        val ANOTHER_TIME = "io.github.sonnayasomnambula.mezzoparser.action.ANOTHER_TIME"
        val DATA_DATE = "date"
        val DATA_TIME = "time"
        val DATA_PROGRESS = "progress"

        val DONE = "io.github.sonnayasomnambula.mezzoparser.action.DONE"
    }
}