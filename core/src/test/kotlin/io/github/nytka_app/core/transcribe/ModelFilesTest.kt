package io.github.nytka_app.core.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the sherpa-onnx Parakeet folder is a transducer and its int8 files win`() {
        val picked =
            ModelFiles.pick(
                listOf(
                    "encoder.int8.onnx",
                    "encoder.onnx",
                    "decoder.int8.onnx",
                    "joiner.int8.onnx",
                    "tokens.txt",
                    "README.md",
                    "test_wavs",
                ),
            )!!

        assertEquals(ModelLayout.Transducer, picked.layout)
        assertEquals("encoder.int8.onnx", picked.encoder)
        assertEquals(listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"), picked.names)
    }

    @Test
    fun `a single onnx with tokens is a CTC model`() {
        val picked = ModelFiles.pick(listOf("model.int8.onnx", "tokens.txt"))!!

        assertEquals(ModelLayout.Ctc, picked.layout)
        assertEquals("model.int8.onnx", picked.model)
    }

    @Test
    fun `encoder and decoder without a joiner is Whisper`() {
        val picked = ModelFiles.pick(listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt"))!!

        assertEquals(ModelLayout.Whisper, picked.layout)
    }

    @Test
    fun `no tokens or no onnx is no model`() {
        assertNull(ModelFiles.pick(listOf("encoder.onnx", "decoder.onnx", "joiner.onnx")))
        assertNull(ModelFiles.pick(listOf("tokens.txt", "README.md")))
    }

    @Test
    fun `a directory on disk resolves to its files and their size`() {
        val dir = temp.newFolder("parakeet")
        listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt").forEach {
            dir.resolve(it).writeBytes(ByteArray(10))
        }

        val model = ModelFiles.inDirectory(dir)!!

        assertEquals("parakeet", model.name)
        assertEquals(40L, model.bytes)
        assertEquals(dir.resolve("joiner.int8.onnx"), model.joiner)
    }
}
