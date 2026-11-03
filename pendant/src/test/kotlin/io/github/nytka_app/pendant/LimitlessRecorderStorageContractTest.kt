package io.github.nytka_app.pendant

import kotlinx.coroutines.test.TestScope

/** The Limitless driver over [FakeLimitless]: stamps are milliseconds, a page is a block. Untested on hardware. */
class LimitlessRecorderStorageContractTest : RecorderStorageContractTest() {
    override fun TestScope.rig(): Rig {
        val pendant = FakeLimitless(backgroundScope)
        val limitless = LimitlessStorage(pendant, LimitlessCommands(), { LimitlessMode.Live })
        pendant.listener = { limitless.onNotification(it) }
        limitless.evaluate()
        return object : Rig {
            override val storage: RecorderStorage = limitless

            override fun fill(blocks: Int) {
                repeat(blocks) { pendant.store(LimitlessFixtures.pageStamp(it), listOf(LimitlessFixtures.opus())) }
            }

            override fun stallAfter(blocks: Int) {
                pendant.stallAfterPages = blocks
            }

            override fun cutLink() {
                pendant.disconnect()
                limitless.linkLost()
            }

            override fun first() = pendant.first
        }
    }
}
