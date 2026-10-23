package io.github.nytka_app.core.context

/**
 * What kind of app plays a media session, worked out on the phone from the app's package so that no app name, title
 * or artist leaves it: the server gets only this word.
 */
enum class MediaCategory(
    val wire: String,
) {
    VIDEO("video"),
    MUSIC("music"),
    PODCAST("podcast"),
    MESSENGER("messenger"),
    BROWSER("browser"),
    OTHER("other"),
    ;

    companion object {
        private val packages =
            mapOf(
                VIDEO to
                    listOf(
                        "com.google.android.youtube",
                        "app.revanced.android.youtube",
                        "com.netflix.mediaclient",
                        "org.videolan.vlc",
                        "tv.twitch.android.app",
                        "com.zhiliaoapp.musically",
                        "com.ss.android.ugc.trill",
                        "com.instagram.android",
                        "com.amazon.avod.thirdpartyclient",
                        "com.disney.disneyplus",
                        "org.schabi.newpipe",
                        "com.google.android.apps.youtube.kids",
                    ),
                MUSIC to
                    listOf(
                        "com.google.android.apps.youtube.music",
                        "com.spotify.music",
                        "deezer.android.app",
                        "com.apple.android.music",
                        "com.soundcloud.android",
                        "ru.yandex.music",
                        "com.aspiro.tidal",
                        "com.amazon.mp3",
                    ),
                PODCAST to
                    listOf(
                        "com.google.android.apps.podcasts",
                        "au.com.shiftyjelly.pocketcasts",
                        "fm.castbox.audiobook.radio.podcast",
                        "com.bambuna.podcastaddict",
                        "de.danoeh.antennapod",
                        "com.audible.application",
                    ),
                MESSENGER to
                    listOf(
                        "org.telegram.messenger",
                        "org.telegram.messenger.web",
                        "org.telegram.plus",
                        "org.thunderdog.challegram",
                        "com.whatsapp",
                        "org.thoughtcrime.securesms",
                        "com.viber.voip",
                        "com.discord",
                    ),
                BROWSER to
                    listOf(
                        "com.android.chrome",
                        "org.mozilla.firefox",
                        "org.mozilla.fenix",
                        "com.brave.browser",
                        "com.opera.browser",
                        "com.sec.android.app.sbrowser",
                        "com.microsoft.emmx",
                        "com.vivaldi.browser",
                    ),
            ).flatMap { (category, names) -> names.map { it to category } }
                .toMap()

        /** The category of the app with this package; [OTHER] for one not on the list. */
        fun of(packageName: String): MediaCategory = packages[packageName] ?: OTHER
    }
}
