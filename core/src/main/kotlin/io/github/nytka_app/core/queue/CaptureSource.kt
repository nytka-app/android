package io.github.nytka_app.core.queue

/**
 * Which microphone a frame came from. A capture session holds one source, and the server keeps them apart
 * (`X-Nytka-Source` on the chunk upload), so phone audio never joins a pendant conversation.
 */
object CaptureSource {
    const val PENDANT = "pendant"
    const val PHONE_MIC = "phone-mic"
}
