package io.github.nytka_app.ui.voice

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.EnrollmentCapture
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.EnrollMode
import io.github.nytka_app.core.api.EnrollRefusal
import io.github.nytka_app.core.api.EnrollResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.VoiceClient
import io.github.nytka_app.core.api.VoiceStatus
import io.github.nytka_app.core.chunks.ChunkReader
import io.github.nytka_app.core.chunks.EnrollmentRecording
import io.github.nytka_app.pendant.AudioFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeVoice : VoiceClient {
        var status: ApiResult<VoiceStatus> = ApiResult.Ok(VoiceStatus(modelAvailable = true))
        var enroll: EnrollResult = EnrollResult.Enrolled(31.0, 7, 0.6)
        var gate: CompletableDeferred<Unit>? = null
        var forget: ApiResult<Unit> = ApiResult.Ok(Unit)
        val sent = mutableListOf<Pair<ByteArray, EnrollMode>>()
        var forgets = 0

        override suspend fun voice() = status

        override suspend fun enroll(
            body: ByteArray,
            mode: EnrollMode,
        ): EnrollResult {
            sent += body to mode
            gate?.await()
            return enroll
        }

        override suspend fun reset() = status

        override suspend fun forget() = forget.also { forgets++ }
    }

    /** Live capture as the hub sees it: [queued] is what would reach the upload queue. */
    private class FakeCapture : EnrollmentCapture {
        override val recording = MutableStateFlow(true)
        var sink: ((AudioFrame) -> Unit)? = null
        var running = true
        val queued = mutableListOf<AudioFrame>()

        val diverting: Boolean get() = sink != null

        override fun divert(sink: (AudioFrame) -> Unit): Boolean {
            if (!running) return false
            this.sink = sink
            return true
        }

        override fun resume() {
            sink = null
        }

        fun frames(
            count: Int,
            size: Int = 80,
        ) = repeat(count) {
            val frame = AudioFrame(ByteArray(size) { 1 }, it * 20L)
            sink?.invoke(frame) ?: queued.add(frame)
        }
    }

    private val api = FakeVoice()
    private val capture = FakeCapture()

    private fun newViewModel() = VoiceViewModel(api, capture).apply { toggleLanguage(PromptLanguage.Ukrainian) }

    @Test
    fun `starting diverts live frames, and sending resumes capture before the server answers`() {
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        assertTrue(capture.diverting)
        assertEquals(VoicePhase.Recording, vm.state.value.phase)
        capture.frames(1_000)
        vm.send()

        assertFalse(capture.diverting)
        assertEquals(VoicePhase.Sending, vm.state.value.phase)
        capture.frames(3)
        gate.complete(Unit)

        assertTrue(capture.queued.size == 3)
        assertEquals(VoicePhase.Idle, vm.state.value.phase)
        assertEquals(VoiceNotice.Enrolled(31.0, 7), vm.state.value.notice)
    }

    @Test
    fun `the frames read during enrollment are the ones sent, and none were queued`() {
        val vm = newViewModel()

        vm.start(EnrollMode.Add)
        capture.frames(1_600)
        vm.send()

        val (body, mode) = api.sent.single()
        assertEquals(EnrollMode.Add, mode)
        assertEquals(1_600, ChunkReader.readAll(body).sumOf { it.frames.size })
        assertTrue(capture.queued.isEmpty())
    }

    @Test
    fun `capture resumes when the server refuses the reading`() {
        api.enroll = EnrollResult.Refused(EnrollRefusal.TooLittleSpeech, 12.0, 2)
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        capture.frames(600)
        vm.send()

        assertFalse(capture.diverting)
        assertEquals(VoiceNotice.Refused(EnrollRefusal.TooLittleSpeech, 12.0), vm.state.value.notice)
        assertEquals(VoicePhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `capture resumes when the call fails`() {
        api.enroll = EnrollResult.Failed(ApiResult.Failure(FailureKind.Forbidden, "No."))
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        capture.frames(600)
        vm.send()

        assertFalse(capture.diverting)
        assertEquals(VoiceNotice.Failed("The app needs an admin token."), vm.state.value.notice)
    }

    @Test
    fun `each other server answer has its notice`() {
        val answers =
            mapOf(
                EnrollResult.TooLong to VoiceNotice.TooLong,
                EnrollResult.NoModel to VoiceNotice.NoModel,
                EnrollResult.Unreadable to VoiceNotice.Unreadable,
                EnrollResult.OtherModel to VoiceNotice.OtherModel,
            )
        val vm = newViewModel()
        answers.forEach { (result, notice) ->
            api.enroll = result
            vm.start(EnrollMode.Replace)
            capture.frames(10)
            vm.send()

            assertEquals(notice, vm.state.value.notice)
            assertFalse(capture.diverting)
        }
    }

    @Test
    fun `cancel resumes capture and sends nothing`() {
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        capture.frames(100)
        vm.cancel()
        capture.frames(2)

        assertFalse(capture.diverting)
        assertTrue(api.sent.isEmpty())
        assertEquals(2, capture.queued.size)
        assertEquals(VoicePhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `the 120 second cap ends the diversion by itself`() {
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        capture.frames(EnrollmentRecording.MAX_FRAMES + 5)

        assertFalse(capture.diverting)
        assertEquals(5, capture.queued.size)
        assertTrue(vm.state.value.full)
        assertEquals(120.0, vm.state.value.seconds, 0.0)
        vm.send()
        assertEquals(
            EnrollmentRecording.MAX_FRAMES,
            ChunkReader.readAll(api.sent.single().first).sumOf { it.frames.size },
        )
    }

    @Test
    fun `elapsed seconds and the level follow the frames`() {
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        capture.frames(250, size = 100)

        assertEquals(5.0, vm.state.value.seconds, 0.0)
        assertEquals(1f, vm.state.value.level, 0f)
    }

    @Test
    fun `a pendant that sends no audio cannot start`() {
        capture.recording.value = false
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)

        assertFalse(capture.diverting)
        assertEquals(VoicePhase.Idle, vm.state.value.phase)
        assertEquals(VoiceNotice.NotRecording, vm.state.value.notice)
    }

    @Test
    fun `capture that stopped meanwhile cannot start either`() {
        capture.running = false
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)

        assertEquals(VoicePhase.Idle, vm.state.value.phase)
        assertEquals(VoiceNotice.NotRecording, vm.state.value.notice)
    }

    @Test
    fun `no language chosen, no start`() {
        val vm = VoiceViewModel(api, capture)

        vm.start(EnrollMode.Replace)

        assertFalse(capture.diverting)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `sending nothing says so`() {
        val vm = newViewModel()

        vm.start(EnrollMode.Replace)
        vm.send()

        assertFalse(capture.diverting)
        assertTrue(api.sent.isEmpty())
        assertEquals(VoiceNotice.NothingRecorded, vm.state.value.notice)
    }

    @Test
    fun `frames from a cancelled reading are not kept`() {
        val vm = newViewModel()
        vm.start(EnrollMode.Replace)
        val stale = capture.sink!!
        vm.cancel()
        vm.start(EnrollMode.Replace)

        repeat(10) { stale(AudioFrame(byteArrayOf(1), 0)) }
        capture.frames(5)
        vm.send()

        assertEquals(5, ChunkReader.readAll(api.sent.single().first).sumOf { it.frames.size })
    }

    @Test
    fun `forget deletes and reads the status again`() {
        val vm = newViewModel()

        vm.askForget()
        assertTrue(vm.state.value.confirmForget)
        vm.forget()

        assertEquals(1, api.forgets)
        assertFalse(vm.state.value.confirmForget)
        assertEquals(VoiceNotice.Forgotten, vm.state.value.notice)
    }

    @Test
    fun `a status the server refuses shows why`() {
        api.status = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        assertEquals("This server needs an update", newViewModel().state.value.error)
    }
}
