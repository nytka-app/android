package io.github.nytka_app.transcribe

import io.github.nytka_app.core.transcribe.ModelFiles
import io.github.nytka_app.core.transcribe.ModelLayout
import io.github.nytka_app.core.transcribe.OnDeviceTranscriber
import io.github.nytka_app.core.transcribe.TranscriberEngine

/**
 * The oss build ships no recogniser: sherpa-onnx comes as a prebuilt native library, which F-Droid does not accept
 * unbuilt. The setting explains this and stays off.
 */
class SherpaEngine : TranscriberEngine {
    override val available = false

    override val layouts = emptySet<ModelLayout>()

    override fun open(model: ModelFiles): OnDeviceTranscriber = error("This build has no on-device recogniser.")
}
