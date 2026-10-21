package io.github.nytka_app.transcribe

import android.os.Build
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import io.github.nytka_app.core.transcribe.ModelFiles
import io.github.nytka_app.core.transcribe.ModelLayout
import io.github.nytka_app.core.transcribe.OnDeviceTranscriber
import io.github.nytka_app.core.transcribe.TranscriberEngine

/**
 * sherpa-onnx (k2-fsa, Apache-2.0) over ONNX Runtime (MIT), offline recognition from files on the phone. Runs NeMo
 * transducers such as Parakeet TDT 0.6B v3, the model the server's own recogniser runs, and NeMo CTC models.
 */
class SherpaEngine : TranscriberEngine {
    // The play build packs arm64-v8a only.
    override val available: Boolean get() = "arm64-v8a" in Build.SUPPORTED_ABIS

    override val layouts = setOf(ModelLayout.Transducer, ModelLayout.Ctc)

    override fun open(model: ModelFiles): OnDeviceTranscriber {
        require(model.layout in layouts) { "This app cannot run a ${model.layout} model." }
        val nemo = model.layout == ModelLayout.Ctc
        val config =
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig =
                    OfflineModelConfig(
                        transducer =
                            if (nemo) {
                                OfflineTransducerModelConfig()
                            } else {
                                OfflineTransducerModelConfig(
                                    encoder = model.encoder!!.path,
                                    decoder = model.decoder!!.path,
                                    joiner = model.joiner!!.path,
                                )
                            },
                        nemo =
                            if (nemo) {
                                OfflineNemoEncDecCtcModelConfig(
                                    model = model.model!!.path,
                                )
                            } else {
                                OfflineNemoEncDecCtcModelConfig()
                            },
                        tokens = model.tokens.path,
                        numThreads = threads(),
                        modelType = if (nemo) "" else "nemo_transducer",
                        debug = false,
                    ),
            )
        return SherpaTranscriber(OfflineRecognizer(null, config))
    }

    private class SherpaTranscriber(
        private val recognizer: OfflineRecognizer,
    ) : OnDeviceTranscriber {
        override fun transcribe(samples: FloatArray): String {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer.decode(stream)
                return recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
        }

        override fun close() = recognizer.release()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val MAX_THREADS = 4

        /** A mid-range phone has four fast cores at most; more threads land on the slow ones and cost battery. */
        fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
    }
}
