package io.github.nytka_app.capture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.chunks.ChunkReader
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.pendant.FakePendant
import io.github.nytka_app.pendant.OmiPendant
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantLogger
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class PendantFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val log: EventLog,
    ) {
        fun create(
            scope: CoroutineScope,
            fake: Boolean,
        ): Pendant =
            if (fake) {
                FakePendant(
                    fakePayloads(),
                    scope,
                )
            } else {
                OmiPendant(context, scope, logger = PendantLogger(log::log))
            }

        /** The bundled synthetic recording (Task 8), frame payloads in order. */
        private fun fakePayloads(): List<ByteArray> =
            ChunkReader
                .readAll(
                    context.assets.open(FAKE_ASSET).use { it.readBytes() },
                ).flatMap { it.frames }
                .map { it.payload }

        companion object {
            const val FAKE_ASSET = "fake_pendant.nytk"
            const val FAKE_ADDRESS = "fake"
        }
    }
