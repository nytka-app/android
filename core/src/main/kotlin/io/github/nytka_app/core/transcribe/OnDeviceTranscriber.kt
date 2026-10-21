package io.github.nytka_app.core.transcribe

/**
 * Speech recognition that runs on the phone. The seam between Nytka and an engine: the app's `play` build binds
 * sherpa-onnx, the `oss` build binds nothing ([TranscriberEngine.available] is false). Not thread-safe; one run at
 * a time.
 */
interface OnDeviceTranscriber : AutoCloseable {
    /** Text for [samples]: 16 kHz mono, -1 to 1. May be empty. Blocks for as long as recognition takes. */
    fun transcribe(samples: FloatArray): String
}

/** Opens a [OnDeviceTranscriber] over an imported model. */
interface TranscriberEngine {
    /** False in a build that ships no engine; the setting then explains that and stays off. */
    val available: Boolean

    /** The model layouts this engine can open. */
    val layouts: Set<ModelLayout>

    /** Loads [model]; throws if its files are not a model this engine can run. Loading takes seconds. */
    fun open(model: ModelFiles): OnDeviceTranscriber
}

/** Turns one 20 ms Opus frame from the pendant into 320 samples at 16 kHz. Not thread-safe. */
interface OpusDecoder : AutoCloseable {
    fun decode(frame: ByteArray): FloatArray

    /** Forgets the previous frames: call before a frame that does not follow the last one. */
    fun reset()
}
