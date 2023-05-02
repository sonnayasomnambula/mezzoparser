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
        val PROGRESS = "io.github.sonnayasomnambula.mezzoparser.action.PROGRESS"
        val DATA_MESSAGE = "message"
        val DATA_PROGRESS1 = "progress1"
        val DATA_PROGRESS2 = "progress2"
    }
}