package io.github.nytka_app.core.transcribe

import java.io.File

/** How a model's files are laid out, as sherpa-onnx packages them. */
enum class ModelLayout {
    /** NeMo or icefall transducer, such as Parakeet TDT: encoder, decoder, joiner, tokens. */
    Transducer,

    /** Whisper: encoder, decoder, tokens. */
    Whisper,

    /** One CTC model, such as a NeMo FastConformer CTC: model, tokens. */
    Ctc,
}

/** One file of a model folder as the picker lists it, before anything is copied. */
data class ModelFileEntry(
    val name: String,
    val bytes: Long,
)

/**
 * The files of one model, recognised by name: `*encoder*.onnx`, `*decoder*.onnx`, `*joiner*.onnx`, `tokens.txt`
 * (any prefix), and for CTC a single other `.onnx`. Where a folder holds both an int8 file and a full-precision one
 * for the same part, the int8 one wins: it is a quarter of the size and what a phone can run.
 */
data class ModelFiles(
    val name: String,
    val layout: ModelLayout,
    val encoder: File? = null,
    val decoder: File? = null,
    val joiner: File? = null,
    val model: File? = null,
    val tokens: File,
) {
    val files: List<File> get() = listOfNotNull(encoder, decoder, joiner, model, tokens)

    val bytes: Long get() = files.sumOf { it.length() }

    companion object {
        /** The model in [dir], or null when its files are not one. */
        fun inDirectory(dir: File): ModelFiles? {
            val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: return null
            val pick = pick(names) ?: return null
            return ModelFiles(
                name = dir.name,
                layout = pick.layout,
                encoder = pick.encoder?.let { File(dir, it) },
                decoder = pick.decoder?.let { File(dir, it) },
                joiner = pick.joiner?.let { File(dir, it) },
                model = pick.model?.let { File(dir, it) },
                tokens = File(dir, pick.tokens),
            )
        }

        /** Which of [names] make up a model, and its layout; null when they make none. */
        fun pick(names: Collection<String>): Picked? {
            val tokens = names.firstOrNull { it.endsWith("tokens.txt") } ?: return null
            val onnx = names.filter { it.endsWith(".onnx") }
            val encoder = best(onnx.filter { "encoder" in it })
            val decoder = best(onnx.filter { "decoder" in it })
            val joiner = best(onnx.filter { "joiner" in it })
            return when {
                encoder != null && decoder != null && joiner != null ->
                    Picked(ModelLayout.Transducer, tokens, encoder = encoder, decoder = decoder, joiner = joiner)

                encoder != null && decoder != null ->
                    Picked(ModelLayout.Whisper, tokens, encoder = encoder, decoder = decoder)

                else -> {
                    val model = best(onnx.filter { "encoder" !in it && "decoder" !in it && "joiner" !in it })
                    model?.let { Picked(ModelLayout.Ctc, tokens, model = it) }
                }
            }
        }

        private fun best(candidates: List<String>): String? =
            candidates.firstOrNull { "int8" in it } ?: candidates.minByOrNull { it.length }
    }

    data class Picked(
        val layout: ModelLayout,
        val tokens: String,
        val encoder: String? = null,
        val decoder: String? = null,
        val joiner: String? = null,
        val model: String? = null,
    ) {
        val names: List<String> get() = listOfNotNull(encoder, decoder, joiner, model, tokens)
    }
}
